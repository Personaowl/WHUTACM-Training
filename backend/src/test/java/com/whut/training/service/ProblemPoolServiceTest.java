package com.whut.training.service;

import com.whut.training.domain.entity.CfProblem;
import com.whut.training.exception.BusinessException;
import com.whut.training.repository.ProblemCatalogRepository;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProblemPoolServiceTest {
    private final ProblemCatalogRepository catalog = mock(ProblemCatalogRepository.class);
    private final CodeforcesApiService api = mock(CodeforcesApiService.class);
    private final CfProblem problem = new CfProblem("123-A", 123, "A", "Example", 1200,
            "math", false, null, 10, "https://codeforces.com/problemset/problem/123/A");

    @Test void disabledStartupDoesNotQueryOrFetch() {
        new ProblemPoolService(catalog, api, false).initialSyncOnStartup();
        verifyNoInteractions(catalog, api);
    }

    @Test void populatedCatalogDoesNotStartExternalWork() {
        when(catalog.countProblems()).thenReturn(10L);
        var service = new ProblemPoolService(catalog, api, true);
        service.initialSyncOnStartup();
        service.ensureAvailable();
        verifyNoInteractions(api);
    }

    @Test void fortyEmptyPoolRequestsAndSchedulerShareOneNonBlockingSyncGate() throws Exception {
        var service = new ProblemPoolService(catalog, api, true);
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var finished = new CountDownLatch(1);
        when(catalog.countProblems()).thenReturn(0L);
        when(api.fetchProblemSet()).thenAnswer(call -> {
            started.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("test did not release fetch");
            return List.of(problem);
        });
        when(catalog.upsertProblems(List.of(problem))).thenAnswer(call -> { finished.countDown(); return 1; });
        var readers = Executors.newFixedThreadPool(8);
        try {
            assertTimeout(Duration.ofSeconds(1), () -> assertEquals(503,
                    assertThrows(BusinessException.class, service::ensureAvailable).getCode()));
            assertTrue(started.await(1, TimeUnit.SECONDS));
            var futures = new java.util.ArrayList<java.util.concurrent.Future<Integer>>();
            for (int i = 0; i < 40; i++) futures.add(readers.submit(() ->
                    assertThrows(BusinessException.class, service::ensureAvailable).getCode()));
            for (var future : futures) assertEquals(503, future.get(1, TimeUnit.SECONDS));
            service.syncProblemPoolByScheduler();
            service.initialSyncOnStartup();
            verify(api, times(1)).fetchProblemSet();
            verify(catalog, never()).upsertProblems(anyList());
            release.countDown();
            assertTrue(finished.await(1, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            readers.shutdownNow();
        }
    }

    @Test void failedScheduledSyncReleasesGateForTheNextAttempt() {
        var service = new ProblemPoolService(catalog, api, true);
        when(api.fetchProblemSet()).thenThrow(new IllegalStateException("upstream unavailable")).thenReturn(List.of(problem));
        when(catalog.upsertProblems(List.of(problem))).thenReturn(1);
        assertThrows(IllegalStateException.class, service::syncProblemPoolByScheduler);
        service.syncProblemPoolByScheduler();
        verify(api, times(2)).fetchProblemSet();
        verify(catalog).upsertProblems(List.of(problem));
    }

    @Test void emptyUpstreamDoesNotReplaceCatalogAndAllowsRetry() {
        var service = new ProblemPoolService(catalog, api, true);
        when(api.fetchProblemSet()).thenReturn(List.of(), List.of(problem));
        service.syncProblemPoolByScheduler();
        verify(catalog, never()).upsertProblems(anyList());
        service.syncProblemPoolByScheduler();
        verify(catalog).upsertProblems(List.of(problem));
    }
}
