package com.whut.training.repository;

import com.whut.training.common.TimeProvider;
import com.whut.training.domain.dto.DailyHeatmapItem;
import com.whut.training.domain.dto.DailyProblemHistoryItem;
import com.whut.training.domain.entity.CfProblem;
import com.whut.training.domain.entity.DailyProblem;
import com.whut.training.domain.entity.UserDailyStatus;
import com.whut.training.domain.entity.UserPracticeDraw;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Statement;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 每日题相关仓储。
 *
 * <p>负责题库同步、每日题落库、打卡记录和练习记录的 MySQL 读写。
 */
@Repository
public class DailyProblemRepository {

    private final JdbcTemplate jdbcTemplate;
    private final TimeProvider timeProvider;

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

    private final RowMapper<DailyProblem> dailyProblemRowMapper = (rs, rowNum) -> new DailyProblem(
            rs.getLong("id"),
            LocalDate.parse(rs.getString("date")),
            rs.getString("problem_key"),
            rs.getInt("contest_id"),
            rs.getString("problem_index"),
            rs.getString("name"),
            (Integer) rs.getObject("rating"),
            rs.getString("tags"),
            rs.getString("source_url")
    );

    private final RowMapper<UserDailyStatus> userDailyStatusRowMapper = (rs, rowNum) -> new UserDailyStatus(
            rs.getLong("user_id"),
            LocalDate.parse(rs.getString("date")),
            rs.getLong("submission_id"),
            rs.getString("verdict"),
            rs.getInt("score")
    );

    private final RowMapper<UserDailyStatus> userDailySlotStatusRowMapper = (rs, rowNum) -> new UserDailyStatus(
            rs.getLong("user_id"),
            LocalDate.parse(rs.getString("date")),
            rs.getLong("submission_id"),
            rs.getString("verdict"),
            rs.getInt("score")
    );

    private final RowMapper<UserPracticeDraw> userPracticeDrawRowMapper = (rs, rowNum) -> new UserPracticeDraw(
            rs.getLong("id"),
            rs.getLong("user_id"),
            LocalDate.parse(rs.getString("draw_date")),
            rs.getString("problem_key"),
            rs.getInt("contest_id"),
            rs.getString("problem_index"),
            rs.getString("name"),
            (Integer) rs.getObject("rating"),
            rs.getString("tags"),
            rs.getString("source_url"),
            (Long) rs.getObject("submission_id"),
            rs.getString("verdict")
    );

            private final RowMapper<com.whut.training.domain.entity.DailyProblemSlot> dailyProblemSlotRowMapper = (rs, rowNum) -> new com.whut.training.domain.entity.DailyProblemSlot(
                rs.getLong("id"),
                LocalDate.parse(rs.getString("date")),
                rs.getString("slot"),
                rs.getString("problem_key"),
                rs.getInt("contest_id"),
                rs.getString("problem_index"),
                rs.getString("name"),
                (Integer) rs.getObject("rating"),
                rs.getString("tags"),
                rs.getString("source_url"),
                rs.getInt("is_redrawn") != 0
            );

    /**
     * 创建每日题仓储。
     *
     * @param jdbcTemplate JDBC 模板。
     */
    public DailyProblemRepository(JdbcTemplate jdbcTemplate, TimeProvider timeProvider) {
        this.jdbcTemplate = jdbcTemplate;
        this.timeProvider = timeProvider;
    }

    /**
     * 按日期查询所有题位（slot），用于支持每日多题（easy/hard）。
     *
     * @param date 日期。
     * @return 题位列表（可能为空）。
     */
    public List<com.whut.training.domain.entity.DailyProblemSlot> findDailySlotsByDate(LocalDate date) {
        return jdbcTemplate.query(
                "SELECT id, date, slot, problem_key, contest_id, problem_index, name, rating, tags, source_url, is_redrawn FROM daily_problem_slot WHERE date = ? ORDER BY slot",
                dailyProblemSlotRowMapper,
                date.toString()
        );
    }

