package com.whut.training.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * 认证会话仓储。
 *
 * <p>负责保存 access token / refresh token 对以及清理过期会话。当前实现是数据库持久化而非内存缓存，因此重启后会话仍可保留到过期时间。
 */
@Repository
public class AuthTokenSessionRepository {

    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<AuthTokenSession> rowMapper = (rs, rowNum) -> new AuthTokenSession(
            rs.getLong("user_id"),
            rs.getString("access_token"),
            rs.getString("refresh_token"),
            rs.getLong("access_expired_at_seconds"),
            rs.getLong("refresh_expired_at_seconds"),
            rs.getLong("created_at_seconds")
    );

    /**
     * 创建认证会话仓储。
     *
     * @param jdbcTemplate JDBC 模板。
     */
    public AuthTokenSessionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 保存认证会话。
     *
     * @param session 会话对象。
     */
    public void save(AuthTokenSession session) {
        jdbcTemplate.update(
                "INSERT INTO auth_token_session (user_id, access_token, refresh_token, access_expired_at_seconds, refresh_expired_at_seconds, created_at_seconds) VALUES (?, ?, ?, ?, ?, ?)",
                session.userId(),
                tokenHash(session.accessToken()),
                tokenHash(session.refreshToken()),
                session.accessExpiredAtSeconds(),
                session.refreshExpiredAtSeconds(),
                session.createdAtSeconds()
        );
    }

    /**
     * 通过 access token 查询会话。
     *
     * @param accessToken access token。
     * @return 会话。
     */
    public Optional<AuthTokenSession> findByAccessToken(String accessToken) {
        List<AuthTokenSession> rows = jdbcTemplate.query(
                "SELECT user_id, access_token, refresh_token, access_expired_at_seconds, refresh_expired_at_seconds, created_at_seconds FROM auth_token_session WHERE access_token IN (?, ?)",
                rowMapper,
                accessToken,
                tokenHash(accessToken)
        );
        return rows.stream().findFirst();
    }

    /**
     * 通过 refresh token 查询会话。
     *
     * @param refreshToken refresh token。
     * @return 会话。
     */
    public Optional<AuthTokenSession> findByRefreshToken(String refreshToken) {
        List<AuthTokenSession> rows = jdbcTemplate.query(
                "SELECT user_id, access_token, refresh_token, access_expired_at_seconds, refresh_expired_at_seconds, created_at_seconds FROM auth_token_session WHERE refresh_token IN (?, ?)",
                rowMapper,
                refreshToken,
                tokenHash(refreshToken)
        );
        return rows.stream().findFirst();
    }

    /**
     * 按 access token 删除会话。
     *
     * @param accessToken access token。
     * @return 删除条数。
     */
    public int deleteByAccessToken(String accessToken) {
        return jdbcTemplate.update(
                "DELETE FROM auth_token_session WHERE access_token IN (?, ?)",
                accessToken,
                tokenHash(accessToken)
        );
    }

    /**
     * 按 refresh token 删除会话。
     *
     * @param refreshToken refresh token。
     * @return 删除条数。
     */
    public int deleteByRefreshToken(String refreshToken) {
        return jdbcTemplate.update(
                "DELETE FROM auth_token_session WHERE refresh_token IN (?, ?)",
                refreshToken,
                tokenHash(refreshToken)
        );
    }

    /**
     * 删除已过期会话。
     *
     * @param epochSeconds 当前时间戳秒。
     * @return 删除条数。
     */
    public int deleteExpiredBefore(long epochSeconds) {
        return jdbcTemplate.update(
                "DELETE FROM auth_token_session WHERE refresh_expired_at_seconds <= ?",
                epochSeconds
        );
    }

    /**
     * 删除指定用户的全部会话；用于管理员重置密码后立即使旧登录失效。
     */
    public int deleteByUserId(Long userId) {
        return jdbcTemplate.update("DELETE FROM auth_token_session WHERE user_id = ?", userId);
    }

    private String tokenHash(String token) {
        if (token == null || token.isBlank()) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    /**
     * 认证会话数据结构。
     *
     * @param userId                  用户 ID。
     * @param accessToken             access token。
     * @param refreshToken            refresh token。
     * @param accessExpiredAtSeconds  access token 过期时间戳。
     * @param refreshExpiredAtSeconds refresh token 过期时间戳。
     * @param createdAtSeconds        本次登录会话的初始创建时间戳。
     */
    public record AuthTokenSession(
            Long userId,
            String accessToken,
            String refreshToken,
            Long accessExpiredAtSeconds,
            Long refreshExpiredAtSeconds,
            Long createdAtSeconds
    ) {
    }
}
