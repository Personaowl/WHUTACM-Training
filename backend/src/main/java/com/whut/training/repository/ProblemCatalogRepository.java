package com.whut.training.repository;

import com.whut.training.domain.entity.CfProblem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** Shared local CF catalog, random selection and revision-aware tag cache. */
@Repository
public class ProblemCatalogRepository {
    private final JdbcTemplate jdbcTemplate;
    private final Object practiceTagsLock = new Object();
    private final AtomicLong practiceTagsRevision = new AtomicLong();
    private volatile PracticeTagsCache practiceTagsCache;

    private final RowMapper<CfProblem> cfProblemRowMapper = (rs, rowNum) -> new CfProblem(
            rs.getString("problem_key"),
            rs.getInt("contest_id"),
            rs.getString("problem_index"),
            rs.getString("name"),
            (Integer) rs.getObject("rating"),
            rs.getString("tags"),
            rs.getInt("is_interactive") != 0,
            (Integer) rs.getObject("source_contest_id"),
            (Integer) rs.getObject("solved_count"),
            rs.getString("source_url")
    );

    public ProblemCatalogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 批量新增或更新题库。
     *
     * @param problems 题库列表。
     * @return 实际处理条数。
     */
    public int upsertProblems(List<CfProblem> problems) {
        if (problems.isEmpty()) {
            return 0;
        }
        String nowIso = OffsetDateTime.now().toString();
        try {
            jdbcTemplate.batchUpdate(
                    """
                            INSERT INTO cf_problem (
                                problem_key, contest_id, problem_index, name, rating, tags,
                                is_interactive, source_contest_id, solved_count, source_url, last_synced_at
                            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            ON DUPLICATE KEY UPDATE
                                contest_id = VALUES(contest_id),
                                problem_index = VALUES(problem_index),
                                name = VALUES(name),
                                rating = VALUES(rating),
                                tags = VALUES(tags),
                                is_interactive = VALUES(is_interactive),
                                source_contest_id = VALUES(source_contest_id),
                                solved_count = VALUES(solved_count),
                                source_url = VALUES(source_url),
                                last_synced_at = VALUES(last_synced_at)
                            """,
                    problems,
                    300,
                    (ps, problem) -> {
                        ps.setString(1, problem.problemKey());
                        ps.setInt(2, problem.contestId());
                        ps.setString(3, problem.problemIndex());
                        ps.setString(4, problem.name());
                        if (problem.rating() == null) {
                            ps.setObject(5, null);
                        } else {
                            ps.setInt(5, problem.rating());
                        }
                        ps.setString(6, problem.tags());
                        ps.setInt(7, problem.interactive() ? 1 : 0);
                        ps.setObject(8, problem.sourceContestId());
                        ps.setObject(9, problem.solvedCount());
                        ps.setString(10, problem.sourceUrl());
                        ps.setString(11, nowIso);
                    }
            );
        } finally {
            practiceTagsRevision.incrementAndGet();
            practiceTagsCache = null;
        }
        return problems.size();
    }

