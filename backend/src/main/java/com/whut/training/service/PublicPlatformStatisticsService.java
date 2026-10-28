package com.whut.training.service;

import com.whut.training.repository.PlatformStatisticsSnapshotRepository;
import com.whut.training.service.PlatformStatisticsProvider.Statistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/** Local-first, single-flight refresh with bounded workers and failure backoff. No network on servlet threads. */
@Service
public class PublicPlatformStatisticsService {
    private static final Logger log = LoggerFactory.getLogger(PublicPlatformStatisticsService.class);
    private final PlatformStatisticsSnapshotRepository snapshots;
    private final PublicPlatformStatisticsClient client;
    private final Executor executor;
    private final boolean enabled;
    private final Duration ttl;
    private final Instant startedAt = Instant.now();
    private final Set<Key> refreshing = ConcurrentHashMap.newKeySet();
    private final Map<Key, Instant> retryAfter = new ConcurrentHashMap<>();

    public PublicPlatformStatisticsService(PlatformStatisticsSnapshotRepository snapshots, PublicPlatformStatisticsClient client,
            @Qualifier("platformStatisticsExecutor") Executor executor,
            @Value("${app.platform-statistics.enabled:true}") boolean enabled,
            @Value("${app.platform-statistics.cache-minutes:30}") long cacheMinutes) {
        this.snapshots = snapshots; this.client = client; this.executor = executor; this.enabled = enabled;
        this.ttl = Duration.ofMinutes(Math.max(1, cacheMinutes));
    }

    public Statistics read(Long userId, String platform, String account) {
        var key = new Key(userId, platform, account);
        var snapshot = snapshots.find(userId, platform, account).orElse(null);
        var now = Instant.now();
        boolean failed = retryAfter.getOrDefault(key, Instant.MIN).isAfter(now);
        // Backfill pre-upgrade names/facts without waiting for the full TTL. A successful fetch
        // after startup, even with no available public fields, does not retrigger on every read.
        boolean nameBackfill = snapshot != null && ("luogu".equals(platform) || "nowcoder".equals(platform))
                && (snapshot.displayName() == null || snapshot.displayName().isBlank()) && snapshot.syncedAt().isBefore(startedAt);
        boolean profileBackfill = snapshot != null && snapshot.profileInfo() == null && snapshot.syncedAt().isBefore(startedAt);
        if (enabled && !failed && (snapshot == null || nameBackfill || profileBackfill || snapshot.syncedAt().plus(ttl).isBefore(now))) enqueue(key);
        if (snapshot == null) {
            if (failed) throw new IllegalStateException("Platform statistics retry deferred");
            return null;
        }
        return new Statistics(snapshot.rating(), snapshot.maxRating(), snapshot.solvedCount(), snapshot.syncedAt(),
                snapshot.partial(), refreshing.contains(key), snapshot.displayName(), snapshot.profileInfo());
    }

    private void enqueue(Key key) {
        if (!refreshing.add(key)) return;
        try {
            executor.execute(() -> {
                try {
                    var data = client.fetch(key.platform(), key.account());
                    snapshots.save(key.userId(), key.platform(), key.account(), data);
                    retryAfter.remove(key);
                } catch (RuntimeException error) {
                    retryAfter.put(key, Instant.now().plusSeconds(300));
                    log.warn("Platform statistics sync failed platform={} userId={} cause={}",
                            key.platform(), key.userId(), error.getClass().getSimpleName());
                } finally { refreshing.remove(key); }
            });
        } catch (RuntimeException rejected) {
            refreshing.remove(key);
            retryAfter.put(key, Instant.now().plusSeconds(30));
        }
    }

    @Scheduled(fixedDelayString = "${app.platform-statistics.cleanup-delay-ms:600000}")
    public void cleanup() { retryAfter.entrySet().removeIf(entry -> !entry.getValue().isAfter(Instant.now())); }

    private record Key(Long userId, String platform, String account) { }
}
