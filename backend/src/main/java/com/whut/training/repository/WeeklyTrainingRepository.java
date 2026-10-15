package com.whut.training.repository;

import com.whut.training.domain.dto.WeeklyTrainingReport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public class WeeklyTrainingRepository {
    private final JdbcTemplate jdbc;

    public WeeklyTrainingRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void captureRoster(LocalDate week, String now) {
        jdbc.update("""
                INSERT INTO weekly_training_record (week_start, user_id, created_at)
                SELECT ?, id, ? FROM users WHERE member_type = 'ACTIVE_TEAM'
                ON DUPLICATE KEY UPDATE user_id = VALUES(user_id)
                """, week.toString(), now);
    }

    public void ensureRecord(LocalDate week, Long userId, String now) {
        jdbc.update("""
                INSERT INTO weekly_training_record (week_start, user_id, created_at) VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE user_id = VALUES(user_id)
                """, week.toString(), userId, now);
    }

    public List<Roster> findRoster(LocalDate week, long start, long end, Long userId) {
        String filter = userId == null ? "" : " AND u.id = ?";
        Object[] args = userId == null ? new Object[]{week.toString(), start, end}
                : new Object[]{week.toString(), start, end, userId};
        return jdbc.query("""
                SELECT u.id, u.username, u.display_name, u.real_name, u.college, u.class_name,
                       u.avatar_url, u.member_type, u.atcoder_handle
                FROM users u
                WHERE (u.member_type = 'ACTIVE_TEAM'
                    OR EXISTS (SELECT 1 FROM weekly_training_record w WHERE w.user_id = u.id AND w.week_start = ?)
                    OR EXISTS (SELECT 1 FROM atcoder_requirement_member r
                        JOIN atcoder_contest c ON c.contest_id = r.contest_id
                        WHERE r.user_id = u.id AND r.required = 1
                          AND c.start_time_seconds >= ? AND c.start_time_seconds < ?))
                """ + filter + " ORDER BY u.username", (rs, row) -> new Roster(rs.getLong("id"),
                rs.getString("username"), rs.getString("display_name"), rs.getString("real_name"),
                rs.getString("college"), rs.getString("class_name"), rs.getString("avatar_url"),
                rs.getString("member_type"), rs.getString("atcoder_handle")), args);
    }

    public List<DailyRow> findDaily(LocalDate week, Long userId) {
        String filter = userId == null ? "" : " AND user_id = ?";
        Object[] args = userId == null
                ? new Object[]{week.toString(), week.plusDays(6).toString(), week.toString(), week.plusDays(6).toString()}
                : new Object[]{week.toString(), week.plusDays(6).toString(), userId,
                    week.toString(), week.plusDays(6).toString(), userId};
        return jdbc.query("""
                SELECT user_id, date, MAX(score) AS score,
                    MAX(easy_done) AS easy_done, MAX(hard_done) AS hard_done
                FROM (
                    SELECT user_id, date, score, 0 AS easy_done, 0 AS hard_done
                    FROM user_daily_status WHERE date BETWEEN ? AND ? AND UPPER(verdict) = 'OK'
                """ + filter + """
                    UNION ALL
                    SELECT user_id, date, score,
                        CASE WHEN slot = 'easy' THEN 1 ELSE 0 END AS easy_done,
                        CASE WHEN slot = 'hard' THEN 1 ELSE 0 END AS hard_done
                    FROM user_daily_slot_status WHERE date BETWEEN ? AND ? AND UPPER(verdict) = 'OK'
                """ + filter + """
                ) activity GROUP BY user_id, date
                """, (rs, row) -> new DailyRow(rs.getLong("user_id"), LocalDate.parse(rs.getString("date")),
                rs.getInt("score"), rs.getInt("easy_done") == 1, rs.getInt("hard_done") == 1), args);
    }

    public List<ContestRow> findContests(long start, long end) {
        return jdbc.query("""
                SELECT contest_id, name, contest_url, start_time_seconds, end_time_seconds,
                       member_snapshot_frozen FROM atcoder_contest
                WHERE start_time_seconds >= ? AND start_time_seconds < ? ORDER BY start_time_seconds
                """, (rs, row) -> new ContestRow(rs.getString("contest_id"), rs.getString("name"),
                rs.getString("contest_url"), rs.getLong("start_time_seconds"), rs.getLong("end_time_seconds"),
                rs.getInt("member_snapshot_frozen") == 1), start, end);
    }

    public List<ParticipationRow> findParticipation(long start, long end, Long userId) {
        String filter = userId == null ? "" : " AND r.user_id = ?";
        Object[] args = userId == null ? new Object[]{start, end} : new Object[]{start, end, userId};
        return jdbc.query("""
                SELECT r.user_id, r.contest_id, r.exempted, p.compliance_status, p.ac_count, p.checked_at
                FROM atcoder_requirement_member r
                JOIN atcoder_contest c ON c.contest_id = r.contest_id
                LEFT JOIN atcoder_participation p ON p.user_id = r.user_id AND p.contest_id = r.contest_id
                WHERE r.required = 1 AND c.start_time_seconds >= ? AND c.start_time_seconds < ?
                """ + filter, (rs, row) -> new ParticipationRow(rs.getLong("user_id"), rs.getString("contest_id"),
                rs.getInt("exempted") == 1, rs.getString("compliance_status"),
                (Integer) rs.getObject("ac_count"), rs.getString("checked_at")), args);
    }

    public List<WeekRecord> findRecords(LocalDate week, Long userId) {
        return jdbc.query("SELECT w.*, COALESCE(u.display_name, u.username) AS reviewer FROM weekly_training_record w "
                + "LEFT JOIN users u ON u.id = w.reviewed_by WHERE w.week_start = ?"
                + (userId == null ? "" : " AND w.user_id = ?"), recordMapper(),
                userId == null ? new Object[]{week.toString()} : new Object[]{week.toString(), userId});
    }

    public Optional<WeekRecord> lockRecord(LocalDate week, Long userId) {
        return jdbc.query("SELECT w.*, NULL AS reviewer FROM weekly_training_record w WHERE week_start = ? AND user_id = ? FOR UPDATE",
                recordMapper(), week.toString(), userId).stream().findFirst();
    }

    public void submitExplanation(LocalDate week, Long userId, String content, String now) {
        jdbc.update("""
                UPDATE weekly_training_record SET explanation = ?, submitted_at = ?, review_status = 'PENDING',
                    review_note = NULL, reviewed_by = NULL, reviewed_at = NULL, version = version + 1
                WHERE week_start = ? AND user_id = ?
                """, content, now, week.toString(), userId);
    }

    public void review(LocalDate week, Long userId, String reviewStatus, String manualStatus,
                       String note, Long actorId, String now) {
        jdbc.update("""
                UPDATE weekly_training_record SET review_status = ?, manual_status = ?, review_note = ?,
                    reviewed_by = ?, reviewed_at = ?, version = version + 1
                WHERE week_start = ? AND user_id = ?
                """, reviewStatus, manualStatus, note, actorId, now, week.toString(), userId);
    }

    public void appendAudit(LocalDate week, Long userId, Long actorId, String action, String message,
                            String resultStatus, String now) {
        jdbc.update("""
                INSERT INTO weekly_training_audit
                    (week_start, user_id, actor_id, action, message, result_status, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, week.toString(), userId, actorId, action, message, resultStatus, now);
    }

    public List<AuditRow> findHistory(LocalDate week, Long userId) {
        String filter = userId == null ? "" : " AND a.user_id = ?";
        return jdbc.query("""
                SELECT * FROM (
                    SELECT a.*, COALESCE(u.display_name, u.username, '已注销用户') AS actor,
                        ROW_NUMBER() OVER (PARTITION BY a.user_id ORDER BY a.id DESC) AS row_num
                    FROM weekly_training_audit a LEFT JOIN users u ON u.id = a.actor_id
                    WHERE a.week_start = ?
                """ + filter + ") recent WHERE row_num <= 20 ORDER BY id DESC", (rs, row) -> new AuditRow(
                rs.getLong("user_id"), new WeeklyTrainingReport.Audit(rs.getLong("id"), rs.getString("action"),
                rs.getString("actor"), rs.getString("message"), rs.getString("result_status"), rs.getString("created_at"))),
                userId == null ? new Object[]{week.toString()} : new Object[]{week.toString(), userId});
    }

    private RowMapper<WeekRecord> recordMapper() {
        return (rs, row) -> new WeekRecord(rs.getLong("user_id"), rs.getString("explanation"),
                rs.getString("submitted_at"), rs.getString("review_status"), rs.getString("review_note"),
                (Long) rs.getObject("reviewed_by"), rs.getString("reviewed_at"),
                rs.getString("manual_status"), rs.getLong("version"), rs.getString("reviewer"));
    }

    public record Roster(Long userId, String username, String displayName, String realName, String college,
                         String className, String avatarUrl, String memberType, String atcoderHandle) {}
    public record DailyRow(Long userId, LocalDate date, int score, boolean easy, boolean hard) {}
    public record ContestRow(String id, String name, String url, long start, long end, boolean frozen) {}
    public record ParticipationRow(Long userId, String contestId, boolean exempted, String status,
                                   Integer acCount, String checkedAt) {}
    public record WeekRecord(Long userId, String explanation, String submittedAt, String reviewStatus,
                             String reviewNote, Long reviewedBy, String reviewedAt, String manualStatus, long version,
                             String reviewer) {}
    public record AuditRow(Long userId, WeeklyTrainingReport.Audit audit) {}
}
