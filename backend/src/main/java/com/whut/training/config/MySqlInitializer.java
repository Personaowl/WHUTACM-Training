package com.whut.training.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * MySQL 表结构初始化器。
 *
 * <p>项目不再迁移旧 SQLite 数据；连接到空的 MySQL 数据库后会自动创建所需表和索引。
 */
@Component
@ConditionalOnProperty(name = "app.database.type", havingValue = "mysql", matchIfMissing = true)
public class MySqlInitializer implements DatabaseInitializer {

    private final JdbcTemplate jdbcTemplate;

    public MySqlInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @PostConstruct
    public void init() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS contest_source_snapshot (
                    platform VARCHAR(16) PRIMARY KEY,
                    payload_json MEDIUMTEXT NOT NULL,
                    synced_at VARCHAR(64) NOT NULL
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS platform_statistics_snapshot (
                    user_id BIGINT NOT NULL,
                    platform VARCHAR(16) NOT NULL,
                    account_id VARCHAR(64) NOT NULL,
                    display_name VARCHAR(128),
                    rating INT,
                    max_rating INT,
                    solved_count INT,
                    synced_at VARCHAR(64) NOT NULL,
                    partial TINYINT(1) NOT NULL DEFAULT 0,
                    PRIMARY KEY (user_id, platform, account_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS upsolve_plan (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    user_id BIGINT NOT NULL,
                    title VARCHAR(255) NOT NULL,
                    link VARCHAR(1000) NOT NULL,
                    link_hash VARCHAR(64) NOT NULL,
                    note VARCHAR(1000),
                    completed TINYINT(1) NOT NULL DEFAULT 0,
                    completed_at TIMESTAMP NULL,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE KEY uk_upsolve_user_link (user_id, link_hash),
                    KEY idx_upsolve_user_status (user_id, completed, id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS user_platform_binding (
                    user_id BIGINT NOT NULL,
                    platform VARCHAR(16) NOT NULL,
                    account_id VARCHAR(18),
                    verified_at BIGINT,
                    pending_account_id VARCHAR(18),
                    verification_token VARCHAR(64),
                    expires_at BIGINT NOT NULL DEFAULT 0,
                    started_at BIGINT NOT NULL DEFAULT 0,
                    last_attempt_at BIGINT NOT NULL DEFAULT 0,
                    PRIMARY KEY (user_id, platform),
                    UNIQUE KEY uk_platform_account (platform, account_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS users (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    username VARCHAR(64) NOT NULL UNIQUE,
                    email VARCHAR(255),
                    password VARCHAR(255) NOT NULL,
                    role VARCHAR(32),
                    uid BIGINT,
                    codeforces_rating INT,
                    max_rating INT,
                    is_online TINYINT(1),
                    last_online_time_seconds BIGINT,
                    avatar_url LONGTEXT,
                    avatar_customized TINYINT(1) NOT NULL DEFAULT 0,
                    total_points INT NOT NULL DEFAULT 0,
                    display_name VARCHAR(100),
                    real_name VARCHAR(100),
                    college VARCHAR(150),
                    class_name VARCHAR(150),
                    bio TEXT,
                    codeforces_handle VARCHAR(64),
                    pending_codeforces_handle VARCHAR(64),
                    codeforces_binding_started_at_seconds BIGINT,
                    atcoder_handle VARCHAR(64),
                    pending_atcoder_handle VARCHAR(64),
                    atcoder_binding_token VARCHAR(64),
                    atcoder_binding_started_at_seconds BIGINT,
                    atcoder_verified_at_seconds BIGINT,
                    member_type VARCHAR(32) NOT NULL DEFAULT 'REGULAR',
                    show_problem_tags TINYINT(1) NOT NULL DEFAULT 1,
                    UNIQUE KEY uk_users_codeforces_handle (codeforces_handle),
                    UNIQUE KEY uk_users_atcoder_handle (atcoder_handle)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS cf_problem (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    problem_key VARCHAR(64) NOT NULL UNIQUE,
                    contest_id INT NOT NULL,
                    problem_index VARCHAR(16) NOT NULL,
                    name VARCHAR(255) NOT NULL,
                    rating INT,
                    tags TEXT,
                    is_interactive TINYINT(1) NOT NULL DEFAULT 0,
                    source_contest_id INT,
                    solved_count INT,
                    source_url TEXT NOT NULL,
                    last_synced_at VARCHAR(64) NOT NULL,
                    KEY idx_cf_problem_rating (rating),
                    KEY idx_cf_problem_contest_idx (contest_id, problem_index)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS daily_problem (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    date VARCHAR(10) NOT NULL UNIQUE,
                    problem_key VARCHAR(64) NOT NULL,
                    contest_id INT NOT NULL,
                    problem_index VARCHAR(16) NOT NULL,
                    name VARCHAR(255) NOT NULL,
                    rating INT,
                    tags TEXT,
                    source_url TEXT NOT NULL,
                    generated_at VARCHAR(64) NOT NULL,
                    generated_by VARCHAR(64) NOT NULL,
                    KEY idx_daily_problem_date (date)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS daily_problem_slot (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    date VARCHAR(10) NOT NULL,
                    slot VARCHAR(32) NOT NULL,
                    problem_key VARCHAR(64) NOT NULL,
                    contest_id INT NOT NULL,
                    problem_index VARCHAR(16) NOT NULL,
                    name VARCHAR(255) NOT NULL,
                    rating INT,
                    tags TEXT,
                    source_url TEXT NOT NULL,
                    generated_at VARCHAR(64) NOT NULL,
                    generated_by VARCHAR(64) NOT NULL,
                    is_redrawn TINYINT(1) NOT NULL DEFAULT 0,
                    KEY idx_daily_problem_slot_date (date)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS user_daily_status (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    user_id BIGINT NOT NULL,
                    date VARCHAR(10) NOT NULL,
                    submission_id BIGINT NOT NULL,
                    verdict VARCHAR(64) NOT NULL,
                    checked_at VARCHAR(64) NOT NULL,
                    score INT NOT NULL DEFAULT 1,
                    UNIQUE KEY uk_user_daily_status (user_id, date),
                    KEY idx_user_daily_status_user (user_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS user_daily_slot_status (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    user_id BIGINT NOT NULL,
                    date VARCHAR(10) NOT NULL,
                    slot VARCHAR(32) NOT NULL,
                    problem_key VARCHAR(64) NOT NULL,
                    submission_id BIGINT NOT NULL,
                    verdict VARCHAR(64) NOT NULL,
                    checked_at VARCHAR(64) NOT NULL,
                    score INT NOT NULL DEFAULT 0,
                    UNIQUE KEY uk_user_daily_slot_status (user_id, date, problem_key),
                    KEY idx_user_daily_slot_status_user_date (user_id, date),
                    KEY idx_user_daily_slot_status_problem (problem_key)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS user_practice_draw (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    user_id BIGINT NOT NULL,
                    draw_date VARCHAR(10) NOT NULL,
                    problem_key VARCHAR(64) NOT NULL,
                    contest_id INT NOT NULL,
                    problem_index VARCHAR(16) NOT NULL,
                    name VARCHAR(255) NOT NULL,
                    rating INT,
                    tags TEXT,
                    source_url TEXT NOT NULL,
                    drawn_at VARCHAR(64) NOT NULL,
                    submission_id BIGINT,
                    verdict VARCHAR(64),
                    checked_at VARCHAR(64),
                    KEY idx_user_practice_draw_user_date (user_id, draw_date)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS auth_token_session (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    user_id BIGINT NOT NULL,
                    access_token VARCHAR(255) NOT NULL UNIQUE,
                    refresh_token VARCHAR(255) NOT NULL UNIQUE,
                    access_expired_at_seconds BIGINT NOT NULL,
                    refresh_expired_at_seconds BIGINT NOT NULL,
                    created_at_seconds BIGINT NOT NULL,
                    KEY idx_auth_token_session_user (user_id),
                    KEY idx_auth_token_session_access_exp (access_expired_at_seconds),
                    KEY idx_auth_token_session_refresh_exp (refresh_expired_at_seconds)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS role_change_log (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    target_user_id BIGINT NOT NULL,
                    changed_by BIGINT NOT NULL,
                    from_role VARCHAR(32),
                    to_role VARCHAR(32) NOT NULL,
                    changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    KEY idx_role_change_target (target_user_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS cf_profile_snapshot (
                    user_id BIGINT PRIMARY KEY,
                    codeforces_handle VARCHAR(64) NOT NULL,
                    payload_json LONGTEXT NOT NULL,
                    synced_at VARCHAR(64) NOT NULL,
                    KEY idx_cf_profile_snapshot_handle (codeforces_handle)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS cf_rating_change (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    user_id BIGINT NOT NULL,
                    contest_id BIGINT NOT NULL,
                    contest_name VARCHAR(255),
                    contest_rank INT,
                    old_rating INT,
                    new_rating INT,
                    rating_change INT,
                    rating_update_time_seconds BIGINT,
                    contest_url TEXT,
                    UNIQUE KEY uk_cf_rating_change_user_contest (user_id, contest_id),
                    KEY idx_cf_rating_change_time (rating_update_time_seconds),
                    KEY idx_cf_rating_change_user_time (user_id, rating_update_time_seconds)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS problem_list (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    owner_user_id BIGINT NOT NULL,
                    name VARCHAR(80) NOT NULL,
                    description VARCHAR(500),
                    is_shared TINYINT(1) NOT NULL DEFAULT 0,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    KEY idx_problem_list_owner (owner_user_id),
                    KEY idx_problem_list_shared_updated (is_shared, updated_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS problem_list_item (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    list_id BIGINT NOT NULL,
                    added_by BIGINT,
                    title VARCHAR(255) NOT NULL,
                    link VARCHAR(1000) NOT NULL,
                    note VARCHAR(1000),
                    problem_key VARCHAR(64),
                    rating INT,
                    tags TEXT,
                    sort_order INT NOT NULL DEFAULT 0,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    KEY idx_problem_list_item_list_order (list_id, sort_order, id),
                    KEY idx_problem_list_item_problem_key (problem_key)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS weekly_training_record (
                    week_start VARCHAR(10) NOT NULL,
                    user_id BIGINT NOT NULL,
                    explanation TEXT,
                    submitted_at VARCHAR(64),
                    review_status VARCHAR(32) NOT NULL DEFAULT 'NONE',
                    review_note TEXT,
                    reviewed_by BIGINT,
                    reviewed_at VARCHAR(64),
                    manual_status VARCHAR(32),
                    version BIGINT NOT NULL DEFAULT 0,
                    created_at VARCHAR(64) NOT NULL,
                    PRIMARY KEY (week_start, user_id),
                    KEY idx_weekly_training_user_week (user_id, week_start)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS weekly_training_audit (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    week_start VARCHAR(10) NOT NULL,
                    user_id BIGINT NOT NULL,
                    actor_id BIGINT NOT NULL,
                    action VARCHAR(32) NOT NULL,
                    message TEXT NOT NULL,
                    result_status VARCHAR(32),
                    created_at VARCHAR(64) NOT NULL,
                    KEY idx_weekly_audit_week_user (week_start, user_id, id),
                    KEY idx_weekly_audit_user_week (user_id, week_start, id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS atcoder_contest (
                    contest_id VARCHAR(64) PRIMARY KEY,
                    name VARCHAR(255) NOT NULL,
                    start_time_seconds BIGINT NOT NULL,
                    end_time_seconds BIGINT NOT NULL,
                    contest_url TEXT NOT NULL,
                    member_snapshot_frozen TINYINT(1) NOT NULL DEFAULT 0,
                    sync_status VARCHAR(32) NOT NULL DEFAULT 'UPCOMING',
                    discovered_at VARCHAR(64) NOT NULL,
                    last_sync_at VARCHAR(64),
                    KEY idx_atcoder_contest_start (start_time_seconds),
                    KEY idx_atcoder_contest_status (sync_status)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS atcoder_requirement_member (
                    contest_id VARCHAR(64) NOT NULL,
                    user_id BIGINT NOT NULL,
                    atcoder_handle_snapshot VARCHAR(64),
                    required TINYINT(1) NOT NULL DEFAULT 1,
                    exempted TINYINT(1) NOT NULL DEFAULT 0,
                    exemption_reason VARCHAR(500),
                    frozen_at VARCHAR(64) NOT NULL,
                    PRIMARY KEY (contest_id, user_id),
                    KEY idx_atcoder_requirement_user (user_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS atcoder_participation (
                    contest_id VARCHAR(64) NOT NULL,
                    user_id BIGINT NOT NULL,
                    participated TINYINT(1) NOT NULL DEFAULT 0,
                    contest_rank INT,
                    performance INT,
                    is_rated TINYINT(1),
                    old_rating INT,
                    new_rating INT,
                    ac_count INT,
                    solved_problem_ids TEXT,
                    compliance_status VARCHAR(32) NOT NULL,
                    source_error VARCHAR(500),
                    checked_at VARCHAR(64) NOT NULL,
                    PRIMARY KEY (contest_id, user_id),
                    KEY idx_atcoder_participation_user (user_id),
                    KEY idx_atcoder_participation_status (compliance_status)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS atcoder_tracking_setting (
                    id INT PRIMARY KEY,
                    minimum_ac_count INT NOT NULL DEFAULT 1,
                    grace_hours INT NOT NULL DEFAULT 24,
                    updated_at VARCHAR(64) NOT NULL,
                    updated_by BIGINT
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
        jdbcTemplate.update("""
                INSERT INTO atcoder_tracking_setting (id, minimum_ac_count, grace_hours, updated_at)
                SELECT 1, 1, 24, ? WHERE NOT EXISTS (SELECT 1 FROM atcoder_tracking_setting WHERE id = 1)
                """, java.time.OffsetDateTime.now().toString());

        ensureColumn("platform_statistics_snapshot", "display_name",
                "ALTER TABLE platform_statistics_snapshot ADD COLUMN display_name VARCHAR(128)");
        ensureColumn("users", "atcoder_handle", "ALTER TABLE users ADD COLUMN atcoder_handle VARCHAR(64)");
        ensureColumn("users", "pending_atcoder_handle", "ALTER TABLE users ADD COLUMN pending_atcoder_handle VARCHAR(64)");
        ensureColumn("users", "atcoder_binding_token", "ALTER TABLE users ADD COLUMN atcoder_binding_token VARCHAR(64)");
        ensureColumn("users", "atcoder_binding_started_at_seconds", "ALTER TABLE users ADD COLUMN atcoder_binding_started_at_seconds BIGINT");
        ensureColumn("users", "atcoder_verified_at_seconds", "ALTER TABLE users ADD COLUMN atcoder_verified_at_seconds BIGINT");
        ensureColumn("users", "real_name", "ALTER TABLE users ADD COLUMN real_name VARCHAR(100)");
        ensureColumn("users", "college", "ALTER TABLE users ADD COLUMN college VARCHAR(150)");
        ensureColumn("users", "class_name", "ALTER TABLE users ADD COLUMN class_name VARCHAR(150)");

        // Retire externally hosted avatars while preserving all uploaded image data.
        jdbcTemplate.update("""
                UPDATE users SET avatar_url = NULL, avatar_customized = 0
                WHERE avatar_url IS NOT NULL AND avatar_url NOT LIKE 'data:image/%'
                """);

        // CREATE TABLE IF NOT EXISTS 不会给已有表补充新索引，因此单独执行轻量迁移。
        ensureIndex(
                "auth_token_session",
                "idx_auth_session_refresh_expiry",
                "ALTER TABLE auth_token_session ADD INDEX idx_auth_session_refresh_expiry (refresh_expired_at_seconds)"
        );
        ensureIndex(
                "users",
                "idx_users_total_points",
                "ALTER TABLE users ADD INDEX idx_users_total_points (total_points)"
        );
        ensureIndex(
                "users",
                "idx_users_member_type",
                "ALTER TABLE users ADD INDEX idx_users_member_type (member_type)"
        );
        ensureIndex(
                "users",
                "uk_users_atcoder_handle",
                "atcoder_handle",
                "ALTER TABLE users ADD UNIQUE INDEX uk_users_atcoder_handle (atcoder_handle)"
        );
        ensureIndex(
                "user_daily_status",
                "idx_user_daily_status_date_user",
                "ALTER TABLE user_daily_status ADD INDEX idx_user_daily_status_date_user (date, user_id)"
        );
        ensureIndex(
                "user_daily_status",
                "idx_user_daily_status_user_checked",
                "ALTER TABLE user_daily_status ADD INDEX idx_user_daily_status_user_checked (user_id, checked_at)"
        );
        ensureIndex(
                "user_daily_slot_status",
                "idx_user_daily_slot_status_date_user",
                "ALTER TABLE user_daily_slot_status ADD INDEX idx_user_daily_slot_status_date_user (date, user_id)"
        );

        ensureIndex("cf_problem", "idx_cf_practice_filter",
                "ALTER TABLE cf_problem ADD INDEX idx_cf_practice_filter (is_interactive, source_contest_id, rating, id)");
        ensureColumn("problem_list", "community_key", "ALTER TABLE problem_list ADD COLUMN community_key VARCHAR(32) UNIQUE");
        ensureColumn("problem_list_item", "added_by", "ALTER TABLE problem_list_item ADD COLUMN added_by BIGINT");
        jdbcTemplate.update("""
                UPDATE problem_list_item SET added_by = (
                    SELECT owner_user_id FROM problem_list WHERE problem_list.id = problem_list_item.list_id
                ) WHERE added_by IS NULL
                """);
        deactivateDuplicateDailySlots();

    }

    /**
     * 保留每个日期、题位最新的一条有效题，将旧版本误生成的重复有效记录归档。
     */
    private void deactivateDuplicateDailySlots() {
        List<ActiveDailySlot> duplicateSlots = jdbcTemplate.query(
                """
                        SELECT date, slot, MAX(id) AS latest_id
                        FROM daily_problem_slot
                        WHERE is_redrawn = 0
                        GROUP BY date, slot
                        HAVING COUNT(*) > 1
                        """,
                (rs, rowNum) -> new ActiveDailySlot(
                        rs.getString("date"),
                        rs.getString("slot"),
                        rs.getLong("latest_id")
                )
        );
        for (ActiveDailySlot duplicateSlot : duplicateSlots) {
            jdbcTemplate.update(
                    """
                            UPDATE daily_problem_slot
                            SET is_redrawn = 1
                            WHERE date = ? AND slot = ? AND is_redrawn = 0 AND id <> ?
                            """,
                    duplicateSlot.date(),
                    duplicateSlot.slot(),
                    duplicateSlot.latestId()
            );
        }
    }

    private record ActiveDailySlot(String date, String slot, long latestId) {
    }

    private void ensureIndex(String tableName, String indexName, String createSql) {
        ensureIndex(tableName, indexName, null, createSql);
    }

    private void ensureIndex(String tableName, String indexName, String indexedColumn, String createSql) {
        Boolean exists = jdbcTemplate.execute((ConnectionCallback<Boolean>) connection -> {
            if (hasIndex(connection, tableName, indexName, indexedColumn)) return true;
            return hasIndex(connection, tableName.toUpperCase(), indexName, indexedColumn);
        });
        if (!Boolean.TRUE.equals(exists)) {
            jdbcTemplate.execute(createSql);
        }
    }

    private boolean hasIndex(java.sql.Connection connection, String tableName, String indexName, String indexedColumn)
            throws java.sql.SQLException {
        try (java.sql.ResultSet indexes = connection.getMetaData().getIndexInfo(
                connection.getCatalog(), null, tableName, false, false
        )) {
            while (indexes.next()) {
                String existingName = indexes.getString("INDEX_NAME");
                if (indexName.equalsIgnoreCase(existingName)) return true;
                String existingColumn = indexes.getString("COLUMN_NAME");
                if (indexedColumn != null && indexedColumn.equalsIgnoreCase(existingColumn)) return true;
            }
            return false;
        }
    }

    private void ensureColumn(String tableName, String columnName, String createSql) {
        Boolean exists = jdbcTemplate.execute((ConnectionCallback<Boolean>) connection -> {
            try (java.sql.ResultSet columns = connection.getMetaData().getColumns(
                    connection.getCatalog(), null, tableName, columnName
            )) {
                if (columns.next()) return true;
            }
            try (java.sql.ResultSet columns = connection.getMetaData().getColumns(
                    connection.getCatalog(), null, tableName.toUpperCase(), columnName.toUpperCase()
            )) {
                return columns.next();
            }
        });
        if (!Boolean.TRUE.equals(exists)) {
            jdbcTemplate.execute(createSql);
        }
    }
}