    /**
     * 使指定日期、题位的所有当前有效题失效。
     *
     * <p>题位保留历史重抽记录，因此不能只按第一条记录的 id 更新。一次更新所有
     * {@code is_redrawn = 0} 的记录，可以同时修复旧版本误生成的重复有效题位。
     *
     * @return 本次失效的题位记录数。
     */
    public int markActiveSlotsRedrawn(LocalDate date, String slot) {
        return jdbcTemplate.update(
                "UPDATE daily_problem_slot SET is_redrawn = 1 WHERE date = ? AND slot = ? AND is_redrawn = 0",
                date.toString(),
                slot
        );
    }

    /**
     * 插入每日题位记录。
     */
    public com.whut.training.domain.entity.DailyProblemSlot insertDailySlot(LocalDate date, String slot, CfProblem problem, String generatedBy) {
        String sql = "INSERT INTO daily_problem_slot (date, slot, problem_key, contest_id, problem_index, name, rating, tags, source_url, generated_at, generated_by, is_redrawn) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)";
        String nowIso = OffsetDateTime.now().toString();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, date.toString());
            statement.setString(2, slot);
            statement.setString(3, problem.problemKey());
            statement.setInt(4, problem.contestId());
            statement.setString(5, problem.problemIndex());
            statement.setString(6, problem.name());
            statement.setObject(7, problem.rating());
            statement.setString(8, problem.tags());
            statement.setString(9, problem.sourceUrl());
            statement.setString(10, nowIso);
            statement.setString(11, generatedBy);
            return statement;
        }, keyHolder);
        Long id = keyHolder.getKey() == null ? null : keyHolder.getKey().longValue();
        return new com.whut.training.domain.entity.DailyProblemSlot(
                id,
                date,
                slot,
                problem.problemKey(),
                problem.contestId(),
                problem.problemIndex(),
                problem.name(),
                problem.rating(),
                problem.tags(),
                problem.sourceUrl(),
                false
        );
    }

    /**
     * 删除指定日期的所有题位。
     */
    public void deleteDailySlotsByDate(LocalDate date) {
        jdbcTemplate.update("DELETE FROM daily_problem_slot WHERE date = ?", date.toString());
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
                SELECT problem_key, contest_id, problem_index, name, rating, tags, is_interactive, source_contest_id, solved_count, source_url
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
        filteredSql.append(" ORDER BY RAND() LIMIT 1");
        List<CfProblem> rows = jdbcTemplate.query(filteredSql.toString(), cfProblemRowMapper, params.toArray());
        return rows.stream().findFirst();
    }

    /**
     * 从本地题库汇总可用于筛选的标签。
     */
    public List<String> findAvailableProblemTags() {
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

    /**
     * 按日期查询每日题。
     *
     * @param date 日期。
     * @return 每日题。
     */
    public Optional<DailyProblem> findDailyByDate(LocalDate date) {
        List<DailyProblem> rows = jdbcTemplate.query(
                """
                        SELECT id, date, problem_key, contest_id, problem_index, name, rating, tags, source_url
                        FROM daily_problem
                        WHERE date = ?
                        """,
                dailyProblemRowMapper,
                date.toString()
        );
        return rows.stream().findFirst();
    }

    /**
     * 插入每日题快照。
     *
     * @param date        日期。
     * @param problem     题目实体。
     * @param generatedBy 生成来源。
     * @return 插入后的每日题。
     */
    public DailyProblem insertDailyProblem(LocalDate date, CfProblem problem, String generatedBy) {
        String sql = """
                INSERT INTO daily_problem (
                    date, problem_key, contest_id, problem_index, name, rating, tags, source_url, generated_at, generated_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        String nowIso = OffsetDateTime.now().toString();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, date.toString());
            statement.setString(2, problem.problemKey());
            statement.setInt(3, problem.contestId());
            statement.setString(4, problem.problemIndex());
            statement.setString(5, problem.name());
            statement.setObject(6, problem.rating());
            statement.setString(7, problem.tags());
            statement.setString(8, problem.sourceUrl());
            statement.setString(9, nowIso);
            statement.setString(10, generatedBy);
            return statement;
        }, keyHolder);
        Long id = keyHolder.getKey() == null ? null : keyHolder.getKey().longValue();
        return new DailyProblem(
                id,
                date,
                problem.problemKey(),
                problem.contestId(),
                problem.problemIndex(),
                problem.name(),
                problem.rating(),
                problem.tags(),
                problem.sourceUrl()
        );
    }

    /**
     * 删除指定日期的每日题。
     *
     * @param date 日期。
     */
    public void deleteDailyByDate(LocalDate date) {
        jdbcTemplate.update("DELETE FROM daily_problem WHERE date = ?", date.toString());
    }

    /**
     * 查询用户某天的打卡状态。
     *
     * @param userId 用户 ID。
     * @param date   日期。
     * @return 打卡状态。
     */
    public Optional<UserDailyStatus> findUserDailyStatus(Long userId, LocalDate date) {
        List<UserDailyStatus> rows = jdbcTemplate.query(
                """
                        SELECT user_id, date, submission_id, verdict, score
                        FROM user_daily_status
                        WHERE user_id = ? AND date = ?
                        """,
                userDailyStatusRowMapper,
                userId,
                date.toString()
        );
        return rows.stream().findFirst();
    }

    public Optional<UserDailyStatus> findUserDailySlotStatus(Long userId, LocalDate date, String problemKey) {
        List<UserDailyStatus> rows = jdbcTemplate.query(
                """
                        SELECT user_id, date, submission_id, verdict, score
                        FROM user_daily_slot_status
                        WHERE user_id = ? AND date = ? AND problem_key = ?
                        """,
                userDailySlotStatusRowMapper,
                userId,
                date.toString(),
                problemKey
        );
        return rows.stream().findFirst();
    }

    public void saveUserDailySlotStatus(Long userId, LocalDate date, String slot, String problemKey,
                                        Long submissionId, String verdict, int score) {
        jdbcTemplate.update(
                """
                        INSERT INTO user_daily_slot_status (
                            user_id, date, slot, problem_key, submission_id, verdict, checked_at, score
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        ON DUPLICATE KEY UPDATE
                            slot = VALUES(slot),
                            submission_id = VALUES(submission_id),
                            verdict = VALUES(verdict),
                            checked_at = VALUES(checked_at),
                            score = VALUES(score)
                        """,
                userId,
                date.toString(),
                slot,
                problemKey,
                submissionId,
                verdict,
                OffsetDateTime.now().toString(),
                score
        );
    }

    public int maxUserDailySlotScore(Long userId, LocalDate date) {
        Integer score = jdbcTemplate.queryForObject(
                """
                        SELECT COALESCE(MAX(score), 0)
                        FROM user_daily_slot_status
                        WHERE user_id = ? AND date = ?
                        """,
                Integer.class,
                userId,
                date.toString()
        );
        return score == null ? 0 : score;
    }

    /**
     * 保存今日题打卡结果。
     *
     * @param userId       用户 ID。
     * @param date         日期。
     * @param submissionId 提交 ID。
     * @param verdict      判题结果。
     * @param score        得分。
     */
    public void saveUserDailyStatus(Long userId, LocalDate date, Long submissionId, String verdict, int score) {
        jdbcTemplate.update(
                """
                        INSERT INTO user_daily_status (user_id, date, submission_id, verdict, checked_at, score)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """,
                userId,
                date.toString(),
                submissionId,
                verdict,
                OffsetDateTime.now().toString(),
                score
        );
    }

    public void upsertUserDailyStatus(Long userId, LocalDate date, Long submissionId, String verdict, int score) {
        jdbcTemplate.update(
                """
                        INSERT INTO user_daily_status (user_id, date, submission_id, verdict, checked_at, score)
                        VALUES (?, ?, ?, ?, ?, ?)
                        ON DUPLICATE KEY UPDATE
                            submission_id = VALUES(submission_id),
                            verdict = VALUES(verdict),
                            checked_at = VALUES(checked_at),
                            score = VALUES(score)
                        """,
                userId,
                date.toString(),
                submissionId,
                verdict,
                OffsetDateTime.now().toString(),
                score
        );
    }

        /**
         * 更新已有的用户当日打卡记录（用于当日得分被更高的提交覆盖）。
         */
        public void updateUserDailyStatus(Long userId, LocalDate date, Long submissionId, String verdict, int score) {
        jdbcTemplate.update(
            "UPDATE user_daily_status SET submission_id = ?, verdict = ?, checked_at = ?, score = ? WHERE user_id = ? AND date = ?",
            submissionId,
            verdict,
            OffsetDateTime.now().toString(),
            score,
            userId,
            date.toString()
        );
        }

    /**
     * 查询用户在指定区间的每日题历史。
     *
     * @param userId    用户 ID。
     * @param startDate 起始日期。
     * @param endDate   结束日期。
     * @return 历史列表。
     */
    public List<DailyProblemHistoryItem> findDailyHistoryForUser(Long userId, LocalDate startDate, LocalDate endDate) {
        return jdbcTemplate.query(
                """
                        SELECT p.date,
                               p.slot,
                               p.problem_key,
                               p.name,
                               p.rating,
                               p.tags,
                               p.source_url,
                               p.is_redrawn,
                               CASE
                                   WHEN us.submission_id IS NOT NULL THEN us.submission_id
                                   WHEN p.slot = 'daily' OR ud.score = p.rating THEN ud.submission_id
                                   ELSE NULL
                               END AS submission_id,
                               CASE
                                   WHEN us.submission_id IS NOT NULL THEN us.verdict
                                   WHEN p.slot = 'daily' OR ud.score = p.rating THEN ud.verdict
                                   ELSE NULL
                               END AS verdict,
                               CASE
                                   WHEN us.submission_id IS NOT NULL THEN us.score
                                   WHEN p.slot = 'daily' OR ud.score = p.rating THEN ud.score
                                   ELSE NULL
                               END AS score
                        FROM (
                            SELECT date, slot, problem_key, name, rating, tags, source_url, is_redrawn
                            FROM daily_problem_slot
                            WHERE date BETWEEN ? AND ?
                            UNION ALL
                            SELECT d.date,
                                   'daily' AS slot,
                                   d.problem_key,
                                   d.name,
                                   d.rating,
                                   d.tags,
                                   d.source_url,
                                   0 AS is_redrawn
                            FROM daily_problem d
                            WHERE d.date BETWEEN ? AND ?
                              AND NOT EXISTS (
                                  SELECT 1 FROM daily_problem_slot s
                                  WHERE s.date = d.date
                              )
                        ) p
                        LEFT JOIN user_daily_slot_status us
                            ON us.user_id = ? AND us.date = p.date AND us.problem_key = p.problem_key
                        LEFT JOIN user_daily_status ud
                            ON ud.user_id = ? AND ud.date = p.date
                        ORDER BY p.date DESC, p.slot ASC
                        """,
                (rs, rowNum) -> new DailyProblemHistoryItem(
                        rs.getString("date"),
                        rs.getString("slot"),
                        rs.getString("problem_key"),
                        rs.getString("name"),
                        (Integer) rs.getObject("rating"),
                        rs.getString("tags"),
                        rs.getString("source_url"),
                        rs.getInt("is_redrawn") != 0,
                        rs.getObject("submission_id") != null,
                        (Long) rs.getObject("submission_id"),
                        rs.getString("verdict"),
                        (Integer) rs.getObject("score")
                ),
                startDate.toString(),
                endDate.toString(),
                startDate.toString(),
                endDate.toString(),
                userId,
                userId
        );
    }

    /**
     * 插入练习题抽题记录。
     *
     * @param userId   用户 ID。
     * @param drawDate 抽题日期。
     * @param problem  题目实体。
     * @return 抽题记录。
     */
    public UserPracticeDraw insertPracticeDraw(Long userId, LocalDate drawDate, CfProblem problem) {
        String sql = """
                INSERT INTO user_practice_draw (
                    user_id, draw_date, problem_key, contest_id, problem_index, name, rating,
                    tags, source_url, drawn_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        String nowIso = OffsetDateTime.now().toString();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, userId);
            statement.setString(2, drawDate.toString());
            statement.setString(3, problem.problemKey());
            statement.setInt(4, problem.contestId());
            statement.setString(5, problem.problemIndex());
            statement.setString(6, problem.name());
            statement.setObject(7, problem.rating());
            statement.setString(8, problem.tags());
            statement.setString(9, problem.sourceUrl());
            statement.setString(10, nowIso);
            return statement;
        }, keyHolder);
        Long id = keyHolder.getKey() == null ? null : keyHolder.getKey().longValue();

        return new UserPracticeDraw(
                id,
                userId,
                drawDate,
                problem.problemKey(),
                problem.contestId(),
                problem.problemIndex(),
                problem.name(),
                problem.rating(),
                problem.tags(),
                problem.sourceUrl(),
                null,
                null
        );
    }

    public int countCheckedInUsersByDate(LocalDate date) {
        Integer c = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(DISTINCT user_id)
                        FROM (
                            SELECT user_id FROM user_daily_status WHERE date = ?
                            UNION
                            SELECT user_id FROM user_daily_slot_status WHERE date = ?
                        ) t
                        """,
                Integer.class,
                date.toString(),
                date.toString()
        );
        return c == null ? 0 : c;
    }

    public int countSubmissionsByDate(LocalDate date) {
        Integer c = jdbcTemplate.queryForObject(
                """
                        SELECT
                            (SELECT COUNT(1) FROM user_daily_slot_status WHERE date = ?)
                            +
                            (SELECT COUNT(1)
                             FROM user_daily_status uds
                             WHERE uds.date = ?
                               AND NOT EXISTS (
                                   SELECT 1
                                   FROM user_daily_slot_status uss
                                   WHERE uss.user_id = uds.user_id
                                     AND uss.date = uds.date
                               ))
                        """,
                Integer.class,
                date.toString(),
                date.toString()
        );
        return c == null ? 0 : c;
    }

    public int countActiveUsers(int days) {
        LocalDate since = timeProvider.today().minusDays(days - 1L);
        Integer c = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT user_id) FROM user_daily_status WHERE date >= ?",
                Integer.class,
                since.toString()
        );
        return c == null ? 0 : c;
    }

    public List<DailyHeatmapItem> findHeatmapForUser(Long userId, int days) {
        LocalDate endDate = timeProvider.today();
        LocalDate startDate = endDate.minusDays(days - 1L);
        List<DailyHeatmapItem> items = jdbcTemplate.query(
                "SELECT date, score FROM user_daily_status WHERE user_id = ? AND date BETWEEN ? AND ? ORDER BY date ASC",
                (rs, rowNum) -> new DailyHeatmapItem(
                        rs.getString("date"),
                        rs.getInt("score"),
                        0 // colorLevel computed in service
                ),
                userId, startDate.toString(), endDate.toString()
        );

        int maxScore = items.stream().mapToInt(DailyHeatmapItem::score).max().orElse(1);
        return items.stream()
                .map(item -> new DailyHeatmapItem(item.date(), item.score(),
                        item.score() > 0 && maxScore > 0
                                ? Math.max(1, Math.min(4, (int) Math.ceil(item.score() * 4.0 / maxScore)))
                                : 0))
                .toList();
    }

    public List<UserPracticeDraw> findPracticeDrawsByUserId(Long userId, int limit) {
        return jdbcTemplate.query(
                "SELECT id, user_id, draw_date, problem_key, contest_id, problem_index, name, rating, tags, source_url, drawn_at, submission_id, verdict, checked_at FROM user_practice_draw WHERE user_id = ? ORDER BY drawn_at DESC LIMIT ?",
                userPracticeDrawRowMapper,
                userId,
                limit
        );
    }

    public boolean deletePracticeDraw(Long drawId, Long userId) {
        int rows = jdbcTemplate.update(
                "DELETE FROM user_practice_draw WHERE id = ? AND user_id = ?",
                drawId, userId
        );
        return rows > 0;
    }
}
