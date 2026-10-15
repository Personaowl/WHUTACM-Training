package com.whut.training.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * One process-wide priority queue for the official Codeforces API limit.
 * Check-ins can overtake profile refreshes without violating the two-second start interval.
 */
@Service
public class CodeforcesRequestCoordinator {
    public enum Priority {
        CHECK_IN(0), NORMAL(10), BACKGROUND(20);

        private final int weight;

        Priority(int weight) {
            this.weight = weight;
        }
    }

    private final long requestIntervalNanos;
    private final PriorityBlockingQueue<QueuedRequest<?>> queue = new PriorityBlockingQueue<>();
    private final AtomicLong sequence = new AtomicLong();
    private final Semaphore queueSlots;
    private final long requestTimeoutMillis;
    private final Object lifecycleLock = new Object();
    private volatile boolean running = true;
    private volatile long nextRequestAllowedAtNanos;
    private Thread worker;

    CodeforcesRequestCoordinator(long requestIntervalMillis) {
        this(requestIntervalMillis, 200);
    }

    CodeforcesRequestCoordinator(long requestIntervalMillis, int maxPendingRequests) {
        this(requestIntervalMillis, maxPendingRequests, 30_000);
    }

    @Autowired
    public CodeforcesRequestCoordinator(
            @Value("${codeforces.request-interval-ms:2100}") long requestIntervalMillis,
            @Value("${codeforces.max-pending-requests:200}") int maxPendingRequests,
            @Value("${codeforces.request-timeout-ms:30000}") long requestTimeoutMillis
    ) {
        this.requestIntervalNanos = TimeUnit.MILLISECONDS.toNanos(Math.max(0, requestIntervalMillis));
        this.queueSlots = new Semaphore(Math.max(1, maxPendingRequests));
        this.requestTimeoutMillis = Math.max(1, requestTimeoutMillis);
    }

    @PostConstruct
    void start() {
        worker = new Thread(this::runLoop, "codeforces-api-queue");
        worker.setDaemon(true);
        worker.start();
    }

    public <T> T execute(Priority priority, Callable<T> action) throws IOException, InterruptedException {
        CompletableFuture<T> result = new CompletableFuture<>();
        QueuedRequest<T> request = new QueuedRequest<>(priority, sequence.getAndIncrement(), action, result);
        synchronized (lifecycleLock) {
            if (!running) throw new IOException("Codeforces request coordinator is shutting down");
            if (!queueSlots.tryAcquire()) {
                throw new IOException("Codeforces request queue is full; please try again later");
            }
            try {
                queue.add(request);
            } catch (RuntimeException ex) {
                queueSlots.release();
                throw ex;
            }
        }
        try {
            return result.get(requestTimeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            cancel(request);
            throw new IOException("Codeforces request timed out while queued or executing", ex);
        } catch (InterruptedException ex) {
            cancel(request);
            Thread.currentThread().interrupt();
            throw ex;
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof IOException io) throw io;
            if (cause instanceof InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw interrupted;
            }
            if (cause instanceof RuntimeException runtime) throw runtime;
            throw new IOException("Codeforces request failed", cause);
        }
    }

    public int pendingRequests() {
        return queue.size();
    }

    private void cancel(QueuedRequest<?> request) {
        request.result.cancel(false);
        if (queue.remove(request)) queueSlots.release();
    }

    private void runLoop() {
        while (running) {
            QueuedRequest<?> request = null;
            try {
                request = queue.take();
                if (request.result.isCancelled()) continue;
                long waitNanos = nextRequestAllowedAtNanos - System.nanoTime();
                if (waitNanos > 0) TimeUnit.NANOSECONDS.sleep(waitNanos);
                if (request.result.isCancelled()) continue;
                nextRequestAllowedAtNanos = System.nanoTime() + requestIntervalNanos;
                request.run();
            } catch (InterruptedException ex) {
                if (request != null) {
                    request.fail(new IOException("Codeforces request interrupted before execution", ex));
                }
                break;
            } finally {
                if (request != null) queueSlots.release();
            }
        }
    }

    @PreDestroy
    void shutdown() {
        synchronized (lifecycleLock) {
            running = false;
            if (worker != null) worker.interrupt();
            List<QueuedRequest<?>> abandoned = new ArrayList<>();
            queue.drainTo(abandoned);
            abandoned.forEach(request -> {
                request.fail(new IOException("Codeforces request coordinator stopped before processing the request"));
                queueSlots.release();
            });
        }
    }

    private static final class QueuedRequest<T> implements Comparable<QueuedRequest<?>> {
        private final Priority priority;
        private final long sequence;
        private final Callable<T> action;
        private final CompletableFuture<T> result;

        private QueuedRequest(Priority priority, long sequence, Callable<T> action, CompletableFuture<T> result) {
            this.priority = priority;
            this.sequence = sequence;
            this.action = action;
            this.result = result;
        }

        private void run() {
            try {
                result.complete(action.call());
            } catch (Throwable error) {
                result.completeExceptionally(error);
            }
        }

        private void fail(Throwable error) {
            result.completeExceptionally(error);
        }

        @Override
        public int compareTo(QueuedRequest<?> other) {
            int byPriority = Integer.compare(priority.weight, other.priority.weight);
            return byPriority != 0 ? byPriority : Long.compare(sequence, other.sequence);
        }
    }
}
