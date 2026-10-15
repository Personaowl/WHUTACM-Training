package com.whut.training.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodeforcesRequestCoordinatorTest {

    @Test
    void timedOutQueuedWorkIsRemovedAndReleasesCapacity() throws Exception {
        CodeforcesRequestCoordinator coordinator = new CodeforcesRequestCoordinator(0, 1, 500);
        var executed = new java.util.concurrent.atomic.AtomicBoolean();
        try {
            // No worker yet: the request must expire in the queue, without executing its action.
            assertThatThrownBy(() -> coordinator.execute(CodeforcesRequestCoordinator.Priority.NORMAL, () -> {
                executed.set(true);
                return "expired";
            })).isInstanceOf(IOException.class).hasMessageContaining("timed out");
            assertThat(coordinator.pendingRequests()).isZero();
            coordinator.start();
            assertThat(coordinator.execute(CodeforcesRequestCoordinator.Priority.CHECK_IN, () -> "next"))
                    .isEqualTo("next");
            assertThat(executed).isFalse();
        } finally {
            coordinator.shutdown();
        }
    }

    @Test
    void rejectsNewWorkWhenTheBoundedQueueIsFull() throws Exception {
        CodeforcesRequestCoordinator coordinator = new CodeforcesRequestCoordinator(0, 1);
        coordinator.start();
        var callers = Executors.newSingleThreadExecutor();
        var firstStarted = new CountDownLatch(1);
        var releaseFirst = new CountDownLatch(1);
        try {
            var first = callers.submit(() -> coordinator.execute(
                    CodeforcesRequestCoordinator.Priority.NORMAL,
                    () -> {
                        firstStarted.countDown();
                        releaseFirst.await(2, TimeUnit.SECONDS);
                        return "done";
                    }
            ));
            assertTrue(firstStarted.await(1, TimeUnit.SECONDS));

            assertThatThrownBy(() -> coordinator.execute(
                    CodeforcesRequestCoordinator.Priority.CHECK_IN,
                    () -> "rejected"
            )).isInstanceOf(IOException.class)
                    .hasMessageContaining("queue is full");

            releaseFirst.countDown();
            assertThat(first.get(2, TimeUnit.SECONDS)).isEqualTo("done");
        } finally {
            releaseFirst.countDown();
            callers.shutdownNow();
            coordinator.shutdown();
        }
    }

    @Test
    void checkInOvertakesQueuedBackgroundRequest() throws Exception {
        CodeforcesRequestCoordinator coordinator = new CodeforcesRequestCoordinator(0);
        coordinator.start();
        var callers = Executors.newFixedThreadPool(3);
        var firstStarted = new CountDownLatch(1);
        var releaseFirst = new CountDownLatch(1);
        List<String> order = new CopyOnWriteArrayList<>();
        try {
            var first = callers.submit(() -> coordinator.execute(
                    CodeforcesRequestCoordinator.Priority.NORMAL,
                    () -> {
                        order.add("first");
                        firstStarted.countDown();
                        releaseFirst.await(2, TimeUnit.SECONDS);
                        return "first";
                    }
            ));
            assertTrue(firstStarted.await(1, TimeUnit.SECONDS));

            var background = callers.submit(() -> coordinator.execute(
                    CodeforcesRequestCoordinator.Priority.BACKGROUND,
                    () -> { order.add("background"); return "background"; }
            ));
            while (coordinator.pendingRequests() < 1) Thread.onSpinWait();
            var checkIn = callers.submit(() -> coordinator.execute(
                    CodeforcesRequestCoordinator.Priority.CHECK_IN,
                    () -> { order.add("check-in"); return "check-in"; }
            ));
            while (coordinator.pendingRequests() < 2) Thread.onSpinWait();

            releaseFirst.countDown();
            assertThat(first.get(2, TimeUnit.SECONDS)).isEqualTo("first");
            assertThat(checkIn.get(2, TimeUnit.SECONDS)).isEqualTo("check-in");
            assertThat(background.get(2, TimeUnit.SECONDS)).isEqualTo("background");
            assertThat(order).containsExactly("first", "check-in", "background");
        } finally {
            releaseFirst.countDown();
            callers.shutdownNow();
            coordinator.shutdown();
        }
    }
}
