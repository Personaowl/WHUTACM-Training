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
        assertThat(overview.ratingStats()).containsExactly(new com.whut.training.domain.dto.CodeforcesOverview.RatingStat(1400, 2));
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

    @Test
    void difficultyCountsUseOnlyUniqueAcceptedProblemsAndKeepUnratedSeparate() {
        UserRepository users = mock(UserRepository.class);
        CodeforcesApiService api = mock(CodeforcesApiService.class);
        CodeforcesProfileSnapshotRepository snapshots = mock(CodeforcesProfileSnapshotRepository.class);
        User user = new User(7L, "member", null, "password", UserRole.USER);
        user.setCodeforcesHandle("sample");
        when(users.findById(7L)).thenReturn(Optional.of(user));
        when(api.getUserRatingHistory("sample")).thenReturn(Optional.of(List.of()));
        when(api.getUserSubmissions("sample", 10000)).thenReturn(Optional.of(List.of(
                ratedSubmission(1L, "A", 800, "OK"), ratedSubmission(2L, "A", 800, "OK"),
                ratedSubmission(3L, "B", 1900, "WRONG_ANSWER"), ratedSubmission(4L, "C", 1900, "OK"),
                ratedSubmission(5L, "D", null, "OK"), ratedSubmission(6L, "E", 4000, "OK")
        )));
        var service = new CodeforcesProfileService(users, api, snapshots, mock(CodeforcesRatingHistoryRepository.class), 30);
        var overview = service.refresh(7L);
        assertThat(overview.ratingStats()).extracting(stat -> stat.rating() + ":" + stat.count())
                .containsExactly("800:1", "1900:1", "4000:1", "null:1");
        assertThat(overview.solvedCount()).isEqualTo(4);
        assertThat(overview.withStale(true).ratingStats()).isEqualTo(overview.ratingStats());
    }

    @Test
    void legacyJsonSnapshotsRemainReadableAndNewDistributionRoundTrips() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
        var legacy = mapper.readValue("""
                {"handle":"old","solvedCount":4,"tagStats":[],"recentContests":[]}
                """, com.whut.training.domain.dto.CodeforcesOverview.class);
        assertThat(legacy.ratingStats()).isNull();
        assertThat(legacy.withStale(true).solvedCount()).isEqualTo(4);
        var current = new com.whut.training.domain.dto.CodeforcesOverview("new", null, null,
                1, 1, 1, 0, null, false, java.time.Instant.now(), false, List.of(), List.of(),
                List.of(new com.whut.training.domain.dto.CodeforcesOverview.RatingStat(1900, 1)));
        assertThat(mapper.readValue(mapper.writeValueAsString(current), current.getClass())).isEqualTo(current);
    }

    @Test
    void freshLegacySnapshotReturnsImmediatelyAndCoalescesBackfillWithoutRepeatingAfterSuccess() {
        UserRepository users = mock(UserRepository.class);
        CodeforcesApiService api = mock(CodeforcesApiService.class);
        CodeforcesProfileSnapshotRepository snapshots = mock(CodeforcesProfileSnapshotRepository.class);
        User user = new User(7L, "member", null, "password", UserRole.USER);
        user.setCodeforcesHandle("sample");
        when(users.findById(7L)).thenReturn(Optional.of(user));
        var legacy = new com.whut.training.domain.dto.CodeforcesOverview("sample", null, null,
                4, 4, 4, 0, null, false, java.time.Instant.now(), false, List.of(), List.of());
        when(snapshots.find(7L, "sample")).thenReturn(Optional.of(new CodeforcesProfileSnapshotRepository.Snapshot(legacy, legacy.syncedAt())));
        when(api.getUserSubmissions("sample", 10000)).thenReturn(Optional.of(List.of()));
        when(api.getUserRatingHistory("sample")).thenReturn(Optional.of(List.of()));
        var jobs = new java.util.ArrayList<Runnable>();
        var service = new CodeforcesProfileService(users, api, snapshots, mock(CodeforcesRatingHistoryRepository.class), 30, jobs::add);
        for (int index = 0; index < 30; index++) assertThat(service.getOverview(7L).solvedCount()).isEqualTo(4);
        assertThat(jobs).hasSize(1);
        verify(api, org.mockito.Mockito.never()).getUserSubmissions(any(), org.mockito.ArgumentMatchers.anyInt());
        jobs.get(0).run();
        assertThat(service.getOverview(7L).ratingStats()).isEmpty();
        assertThat(jobs).hasSize(1);
    }

    private CodeforcesApiService.CodeforcesSubmission ratedSubmission(Long id, String index, Integer rating, String verdict) {
        return new CodeforcesApiService.CodeforcesSubmission(id, 100, index, "Problem " + index, rating, List.of("dp"), verdict, id);
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
