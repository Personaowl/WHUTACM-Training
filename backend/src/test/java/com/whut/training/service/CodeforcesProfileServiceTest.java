package com.whut.training.service;

import com.whut.training.domain.entity.User;
import com.whut.training.domain.enums.UserRole;
import com.whut.training.repository.CodeforcesProfileSnapshotRepository;
import com.whut.training.repository.CodeforcesRatingHistoryRepository;
import com.whut.training.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CodeforcesProfileServiceTest {

    @Test
    void unavailableCodeforcesDoesNotTriggerARefreshOnEveryPagePoll() {
        UserRepository users = mock(UserRepository.class);
        CodeforcesApiService api = mock(CodeforcesApiService.class);
        CodeforcesProfileSnapshotRepository snapshots = mock(CodeforcesProfileSnapshotRepository.class);
        CodeforcesRatingHistoryRepository history = mock(CodeforcesRatingHistoryRepository.class);
        User user = new User(8L, "member", null, "password", UserRole.USER);
        user.setCodeforcesHandle("audit-handle");
        when(users.findById(8L)).thenReturn(Optional.of(user));
        CodeforcesProfileService service = new CodeforcesProfileService(users, api, snapshots, history, 30, Runnable::run);

        service.getOverview(8L);
        service.getOverview(8L);
        service.getOverview(8L);

        verify(api, org.mockito.Mockito.times(1)).getUserSubmissions("audit-handle", 10000);
    }

    @Test
    void firstOverviewReturnsBasicDataWithoutWaitingForCodeforces() throws Exception {
        UserRepository userRepository = mock(UserRepository.class);
        CodeforcesApiService apiService = mock(CodeforcesApiService.class);
        CodeforcesProfileSnapshotRepository snapshotRepository = mock(CodeforcesProfileSnapshotRepository.class);
        CodeforcesRatingHistoryRepository ratingHistoryRepository = mock(CodeforcesRatingHistoryRepository.class);
        User user = new User(8L, "member", null, "password", UserRole.USER);
        user.setCodeforcesHandle("Persona_owl");
        user.setCodeforcesRating(1961);
        user.setMaxRating(2040);
        CountDownLatch refreshStarted = new CountDownLatch(1);
        CountDownLatch releaseRefresh = new CountDownLatch(1);

        when(userRepository.findById(8L)).thenReturn(Optional.of(user));
        when(snapshotRepository.find(8L, "Persona_owl")).thenReturn(Optional.empty());
        when(apiService.getUserSubmissions("Persona_owl", 10000)).thenAnswer(invocation -> {
            refreshStarted.countDown();
            releaseRefresh.await(2, TimeUnit.SECONDS);
            return Optional.empty();
        });

        CodeforcesProfileService service =
                new CodeforcesProfileService(userRepository, apiService, snapshotRepository, ratingHistoryRepository, 30);

        try {
            var overview = assertTimeout(Duration.ofMillis(500), () -> service.getOverview(8L));
            assertThat(overview.currentRating()).isEqualTo(1961);
            assertThat(overview.maxRating()).isEqualTo(2040);
            assertThat(overview.stale()).isTrue();
            assertThat(overview.syncedAt()).isNull();
            assertTrue(refreshStarted.await(1, TimeUnit.SECONDS));
        } finally {
            releaseRefresh.countDown();
        }
    }

    @Test
    void aggregatesUniqueSolvedProblemsTagsAndRecentRatingChanges() {
        UserRepository userRepository = mock(UserRepository.class);
        CodeforcesApiService apiService = mock(CodeforcesApiService.class);
        CodeforcesProfileSnapshotRepository snapshotRepository =
                mock(CodeforcesProfileSnapshotRepository.class);
        CodeforcesRatingHistoryRepository ratingHistoryRepository = mock(CodeforcesRatingHistoryRepository.class);
        User user = new User(7L, "login-account", null, "password", UserRole.USER);
        user.setCodeforcesHandle("tourist");
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));

        when(apiService.getUserSubmissions("tourist", 10000)).thenReturn(Optional.of(List.of(
                submission(1L, 100, "A", "OK", List.of("dp", "math"), 400L),
                submission(2L, 100, "A", "OK", List.of("dp", "math"), 300L),
                submission(3L, 101, "B", "WRONG_ANSWER", List.of("greedy"), 200L),
                submission(4L, 102, "C", "OK", List.of("dp"), 100L)
        )));
        when(apiService.getUserRatingHistory("tourist")).thenReturn(Optional.of(List.of(
                rating(1L, "Round 1", 1200, 1300, 10L),
                rating(2L, "Round 2", 1300, 1280, 20L)
        )));
        when(apiService.getUserInfo("tourist")).thenReturn(Optional.of(
                new CodeforcesApiService.CodeforcesUserProfile(1280, 1300, false, 500L, null)
        ));

        CodeforcesProfileService service =
                new CodeforcesProfileService(userRepository, apiService, snapshotRepository, ratingHistoryRepository, 30);
        var overview = service.refresh(7L);

        assertThat(overview.solvedCount()).isEqualTo(2);
        assertThat(overview.attemptedCount()).isEqualTo(3);
        assertThat(overview.acceptedSubmissionCount()).isEqualTo(3);
        assertThat(overview.lastSubmissionAtSeconds()).isEqualTo(400L);
        assertThat(overview.tagStats())
                .extracting(stat -> stat.tag() + ":" + stat.count())
                .containsExactly("dp:2", "math:1");
        assertThat(overview.recentContests())
                .extracting(contest -> contest.contestName() + ":" + contest.ratingChange())
                .containsExactly("Round 2:-20", "Round 1:100");
        verify(snapshotRepository).save(eq(7L), eq("tourist"), any());
    }

    @Test
    void fillsMissingRatingHistoryWithoutDownloadingSubmissions() {
        UserRepository userRepository = mock(UserRepository.class);
        CodeforcesApiService apiService = mock(CodeforcesApiService.class);
        CodeforcesProfileSnapshotRepository snapshotRepository = mock(CodeforcesProfileSnapshotRepository.class);
        CodeforcesRatingHistoryRepository ratingHistoryRepository = mock(CodeforcesRatingHistoryRepository.class);
        User user = new User(9L, "legacy", null, "password", UserRole.USER);
        user.setCodeforcesHandle("legacy_handle");

        when(ratingHistoryRepository.existsByUserId(9L)).thenReturn(false);
        when(userRepository.findById(9L)).thenReturn(Optional.of(user));
        List<CodeforcesApiService.CodeforcesRatingChange> changes = List.of(
                rating(3L, "Round 3", 1500, 1560, 30L)
        );
        when(apiService.getUserRatingHistory("legacy_handle")).thenReturn(Optional.of(changes));

        CodeforcesProfileService service = new CodeforcesProfileService(
                userRepository, apiService, snapshotRepository, ratingHistoryRepository, 30
        );
        service.ensureRatingHistory(9L);

        verify(ratingHistoryRepository).replaceForUser(9L, changes);
        verify(apiService, org.mockito.Mockito.never()).getUserSubmissions(any(), org.mockito.ArgumentMatchers.anyInt());
    }

    private CodeforcesApiService.CodeforcesSubmission submission(
            Long id,
            int contestId,
            String index,
            String verdict,
            List<String> tags,
            long time
    ) {
        return new CodeforcesApiService.CodeforcesSubmission(
                id, contestId, index, "Problem " + index, 1400, tags, verdict, time
        );
    }

    private CodeforcesApiService.CodeforcesRatingChange rating(
            Long contestId,
            String name,
            int oldRating,
            int newRating,
            long time
    ) {
        return new CodeforcesApiService.CodeforcesRatingChange(
                contestId, name, 100, oldRating, newRating, time
        );
    }
}
