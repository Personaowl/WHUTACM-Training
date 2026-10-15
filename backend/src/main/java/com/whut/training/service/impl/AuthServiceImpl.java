package com.whut.training.service.impl;

import com.whut.training.aspect.annotation.ServiceLog;
import com.whut.training.common.TimeProvider;
import com.whut.training.domain.dto.LoginRequest;
import com.whut.training.domain.dto.LoginResponse;
import com.whut.training.domain.dto.RefreshTokenResponse;
import com.whut.training.domain.entity.User;
import com.whut.training.exception.BusinessException;
import com.whut.training.repository.AuthTokenSessionRepository;
import com.whut.training.repository.AuthTokenSessionRepository.AuthTokenSession;
import com.whut.training.repository.UserRepository;
import com.whut.training.service.AuthService;
import com.whut.training.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.UUID;

/**
 * 认证服务实现。
 *
 * <p>负责账号密码登录、token 续期及退出；认证不依赖外部竞赛平台可用性。
 * 新密码使用 BCrypt；旧明文账号会在首次成功登录时自动升级。
 */
@Service
@ServiceLog
public class AuthServiceImpl implements AuthService {

    private final long accessTokenTtlSeconds;
    private final long refreshTokenTtlSeconds;

    private final UserService userService;
    private final UserRepository userRepository;
    private final AuthTokenSessionRepository authTokenSessionRepository;
    private final TimeProvider timeProvider;
    private final PasswordEncoder passwordEncoder;

