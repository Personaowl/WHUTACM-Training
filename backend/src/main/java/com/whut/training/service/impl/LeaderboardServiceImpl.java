package com.whut.training.service.impl;

import com.whut.training.domain.dto.LeaderboardItem;
import com.whut.training.repository.UserRepository;
import com.whut.training.service.LeaderboardService;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.function.LongSupplier;
import java.util.concurrent.TimeUnit;

@Service
public class LeaderboardServiceImpl implements LeaderboardService {

    private final UserRepository userRepository;
    private final long ttlNanos;
    private final LongSupplier clock;
    private final Object lock = new Object();
    private final LinkedHashMap<Page, CachedPage> pages = new LinkedHashMap<>();
    private static final long MAX_WEIGHT = 4L * 1024 * 1024;
    private static final int MAX_PAGES = 16;
    private long weight;
    private long version = Long.MIN_VALUE;
    private Integer total;
    private long totalLoadedAt;

    @Autowired
    public LeaderboardServiceImpl(UserRepository userRepository,
                                  @Value("${app.leaderboard.cache-ms:3000}") long cacheMillis) {
        this(userRepository, cacheMillis, System::nanoTime);
    }

    LeaderboardServiceImpl(UserRepository userRepository, long cacheMillis, LongSupplier clock) {
        this.userRepository = userRepository;
        this.ttlNanos = TimeUnit.MILLISECONDS.toNanos(Math.max(0, cacheMillis));
        this.clock = clock;
    }

    @Override
    public List<LeaderboardItem> getTop(int limit, int offset) {
        if (limit <= 0) limit = 10;
        // A caller inside a transaction may see uncommitted data: never share that result.
        if (ttlNanos == 0 || TransactionSynchronizationManager.isActualTransactionActive())
            return userRepository.findTopByTotalPoints(limit, offset);
        synchronized (lock) {
            invalidateChangedVersion();
            var key = new Page(limit, offset);
            var cached = pages.get(key);
            if (cached != null && clock.getAsLong() - cached.loadedAt() < ttlNanos)
                return copy(cached.items());
            var items = userRepository.findTopByTotalPoints(limit, offset);
            long bytes = items.stream().mapToLong(item -> 128L + textBytes(item.getAvatarUrl())
                    + textBytes(item.getUsername()) + textBytes(item.getDisplayName())).sum();
            var removed = pages.remove(key);
            if (removed != null) weight -= removed.weight();
            if (bytes <= MAX_WEIGHT) {
                while (!pages.isEmpty() && (pages.size() >= MAX_PAGES || weight + bytes > MAX_WEIGHT)) {
                    var oldest = pages.keySet().iterator().next();
                    weight -= pages.remove(oldest).weight();
                }
                pages.put(key, new CachedPage(copy(items), clock.getAsLong(), bytes));
                weight += bytes;
            }
            return items;
        }
    }

    @Override
    public int countTotal() {
        if (ttlNanos == 0 || TransactionSynchronizationManager.isActualTransactionActive())
            return userRepository.countRankedUsers();
        synchronized (lock) {
            invalidateChangedVersion();
            if (total == null || clock.getAsLong() - totalLoadedAt >= ttlNanos) {
                total = userRepository.countRankedUsers();
                totalLoadedAt = clock.getAsLong();
            }
            return total;
        }
    }

    private void invalidateChangedVersion() {
        long current = userRepository.rankingVersion();
        if (version == current) return;
        pages.clear(); weight = 0; total = null; version = current;
    }

    private static long textBytes(String value) { return value == null ? 0 : 2L * value.length(); }

    private static List<LeaderboardItem> copy(List<LeaderboardItem> items) {
        return items.stream().map(item -> {
            var result = new LeaderboardItem();
            result.setUserId(item.getUserId()); result.setUsername(item.getUsername());
            result.setDisplayName(item.getDisplayName()); result.setAvatarUrl(item.getAvatarUrl());
            result.setTotalPoints(item.getTotalPoints()); result.setLastCheckinAt(item.getLastCheckinAt());
            return result;
        }).toList();
    }

    private record Page(int limit, int offset) { }
    private record CachedPage(List<LeaderboardItem> items, long loadedAt, long weight) { }
}
