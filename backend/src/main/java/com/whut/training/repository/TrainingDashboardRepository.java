package com.whut.training.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * 训练看板批量统计查询。
 */
@Repository
public class TrainingDashboardRepository {

    private final JdbcTemplate jdbcTemplate;

    public TrainingDashboardRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 查询现役队员每日题完成记录。旧单题表和新题位表合并后按用户、日期去重。
     */
    public List<DailyActivity> findActiveTeamDailyActivity(LocalDate startDate, LocalDate endDate) {
        return jdbcTemplate.query(
                """
                SELECT activity.user_id, activity.date, MAX(activity.score) AS score
                FROM (
                    SELECT user_id, date, score
                    FROM user_daily_status
                    WHERE date BETWEEN ? AND ?
                    UNION ALL
                    SELECT user_id, date, score
                    FROM user_daily_slot_status
                    WHERE date BETWEEN ? AND ?
                ) activity
                INNER JOIN users u ON u.id = activity.user_id
                WHERE u.member_type = 'ACTIVE_TEAM'
                GROUP BY activity.user_id, activity.date
                ORDER BY activity.date ASC
                """,
                (rs, rowNum) -> new DailyActivity(
                        rs.getLong("user_id"),
                        LocalDate.parse(rs.getString("date")),
                        rs.getInt("score")
                ),
                startDate.toString(),
                endDate.toString(),
                startDate.toString(),
                endDate.toString()
        );
    }

    /**
     * 查询近一段时间自主练习抽题与通过数量。
     */
    public List<PracticeSummary> findActiveTeamPracticeSummary(LocalDate startDate, LocalDate endDate) {
        return jdbcTemplate.query(
                """
                SELECT p.user_id,
                       COUNT(1) AS draw_count,
                       SUM(CASE WHEN UPPER(COALESCE(p.verdict, '')) = 'OK' THEN 1 ELSE 0 END) AS solved_count
                FROM user_practice_draw p
                INNER JOIN users u ON u.id = p.user_id
                WHERE u.member_type = 'ACTIVE_TEAM'
                  AND p.draw_date BETWEEN ? AND ?
                GROUP BY p.user_id
                """,
                (rs, rowNum) -> new PracticeSummary(
                        rs.getLong("user_id"),
                        rs.getInt("draw_count"),
                        rs.getInt("solved_count")
                ),
                startDate.toString(),
                endDate.toString()
        );
    }

    public List<DailyExportRow> findActiveTeamDailyExport(LocalDate startDate) {
        String dateFilter = startDate == null ? "" : " AND problems.date >= ?";
        String sql = """
                SELECT u.id AS user_id,
                       u.username,
                       u.display_name,
                       problems.date,
                       problems.slot,
                       problems.problem_key,
                       problems.name,
                       problems.rating,
                       problems.tags,
                       problems.source_url,
                       COALESCE(slot_status.submission_id,
                                CASE WHEN problems.slot = 'daily' THEN daily_status.submission_id END) AS submission_id,
                       COALESCE(slot_status.verdict,
                                CASE WHEN problems.slot = 'daily' THEN daily_status.verdict END) AS verdict,
                       COALESCE(slot_status.score,
                                CASE WHEN problems.slot = 'daily' THEN daily_status.score END) AS score
                FROM users u
                CROSS JOIN (
                    SELECT date, slot, problem_key, name, rating, tags, source_url
                    FROM daily_problem_slot
                    UNION ALL
                    SELECT d.date,
                           'daily' AS slot,
                           d.problem_key,
                           d.name,
                           d.rating,
                           d.tags,
                           d.source_url
                    FROM daily_problem d
                    WHERE NOT EXISTS (
                        SELECT 1 FROM daily_problem_slot s WHERE s.date = d.date
                    )
                ) problems
                LEFT JOIN user_daily_slot_status slot_status
                    ON slot_status.user_id = u.id
                   AND slot_status.date = problems.date
                   AND slot_status.problem_key = problems.problem_key
                LEFT JOIN user_daily_status daily_status
                    ON daily_status.user_id = u.id
                   AND daily_status.date = problems.date
                WHERE u.member_type = 'ACTIVE_TEAM'
                """ + dateFilter + """
                ORDER BY problems.date DESC, problems.slot ASC, u.username ASC
                """;
        Object[] arguments = startDate == null ? new Object[0] : new Object[]{startDate.toString()};
        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new DailyExportRow(
                        rs.getLong("user_id"),
                        rs.getString("username"),
                        rs.getString("display_name"),
                        LocalDate.parse(rs.getString("date")),
                        rs.getString("slot"),
                        rs.getString("problem_key"),
                        rs.getString("name"),
                        rs.getObject("rating", Integer.class),
                        rs.getString("tags"),
                        rs.getString("source_url"),
                        rs.getObject("submission_id") != null,
                        rs.getObject("submission_id", Long.class),
                        rs.getString("verdict"),
                        rs.getObject("score", Integer.class)
                ),
                arguments
        );
    }

    public record DailyActivity(Long userId, LocalDate date, int score) {
    }

    public record PracticeSummary(Long userId, int drawCount, int solvedCount) {
    }

    public record DailyExportRow(
            Long userId,
            String username,
            String displayName,
            LocalDate date,
            String slot,
            String problemKey,
            String problemName,
            Integer rating,
            String tags,
            String sourceUrl,
            boolean completed,
            Long submissionId,
            String verdict,
            Integer score
    ) {
    }

}