    public AuthServiceImpl(
            UserService userService,
            UserRepository userRepository,
            AuthTokenSessionRepository authTokenSessionRepository,
            TimeProvider timeProvider,
            PasswordEncoder passwordEncoder,
            @Value("${app.auth.access-token-ttl-seconds:7200}") long accessTokenTtlSeconds,
            @Value("${app.auth.refresh-token-ttl-seconds:21600}") long refreshTokenTtlSeconds
    ) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.authTokenSessionRepository = authTokenSessionRepository;
        this.timeProvider = timeProvider;
        this.passwordEncoder = passwordEncoder;
        this.accessTokenTtlSeconds = accessTokenTtlSeconds;
        this.refreshTokenTtlSeconds = refreshTokenTtlSeconds;
    }

    /**
     * 校验用户名和密码并签发 token。
     *
     * @param request 登录请求。
     * @return 登录响应。
     */
    @Override
    public LoginResponse login(LoginRequest request) {
        User user;
        try {
            user = userService.getByUsername(request.getUsername());
        } catch (BusinessException ex) {
            throw new BusinessException(401, "invalid username or password");
        }
        String storedPassword = user.getPassword();
        boolean encodedPassword = storedPassword != null && storedPassword.startsWith("$2");
        boolean passwordMatches = encodedPassword
                ? passwordEncoder.matches(request.getPassword(), storedPassword)
                : storedPassword != null && storedPassword.equals(request.getPassword());
        if (!passwordMatches) {
            throw new BusinessException(401, "invalid username or password");
        }
        if (!encodedPassword) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
            userRepository.save(user);
        }
        TokenPair pair = issueTokenPair(user.getId());

        return new LoginResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole(),
                user.getUid(),
                user.getCodeforcesRating(),
                user.getMaxRating(),
                user.getOnline(),
                user.getLastOnlineTimeSeconds(),
                user.getAvatarUrl(),
                user.getCodeforcesHandle(),
                user.getPendingCodeforcesHandle(),
                user.getCodeforcesBindingStartedAtSeconds(),
                user.getDisplayName(),
                pair.accessToken(),
                pair.refreshToken()
        );
    }

    /**
     * 使用 refresh token 换取新的 token 对。
     *
     * @param refreshToken 刷新令牌。
     * @return 新的刷新响应。
     */
    @Override
    @Transactional
    public RefreshTokenResponse refresh(String refreshToken) {
        AuthTokenSession refreshSession = validateRefreshSession(refreshToken);
        if (authTokenSessionRepository.deleteByRefreshToken(refreshToken) != 1) {
            throw new BusinessException(401, "refresh token has already been used");
        }

        TokenPair nextPair = issueTokenPair(refreshSession.userId(), refreshSession.createdAtSeconds());
        return new RefreshTokenResponse(nextPair.accessToken(), nextPair.refreshToken());
    }

    /**
     * 注销登录会话。
     *
     * @param accessToken  可选的 access token。
     * @param refreshToken refresh token。
     */
    @Override
    public void logout(String accessToken, String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException(401, "invalid refresh token");
        }

        AuthTokenSession refreshSession = authTokenSessionRepository.findByRefreshToken(refreshToken).orElse(null);
        if (refreshSession == null) {
            throw new BusinessException(401, "invalid refresh token");
        }

        authTokenSessionRepository.deleteByRefreshToken(refreshToken);
        authTokenSessionRepository.deleteByAccessToken(refreshSession.accessToken());
        if (accessToken != null && !accessToken.isBlank()) {
            authTokenSessionRepository.deleteByAccessToken(accessToken);
        }
    }

    /**
     * 校验 access token 并读取用户。
     *
     * @param accessToken access token。
     * @return 当前用户。
     */
    @Override
    public User validateAccessTokenAndGetUser(String accessToken) {
        AuthTokenSession accessSession = validateAccessSession(accessToken);
        return userService.getById(accessSession.userId());
    }

    /**
     * 校验 access token 是否有效。
     *
     * @param accessToken access token。
     * @return 对应会话。
     */
    private AuthTokenSession validateAccessSession(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new BusinessException(401, "invalid access token");
        }
        AuthTokenSession accessSession = authTokenSessionRepository.findByAccessToken(accessToken)
                .orElseThrow(() -> new BusinessException(401, "invalid access token"));
        long nowSeconds = timeProvider.nowEpochSecond();
        if (nowSeconds >= accessSession.accessExpiredAtSeconds()) {
            throw new BusinessException(401, "access token expired");
        }
        return accessSession;
    }

    /**
     * 校验 refresh token 是否有效。
     *
     * @param refreshToken refresh token。
     * @return 对应会话。
     */
    private AuthTokenSession validateRefreshSession(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException(401, "invalid refresh token");
        }

        AuthTokenSession refreshSession = authTokenSessionRepository.findByRefreshToken(refreshToken)
                .orElseThrow(() -> new BusinessException(401, "invalid refresh token"));

        long nowSeconds = timeProvider.nowEpochSecond();
        if (nowSeconds >= refreshSession.refreshExpiredAtSeconds()) {
            authTokenSessionRepository.deleteByRefreshToken(refreshToken);
            authTokenSessionRepository.deleteByAccessToken(refreshSession.accessToken());
            throw new BusinessException(401, "refresh token expired");
        }
        return refreshSession;
    }

    /**
     * 签发新的 token 对并持久化会话。
     *
     * @param userId 用户 ID。
     * @return token 对。
     */
    private TokenPair issueTokenPair(Long userId) {
        return issueTokenPair(userId, timeProvider.nowEpochSecond());
    }

    /**
     * 刷新 token 时沿用首次登录时间，避免活跃请求无限延长会话。
     */
    private TokenPair issueTokenPair(Long userId, long sessionStartedAtSeconds) {
        String accessToken = UUID.randomUUID().toString();
        String refreshToken = UUID.randomUUID().toString();
        long nowSeconds = timeProvider.nowEpochSecond();
        long sessionExpiresAtSeconds = sessionStartedAtSeconds + refreshTokenTtlSeconds;
        AuthTokenSession session = new AuthTokenSession(
                userId,
                accessToken,
                refreshToken,
                Math.min(nowSeconds + accessTokenTtlSeconds, sessionExpiresAtSeconds),
                sessionExpiresAtSeconds,
                sessionStartedAtSeconds
        );
        try {
            authTokenSessionRepository.save(session);
        } catch (DataAccessException ex) {
            throw new BusinessException(500, "failed to issue token");
        }
        return new TokenPair(accessToken, refreshToken);
    }

    @Scheduled(fixedDelayString = "${app.auth.cleanup-delay-ms:600000}")
    public void cleanupExpiredSessions() {
        authTokenSessionRepository.deleteExpiredBefore(timeProvider.nowEpochSecond());
    }


    /**
     * 一对 token 的简单封装。
     *
     * @param accessToken  access token。
     * @param refreshToken refresh token。
     */
    private record TokenPair(String accessToken, String refreshToken) {
    }
}
