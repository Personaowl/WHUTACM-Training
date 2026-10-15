package com.whut.training.service.impl;

import com.whut.training.common.TimeProvider;
import com.whut.training.domain.dto.LoginRequest;
import com.whut.training.domain.entity.User;
import com.whut.training.domain.enums.UserRole;
import com.whut.training.exception.BusinessException;
import com.whut.training.repository.AuthTokenSessionRepository;
import com.whut.training.repository.UserRepository;
import com.whut.training.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock private UserService userService;
    @Mock private UserRepository userRepository;
    @Mock private AuthTokenSessionRepository tokenRepository;
    @Mock private TimeProvider timeProvider;
    @Mock private PasswordEncoder passwordEncoder;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(
                userService,
                userRepository,
                tokenRepository,
                timeProvider,
                passwordEncoder,
                1800,
                604800
        );
        when(timeProvider.nowEpochSecond()).thenReturn(1_000L);
    }

    @Test
    void authenticatesBcryptPasswordWithoutRewritingIt() {
        User user = new User(1L, "owl", null, "$2a$12$encoded", UserRole.USER);
        when(userService.getByUsername("owl")).thenReturn(user);
        when(passwordEncoder.matches("secret12", user.getPassword())).thenReturn(true);

        var response = authService.login(login("owl", "secret12"));

        assertThat(response.accessToken()).isNotBlank();
        verify(passwordEncoder).matches("secret12", "$2a$12$encoded");
        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void upgradesLegacyPlaintextPasswordAfterSuccessfulLogin() {
        User user = new User(2L, "legacy", null, "old-secret", UserRole.USER);
        when(userService.getByUsername("legacy")).thenReturn(user);
        when(passwordEncoder.encode("old-secret")).thenReturn("$2a$12$upgraded");

        authService.login(login("legacy", "old-secret"));

        assertThat(user.getPassword()).isEqualTo("$2a$12$upgraded");
        verify(userRepository).save(user);
    }

    @Test
    void keepsRefreshTokenUsableAfterAccessTokenExpires() {
        var session = new AuthTokenSessionRepository.AuthTokenSession(
                3L, "expired-access", "active-refresh", 999L, 2_000L, 500L
        );
        when(tokenRepository.findByAccessToken("expired-access")).thenReturn(Optional.of(session));
        when(tokenRepository.findByRefreshToken("active-refresh")).thenReturn(Optional.of(session));
        when(tokenRepository.deleteByRefreshToken("active-refresh")).thenReturn(1);

        assertThatThrownBy(() -> authService.validateAccessTokenAndGetUser("expired-access"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("access token expired");
        verify(tokenRepository, never()).deleteByAccessToken("expired-access");

        var refreshed = authService.refresh("active-refresh");

        assertThat(refreshed.accessToken()).isNotBlank();
        assertThat(refreshed.refreshToken()).isNotBlank();
        verify(tokenRepository).deleteByRefreshToken("active-refresh");
        verify(tokenRepository).save(any(AuthTokenSessionRepository.AuthTokenSession.class));
    }

    @Test
    void refreshKeepsTheOriginalSessionDeadline() {
        authService = new AuthServiceImpl(
                userService,
                userRepository,
                tokenRepository,
                timeProvider,
                passwordEncoder,
                7_200,
                21_600
        );
        var session = new AuthTokenSessionRepository.AuthTokenSession(
                3L, "old-access", "active-refresh", 8_200L, 22_600L, 1_000L
        );
        when(timeProvider.nowEpochSecond()).thenReturn(8_000L);
        when(tokenRepository.findByRefreshToken("active-refresh")).thenReturn(Optional.of(session));
        when(tokenRepository.deleteByRefreshToken("active-refresh")).thenReturn(1);

        authService.refresh("active-refresh");

        verify(tokenRepository).save(argThat(saved ->
                saved.createdAtSeconds().equals(1_000L)
                        && saved.refreshExpiredAtSeconds().equals(22_600L)
                        && saved.accessExpiredAtSeconds().equals(15_200L)
        ));
    }

    @Test
    void onlyTheCallerThatConsumesTheRefreshTokenMayIssueANewPair() {
        var session = new AuthTokenSessionRepository.AuthTokenSession(
                3L, "old-access", "already-consumed", 1500L, 2000L, 500L
        );
        when(tokenRepository.findByRefreshToken("already-consumed")).thenReturn(Optional.of(session));
        when(tokenRepository.deleteByRefreshToken("already-consumed")).thenReturn(0);

        assertThatThrownBy(() -> authService.refresh("already-consumed"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("already been used");
        verify(tokenRepository, never()).save(any());
    }

    private LoginRequest login(String username, String password) {
        LoginRequest request = new LoginRequest();
        request.setUsername(username);
        request.setPassword(password);
        return request;
    }
}
