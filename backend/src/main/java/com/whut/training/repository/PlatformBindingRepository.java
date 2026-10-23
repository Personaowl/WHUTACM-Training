package com.whut.training.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public class PlatformBindingRepository {
    private final JdbcTemplate jdbc;
    public PlatformBindingRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public record Binding(Long userId, String platform, String accountId, Long verifiedAt,
                          String pendingAccountId, String token, long expiresAt, long startedAt) {}
    public Optional<Binding> find(Long userId, String platform, boolean lock) {
        return jdbc.query("SELECT * FROM user_platform_binding WHERE user_id=? AND platform=?" + (lock ? " FOR UPDATE" : ""),
                (rs, n) -> new Binding(rs.getLong("user_id"), rs.getString("platform"), rs.getString("account_id"),
                    rs.getObject("verified_at", Long.class), rs.getString("pending_account_id"), rs.getString("verification_token"),
                    rs.getLong("expires_at"), rs.getLong("started_at")), userId, platform).stream().findFirst();
    }
    public void ensure(Long userId, String platform) {
        jdbc.update("INSERT INTO user_platform_binding(user_id,platform) VALUES (?,?) ON DUPLICATE KEY UPDATE user_id=VALUES(user_id)", userId, platform);
    }
    public void start(Long userId, String platform, String uid, String token, long now) {
        jdbc.update("UPDATE user_platform_binding SET pending_account_id=?,verification_token=?,expires_at=?,started_at=?,last_attempt_at=0 WHERE user_id=? AND platform=?",
                uid, token, now + 600, now, userId, platform);
    }
    public boolean claimAttempt(Long userId, String platform, long now) {
        return jdbc.update("UPDATE user_platform_binding SET last_attempt_at=? WHERE user_id=? AND platform=? AND last_attempt_at <= ?",
                now, userId, platform, now - 5) == 1;
    }
    public void finish(Long userId, String platform, String uid, long now) {
        jdbc.update("UPDATE user_platform_binding SET account_id=?,verified_at=?,pending_account_id=NULL,verification_token=NULL,expires_at=0 WHERE user_id=? AND platform=?",
                uid, now, userId, platform);
    }
}