    /**
     * 统计题库数量。
     *
     * @return 题库条数。
     */
    public long countProblems() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM cf_problem", Long.class);
        return count == null ? 0 : count;
    }

    /**
     * 在指定 rating 范围内随机抽取题目，并排除最近重复题。
     *
     * @param minRating        最小 rating。
     * @param maxRating        最大 rating。
     * @param noRepeatAfterDate 不允许重复的起始日期。
     * @return 随机题目。
     */
    public Optional<CfProblem> findRandomProblem(Integer minRating, Integer maxRating, LocalDate noRepeatAfterDate) {
        String sql = """
                SELECT problem_key, contest_id, problem_index, name, rating, tags, is_interactive, source_contest_id, solved_count, source_url
                FROM cf_problem p
                WHERE p.source_contest_id IS NULL
                  AND p.is_interactive = 0
                  AND p.rating IS NOT NULL
                  AND p.rating BETWEEN ? AND ?
                  AND NOT EXISTS (
                    SELECT 1 FROM daily_problem d
                    WHERE d.problem_key = p.problem_key
                      AND d.date >= ?
                  )
                ORDER BY RAND()
                LIMIT 1
                """;
        List<CfProblem> rows = jdbcTemplate.query(sql, cfProblemRowMapper, minRating, maxRating, noRepeatAfterDate.toString());
        return rows.stream().findFirst();
    }

    /**
     * 在指定 rating 范围内随机抽取题目。
     *
     * @param minRating 最小 rating。
     * @param maxRating 最大 rating。
     * @return 随机题目。
     */
    public Optional<CfProblem> findRandomProblem(Integer minRating, Integer maxRating) {
        return findRandomProblem(minRating, maxRating, List.of());
    }

    /**
     * 按 rating 范围和标签随机抽题。多个标签采用 AND 语义，题目必须同时包含全部标签。
     */
    public Optional<CfProblem> findRandomProblem(Integer minRating, Integer maxRating, List<String> tags) {
        String sql = """
                FROM cf_problem p
                WHERE p.source_contest_id IS NULL
                  AND p.is_interactive = 0
                  AND p.rating IS NOT NULL
                  AND p.rating BETWEEN ? AND ?
                """;
        StringBuilder filteredSql = new StringBuilder(sql);
        List<Object> params = new java.util.ArrayList<>();
        params.add(minRating);
        params.add(maxRating);
        if (tags != null) {
            for (String tag : tags) {
                filteredSql.append(" AND LOCATE(CONCAT(',', LOWER(?), ','), CONCAT(',', LOWER(COALESCE(p.tags, '')), ',')) > 0\n");
                params.add(tag);
            }
        }
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) " + filteredSql, Long.class, params.toArray());
        if (count == null || count == 0) {
            return Optional.empty();
        }
        long offset = ThreadLocalRandom.current().nextLong(count);
        String select = "SELECT problem_key, contest_id, problem_index, name, rating, tags, is_interactive, source_contest_id, solved_count, source_url ";
        List<Object> rowParams = new java.util.ArrayList<>(params);
        rowParams.add(offset);
        // 与筛选索引顺序一致，不为全部候选题计算随机值及排序。
        List<CfProblem> rows = jdbcTemplate.query(select + filteredSql + " ORDER BY p.rating, p.id LIMIT 1 OFFSET ?", cfProblemRowMapper, rowParams.toArray());
        // 同步期间候选数可能缩小；只在该竞态下重读第一条，不能因偏移越界误报没有题目。
        if (rows.isEmpty() && offset > 0) {
            rows = jdbcTemplate.query(select + filteredSql + " ORDER BY p.rating, p.id LIMIT 1", cfProblemRowMapper, params.toArray());
        }
        return rows.stream().findFirst();
    }

    /**
     * 从本地题库汇总可用于筛选的标签。
     */
    public List<String> findAvailableProblemTags() {
        PracticeTagsCache cached = practiceTagsCache;
        long revision = practiceTagsRevision.get();
        if (cached != null && cached.revision() == revision && System.nanoTime() < cached.expiresAtNanos()) {
            return cached.tags();
        }
        synchronized (practiceTagsLock) {
            cached = practiceTagsCache;
            revision = practiceTagsRevision.get();
            if (cached != null && cached.revision() == revision && System.nanoTime() < cached.expiresAtNanos()) {
                return cached.tags();
            }
            List<String> tags = loadAvailableProblemTags();
            if (revision == practiceTagsRevision.get()) {
                practiceTagsCache = new PracticeTagsCache(tags, revision, System.nanoTime() + TimeUnit.MINUTES.toNanos(1));
            }
            return tags;
        }
    }

    private record PracticeTagsCache(List<String> tags, long revision, long expiresAtNanos) { }

    private List<String> loadAvailableProblemTags() {
        List<String> tagRows = jdbcTemplate.queryForList(
                "SELECT tags FROM cf_problem WHERE source_contest_id IS NULL AND is_interactive = 0 AND tags IS NOT NULL AND tags <> ''",
                String.class
        );
        return tagRows.stream()
                .flatMap(row -> java.util.Arrays.stream(row.split(",")))
                .map(String::trim)
                .filter(tag -> !tag.isEmpty())
                .map(tag -> tag.toLowerCase(java.util.Locale.ROOT))
                .distinct()
                .sorted()
                .toList();
    }

}
