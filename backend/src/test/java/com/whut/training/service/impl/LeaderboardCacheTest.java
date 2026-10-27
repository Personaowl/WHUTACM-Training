package com.whut.training.service.impl;

import com.whut.training.domain.dto.LeaderboardItem;
import com.whut.training.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class LeaderboardCacheTest {
    private LeaderboardItem item(int points) {
        var row = new LeaderboardItem(); row.setUserId(7L); row.setUsername("member"); row.setTotalPoints(points);
        return row;
    }

    @Test void fortyParallelPageAndTotalReadsShareOneQueryOfEach() throws Exception {
        var repo = mock(UserRepository.class);
        var started = new CountDownLatch(1); var release = new CountDownLatch(1);
        when(repo.findTopByTotalPoints(50, 0)).thenAnswer(call -> {
            started.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("test release missing");
            return List.of(item(1900));
        });
        when(repo.countRankedUsers()).thenReturn(1);
        var service = new LeaderboardServiceImpl(repo, 3000, () -> 0L);
        var executor = Executors.newFixedThreadPool(8);
        try {
            var requests = new ArrayList<Future<Integer>>();
            for (int n = 0; n < 40; n++) requests.add(executor.submit(() -> {
                assertThat(service.countTotal()).isEqualTo(1);
                return service.getTop(50, 0).get(0).getTotalPoints();
            }));
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue(); release.countDown();
            for (var request : requests) assertThat(request.get(5, TimeUnit.SECONDS)).isEqualTo(1900);
            verify(repo, times(1)).findTopByTotalPoints(50, 0);
            verify(repo, times(1)).countRankedUsers();
        } finally { release.countDown(); executor.shutdownNow(); }
    }

    @Test void expirationAndWriteRevisionBothInvalidatePagesAndTotals() {
        var repo = mock(UserRepository.class); var now = new AtomicLong(); var revision = new AtomicLong();
        when(repo.rankingVersion()).thenAnswer(call -> revision.get());
        when(repo.findTopByTotalPoints(50, 0)).thenReturn(List.of(item(1200)), List.of(item(1900)), List.of(item(2000)));
        when(repo.countRankedUsers()).thenReturn(1, 2, 3);
        var service = new LeaderboardServiceImpl(repo, 3000, now::get);
        assertThat(service.getTop(50, 0).get(0).getTotalPoints()).isEqualTo(1200);
        assertThat(service.countTotal()).isEqualTo(1);
        revision.incrementAndGet();
        assertThat(service.getTop(50, 0).get(0).getTotalPoints()).isEqualTo(1900);
        assertThat(service.countTotal()).isEqualTo(2);
        now.set(TimeUnit.MILLISECONDS.toNanos(3000));
        assertThat(service.getTop(50, 0).get(0).getTotalPoints()).isEqualTo(2000);
        assertThat(service.countTotal()).isEqualTo(3);
    }

    @Test void callerMutationCannotChangeTheCachedRows() {
        var repo = mock(UserRepository.class);
        when(repo.findTopByTotalPoints(50, 0)).thenReturn(List.of(item(1200)));
        var service = new LeaderboardServiceImpl(repo, 3000, () -> 0L);
        service.getTop(50, 0).get(0).setTotalPoints(99999);
        assertThat(service.getTop(50, 0).get(0).getTotalPoints()).isEqualTo(1200);
        service.getTop(50, 0).get(0).setUsername("changed");
        assertThat(service.getTop(50, 0).get(0).getUsername()).isEqualTo("member");
        verify(repo, times(1)).findTopByTotalPoints(50, 0);
    }

    @Test void tooLargeAvatarResultsAreNotRetainedAndPageCountIsBounded() {
        var repo = mock(UserRepository.class);
        var rows = new ArrayList<LeaderboardItem>();
        for (int n = 0; n < 4; n++) { var row = item(1); row.setAvatarUrl("A".repeat(600000)); rows.add(row); }
        when(repo.findTopByTotalPoints(50, 0)).thenReturn(rows);
        var service = new LeaderboardServiceImpl(repo, 3000, () -> 0L);
        service.getTop(50, 0); service.getTop(50, 0);
        verify(repo, times(2)).findTopByTotalPoints(50, 0);

        var smallRepo = mock(UserRepository.class);
        when(smallRepo.findTopByTotalPoints(anyInt(), anyInt())).thenReturn(List.of(item(1)));
        var bounded = new LeaderboardServiceImpl(smallRepo, 3000, () -> 0L);
        for (int offset = 0; offset < 17; offset++) bounded.getTop(1, offset);
        bounded.getTop(1, 0);
        verify(smallRepo, times(2)).findTopByTotalPoints(1, 0);
    }

    @Test void transactionReadsNeverPublishUncommittedDataToTheSharedCache() {
        var repo = mock(UserRepository.class);
        when(repo.findTopByTotalPoints(50, 0)).thenReturn(List.of(item(1)), List.of(item(2)), List.of(item(3)));
        var service = new LeaderboardServiceImpl(repo, 3000, () -> 0L);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try { service.getTop(50, 0); service.getTop(50, 0); }
        finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
        assertThat(service.getTop(50, 0).get(0).getTotalPoints()).isEqualTo(3);
        verify(repo, times(3)).findTopByTotalPoints(50, 0);
    }

    @Test void zeroTtlDisablesCachingAndFailedQueriesAreRetried() {
        var repo = mock(UserRepository.class);
        when(repo.findTopByTotalPoints(50, 0)).thenThrow(new IllegalStateException("offline")).thenReturn(List.of(item(1)));
        var service = new LeaderboardServiceImpl(repo, 0, () -> 0L);
        assertThatThrownBy(() -> service.getTop(50, 0)).isInstanceOf(IllegalStateException.class);
        service.getTop(50, 0); service.getTop(50, 0);
        verify(repo, times(3)).findTopByTotalPoints(50, 0);
    }
}
