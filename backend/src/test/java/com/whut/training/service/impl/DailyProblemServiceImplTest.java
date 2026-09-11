package com.whut.training.service.impl;

import com.whut.training.common.TimeProvider;
import com.whut.training.domain.entity.DailyProblemSlot;
import com.whut.training.domain.entity.User;
import com.whut.training.domain.enums.UserRole;
import com.whut.training.repository.DailyProblemRepository;
import com.whut.training.repository.UserRepository;
import com.whut.training.service.CodeforcesApiService;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DailyProblemServiceImplTest {

    @Test
    void emptyProblemPoolStartsOnlyOneBackgroundSyncWithoutBlockingHome() throws Exception {
        DailyProblemRepository repository = mock(DailyProblemRepository.class);
        CodeforcesApiService codeforcesApiService = mock(CodeforcesApiService.class);
        UserRepository userRepository = mock(UserRepository.class);
        TimeProvider timeProvider = mock(TimeProvider.class);
        LocalDate today = LocalDate.of(2026, 8, 1);
        CountDownLatch fetchStarted = new CountDownLatch(1);
        CountDownLatch releaseFetch = new CountDownLatch(1);

        when(timeProvider.today()).thenReturn(today);
        when(repository.findDailySlotsByDate(today)).thenReturn(List.of());
        when(repository.countProblems()).thenReturn(0L);
        when(codeforcesApiService.fetchProblemSet()).thenAnswer(invocation -> {
            fetchStarted.countDown();
            releaseFetch.await(2, TimeUnit.SECONDS);
            return List.of();
        });

        DailyProblemServiceImpl service = new DailyProblemServiceImpl(
                repository,
                codeforcesApiService,
                userRepository,
                timeProvider,
                1200,
                2000,
                90,
                800,
                1800,
                1900,
                3000,
                true
        );

        try {
            assertTimeout(Duration.ofMillis(500), () -> assertFalse(service.ensureTodaySlots()));
            assertTrue(fetchStarted.await(1, TimeUnit.SECONDS));
            assertTimeout(Duration.ofMillis(500), () -> assertFalse(service.ensureTodaySlots()));
            verify(codeforcesApiService, times(1)).fetchProblemSet();
        } finally {
            releaseFetch.countDown();
        }
    }

    @Test
    void checksOneSubmissionOnlyOnceWhenMatchingTheSecondDailySlot() {
        DailyProblemRepository repository = mock(DailyProblemRepository.class);
        CodeforcesApiService codeforcesApiService = mock(CodeforcesApiService.class);
        UserRepository userRepository = mock(UserRepository.class);
        TimeProvider timeProvider = mock(TimeProvider.class);
        LocalDate today = LocalDate.of(2026, 8, 2);
        List<DailyProblemSlot> slots = List.of(
                new DailyProblemSlot(1L, today, "easy", "100-A", 100, "A", "Easy", 1200,
                        "math", "https://codeforces.com/problemset/problem/100/A", false),
                new DailyProblemSlot(2L, today, "hard", "200-B", 200, "B", "Hard", 1800,
                        "dp", "https://codeforces.com/problemset/problem/200/B", false)
        );
        User user = new User(7L, "owl", null, "password", UserRole.USER);
        user.setCodeforcesHandle("Persona_owl");

        when(timeProvider.today()).thenReturn(today);
        when(repository.findDailySlotsByDate(today)).thenReturn(slots);
        when(codeforcesApiService.getSubmissionStatus("Persona_owl", 999L)).thenReturn(Optional.of(
                new CodeforcesApiService.SubmissionStatus(999L, 200, "B", "OK", Instant.ofEpochSecond(1_000L))
        ));
        when(repository.findUserDailySlotStatus(7L, today, "200-B")).thenReturn(Optional.empty());
        when(repository.findUserDailyStatus(7L, today)).thenReturn(Optional.empty());
        when(repository.maxUserDailySlotScore(7L, today)).thenReturn(1800);

        DailyProblemServiceImpl service = new DailyProblemServiceImpl(
                repository, codeforcesApiService, userRepository, timeProvider,
                1200, 2000, 90, 800, 1800, 1900, 3000, true
        );
        var result = service.checkIn(user, 999L);

        assertEquals("HARD", result.type());
        assertEquals(1800, result.score());
        verify(codeforcesApiService, times(1)).getSubmissionStatus("Persona_owl", 999L);
        verify(userRepository).incrementTotalPoints(7L, 1800);
    }

    @Test
    void checksEasyAndThenHardIndependentlyOnTheSameDay() {
        DailyProblemRepository repository = mock(DailyProblemRepository.class);
        CodeforcesApiService codeforcesApiService = mock(CodeforcesApiService.class);
        UserRepository userRepository = mock(UserRepository.class);
        TimeProvider timeProvider = mock(TimeProvider.class);
        LocalDate today = LocalDate.of(2026, 9, 6);
        List<DailyProblemSlot> slots = List.of(
                new DailyProblemSlot(1L, today, "easy", "100-A", 100, "A", "Easy", 1800,
                        "math", "https://codeforces.com/problemset/problem/100/A", false),
                new DailyProblemSlot(2L, today, "hard", "200-B", 200, "B", "Hard", 2500,
                        "dp", "https://codeforces.com/problemset/problem/200/B", false)
        );
        User user = new User(7L, "owl", null, "password", UserRole.USER);
        user.setCodeforcesHandle("Persona_owl");

        when(timeProvider.today()).thenReturn(today);
        when(repository.findDailySlotsByDate(today)).thenReturn(slots);
        when(codeforcesApiService.getSubmissionStatus("Persona_owl", 101L)).thenReturn(Optional.of(
                new CodeforcesApiService.SubmissionStatus(101L, 100, "A", "OK", Instant.ofEpochSecond(1_000L))
        ));
        when(codeforcesApiService.getSubmissionStatus("Persona_owl", 202L)).thenReturn(Optional.of(
                new CodeforcesApiService.SubmissionStatus(202L, 200, "B", "OK", Instant.ofEpochSecond(1_001L))
        ));
        when(repository.findUserDailySlotStatus(7L, today, "100-A")).thenReturn(Optional.empty());
        when(repository.findUserDailySlotStatus(7L, today, "200-B")).thenReturn(Optional.empty());
        when(repository.findUserDailyStatus(7L, today))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(new com.whut.training.domain.entity.UserDailyStatus(7L, today, 101L, "OK", 1800)));
        when(repository.maxUserDailySlotScore(7L, today))
                .thenReturn(1800)
                .thenReturn(2500);

        DailyProblemServiceImpl service = new DailyProblemServiceImpl(
                repository, codeforcesApiService, userRepository, timeProvider,
                1200, 2000, 90, 800, 1800, 1900, 3000, true
        );

        var easyResult = service.checkIn(user, 101L);
        var hardResult = service.checkIn(user, 202L);

        assertEquals("EASY", easyResult.type());
        assertEquals(1800, easyResult.score());
        assertEquals("HARD", hardResult.type());
        assertEquals(2500, hardResult.score());
        var order = inOrder(userRepository);
        order.verify(userRepository).incrementTotalPoints(7L, 1800);
        order.verify(userRepository).incrementTotalPoints(7L, 700);
    }
}
