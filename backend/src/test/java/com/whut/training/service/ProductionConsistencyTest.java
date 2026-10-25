package com.whut.training.service;

import com.whut.training.domain.entity.User;
import com.whut.training.domain.enums.UserRole;
import com.whut.training.exception.BusinessException;
import com.whut.training.repository.AuthTokenSessionRepository;
import com.whut.training.repository.DailyProblemRepository;
import com.whut.training.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

/** Real transaction boundaries and concurrent writes against H2 or the disposable mysql-test database. */
@SpringBootTest
class ProductionConsistencyTest {
    @Autowired private DailyCheckInRecorder recorder;
    @Autowired private DailyProblemRepository daily;
    @Autowired private AuthService auth;
    @Autowired private UserService userService;
    @Autowired private JdbcTemplate jdbc;
    @SpyBean private UserRepository users;
    @SpyBean private AuthTokenSessionRepository sessions;
    private final LocalDate date = LocalDate.of(2026, 10, 15);

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM user_daily_slot_status WHERE user_id IN (SELECT id FROM users WHERE username LIKE 'prod-audit-%')");
        jdbc.update("DELETE FROM user_daily_status WHERE user_id IN (SELECT id FROM users WHERE username LIKE 'prod-audit-%')");
        jdbc.update("DELETE FROM auth_token_session WHERE user_id IN (SELECT id FROM users WHERE username LIKE 'prod-audit-%')");
        jdbc.update("DELETE FROM users WHERE username LIKE 'prod-audit-%'");
    }

    private User createUser(String suffix) {
        return users.save(new User(null, "prod-audit-" + suffix, null, "hash", UserRole.USER));
    }

    @Test
    void optionalSchoolInformationIsPersistedAtRegistrationAndCanBeClearedLater() {
        var request = new com.whut.training.domain.dto.UserRegisterRequest();
        request.setUsername("prod-audit-register");
        request.setDisplayName("Member");
        request.setPassword("test-password");
        request.setRealName(" 张三 ");
        request.setCollege(" 计算机学院 ");
        request.setClassName(" 计科2401 ");
        User registered = userService.register(request);
        User saved = users.findById(registered.getId()).orElseThrow();
        assertThat(saved.getRealName()).isEqualTo("张三");
        assertThat(saved.getCollege()).isEqualTo("计算机学院");
        assertThat(saved.getClassName()).isEqualTo("计科2401");

        userService.updateProfile(registered.getId(), new com.whut.training.domain.dto.UserUpdateRequest(
                null, null, null, null, "", "", "", null, null, null
        ));
        User cleared = users.findById(registered.getId()).orElseThrow();
        assertThat(cleared.getRealName()).isNull();
        assertThat(cleared.getCollege()).isNull();
        assertThat(cleared.getClassName()).isNull();
    }

    @Test
    void ledgerRollsBackEveryWriteIfTotalPointsCannotBeSaved() {
        User user = createUser("rollback");
        doThrow(new DataIntegrityViolationException("simulated database write failure"))
                .when(users).incrementTotalPoints(user.getId(), 1200);

        assertThatThrownBy(() -> recorder.record(user.getId(), date, "easy", "audit-easy", 100L, "OK", 1200))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(daily.findUserDailySlotStatus(user.getId(), date, "audit-easy")).isEmpty();
        assertThat(daily.findUserDailyStatus(user.getId(), date)).isEmpty();
        assertThat(users.findById(user.getId()).orElseThrow().getTotalPoints()).isZero();
    }

    @Test
    void parallelEasyHardAndRepeatedSubmissionsOnlyAwardTheDailyMaximum() throws Exception {
        User user = createUser("parallel");
        var workers = Executors.newFixedThreadPool(4);
        try {
            var calls = new ArrayList<Callable<Void>>();
            for (int i = 0; i < 12; i++) {
                boolean hard = i % 2 == 0;
                calls.add(() -> {
                    recorder.record(user.getId(), date, hard ? "hard" : "easy",
                            hard ? "audit-hard" : "audit-easy", hard ? 200L : 100L, "OK", hard ? 1900 : 1200);
                    return null;
                });
            }
            for (var result : workers.invokeAll(calls, 10, TimeUnit.SECONDS)) result.get();
        } finally {
            workers.shutdownNow();
        }
        assertThat(users.findById(user.getId()).orElseThrow().getTotalPoints()).isEqualTo(1900);
        assertThat(daily.findUserDailyStatus(user.getId(), date).orElseThrow().score()).isEqualTo(1900);
    }

    @Test
    void savingAStaleProfileCannotUndoPointsAndBackgroundRatingsCannotUndoPasswordChanges() {
        User user = createUser("profile");
        user.setCodeforcesHandle("audit-handle");
        users.save(user);
        User stale = users.findById(user.getId()).orElseThrow();
        users.incrementTotalPoints(user.getId(), 1900);
        stale.setDisplayName("Updated name");
        stale.setPassword("new-password-hash");
        users.save(stale);
        users.updateCodeforcesRatings(user.getId(), "audit-handle", 1700, 1800);

        User saved = users.findById(user.getId()).orElseThrow();
        assertThat(saved.getTotalPoints()).isEqualTo(1900);
        assertThat(saved.getPassword()).isEqualTo("new-password-hash");
        assertThat(saved.getDisplayName()).isEqualTo("Updated name");
        assertThat(saved.getCodeforcesRating()).isEqualTo(1700);
    }

    @Test
    void failedRefreshInsertionKeepsThePreviousSessionUsable() {
        User user = createUser("refresh");
        long now = System.currentTimeMillis() / 1000;
        sessions.save(new AuthTokenSessionRepository.AuthTokenSession(
                user.getId(), "audit-access", "audit-refresh", now + 300, now + 600, now
        ));
        doThrow(new DataIntegrityViolationException("simulated token insert failure")).when(sessions).save(any());

        assertThatThrownBy(() -> auth.refresh("audit-refresh")).isInstanceOf(BusinessException.class);

        assertThat(sessions.findByRefreshToken("audit-refresh")).isPresent();
        assertThat(sessions.findByAccessToken("audit-access")).isPresent();
    }
}
