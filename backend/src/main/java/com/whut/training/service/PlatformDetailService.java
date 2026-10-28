package com.whut.training.service;

import com.whut.training.domain.dto.PlatformDetails;
import com.whut.training.domain.entity.User;
import com.whut.training.exception.BusinessException;
import com.whut.training.repository.PlatformBindingRepository;
import com.whut.training.repository.PlatformDetailSnapshotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/** Lazy detail snapshots: HTTP requests only read local data and schedule bounded background work. */
@Service
public class PlatformDetailService {
    private static final Logger log = LoggerFactory.getLogger(PlatformDetailService.class);
    private static final List<String> PLATFORMS = List.of("codeforces", "atcoder", "luogu", "nowcoder");
    private final PlatformBindingRepository bindings;
    private final PlatformDetailSnapshotRepository snapshots;
    private final PublicPlatformDetailClient client;
    private final CodeforcesProfileService codeforces;
    private final Executor executor;
    private final boolean enabled;
    private final Duration ttl;
    private final Set<Key> refreshing = ConcurrentHashMap.newKeySet();
    private final Map<Key, Instant> retryAfter = new ConcurrentHashMap<>();

    public PlatformDetailService(PlatformBindingRepository bindings, PlatformDetailSnapshotRepository snapshots,
            PublicPlatformDetailClient client, CodeforcesProfileService codeforces,
            @Qualifier("platformDetailExecutor") Executor executor,
            @Value("${app.platform-statistics.enabled:true}") boolean enabled,
            @Value("${app.platform-details.cache-minutes:360}") long cacheMinutes) {
        this.bindings = bindings; this.snapshots = snapshots; this.client = client; this.codeforces = codeforces;
        this.executor = executor; this.enabled = enabled; this.ttl = Duration.ofMinutes(Math.max(1, cacheMinutes));
    }

    public List<PlatformDetails> all(User user, boolean force) {
        return PLATFORMS.stream().map(platform -> read(user, platform, force)).toList();
    }
    public PlatformDetails read(User user, String platform, boolean force) {
        if (user == null || user.getId() == null) throw new BusinessException(401, "请先登录");
        if (!PLATFORMS.contains(platform)) throw new BusinessException(400, "不支持的平台");
        String account = switch (platform) {
            case "codeforces" -> user.getCodeforcesHandle();
            case "atcoder" -> user.getAtcoderHandle();
            default -> bindings.find(user.getId(), platform, false).map(PlatformBindingRepository.Binding::accountId).orElse(null);
        };
        if (account == null || account.isBlank()) return empty(platform, null, "UNBOUND");
        if ("codeforces".equals(platform)) {
            var data = codeforces.getOverview(user.getId());
            boolean requested = enabled && force && (data.syncedAt() == null || data.syncedAt().plusSeconds(60).isBefore(Instant.now()));
            if (requested) codeforces.refreshInBackground(user.getId());
            var contests = data.recentContests() == null ? null : data.recentContests().stream().map(row -> new PlatformDetails.ContestStat(
                    String.valueOf(row.contestId()), row.contestName(), row.rank(), row.oldRating(), row.newRating(), row.ratingChange(), row.ratingUpdateTimeSeconds(), row.url())).toList();
            return new PlatformDetails(platform, account, data.syncedAt(), data.stale() || requested ? "SYNCING" : "READY",
                    data.submissionLimitReached(), "Codeforces 最近最多 10,000 条提交；按北京时间汇总 AC 提交次数，重复 AC 也计入次数，不代表全历史首次通过。",
                    data.tagStats(), null, contests, data.activity(), "AC_SUBMISSIONS");
        }
        var key = new Key(user.getId(), platform, account);
        var stored = snapshots.find(user.getId(), platform, account).orElse(null);
        if (stored != null && (stored.syncedAt() == null || !platform.equals(stored.platform()) || !account.equals(stored.accountId()))) stored = null;
        var now = Instant.now();
        boolean backoff = retryAfter.getOrDefault(key, Instant.MIN).isAfter(now);
        if (enabled && !backoff && (stored == null || stored.syncedAt().plus(force ? Duration.ofMinutes(1) : ttl).isBefore(now))) enqueue(key);
        if (stored == null) return empty(platform, account, backoff ? "UNAVAILABLE" : enabled ? "PENDING" : "DISABLED");
        return stored.withStatus(refreshing.contains(key) ? "SYNCING" : backoff ? "STALE" : "READY");
    }
    private void enqueue(Key key) {
        if (!refreshing.add(key)) return;
        try { executor.execute(() -> {
            try {
                snapshots.save(key.userId(), client.fetch(key.platform(), key.account()));
                retryAfter.remove(key);
            } catch (RuntimeException failure) {
                retryAfter.put(key, Instant.now().plusSeconds(300));
                log.warn("Public detail sync failed platform={} userId={} cause={}", key.platform(), key.userId(), failure.getClass().getSimpleName());
            } finally { refreshing.remove(key); }
        }); } catch (RuntimeException rejected) { refreshing.remove(key); retryAfter.put(key, Instant.now().plusSeconds(30)); }
    }
    private PlatformDetails empty(String platform, String account, String status) {
        return new PlatformDetails(platform, account, null, status, true, null, null, null, null, null,
                "luogu".equals(platform) ? "PUBLIC_ACTIVITY" : "AC_SUBMISSIONS");
    }
    @Scheduled(fixedDelayString = "${app.platform-statistics.cleanup-delay-ms:600000}")
    public void cleanup() { retryAfter.entrySet().removeIf(entry -> !entry.getValue().isAfter(Instant.now())); }
    private record Key(long userId, String platform, String account) { }
}
