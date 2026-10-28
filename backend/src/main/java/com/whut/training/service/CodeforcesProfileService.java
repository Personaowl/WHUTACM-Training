package com.whut.training.service;

import com.whut.training.domain.dto.CodeforcesOverview;
import com.whut.training.domain.entity.User;
import com.whut.training.exception.BusinessException;
import com.whut.training.repository.CodeforcesProfileSnapshotRepository;
import com.whut.training.repository.CodeforcesRatingHistoryRepository;
import com.whut.training.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

/**
 * 汇总并缓存 Codeforces 用户公开统计。
 *
 * <p>内存缓存服务当前进程的热点请求，MySQL 快照保证重启后仍能快速返回。
 * 过期快照采用 stale-while-revalidate，先响应旧数据，再在后台刷新。
 */
@Service
public class CodeforcesProfileService {

    private static final Logger log = LoggerFactory.getLogger(CodeforcesProfileService.class);
    private static final int SUBMISSION_LIMIT = 10000;
    private static final int RECENT_CONTEST_LIMIT = 8;

    private final UserRepository userRepository;
    private final CodeforcesApiService codeforcesApiService;
    private final CodeforcesProfileSnapshotRepository snapshotRepository;
    private final CodeforcesRatingHistoryRepository ratingHistoryRepository;
    private final Duration cacheDuration;
    private final Executor backgroundExecutor;
    private final Map<Long, CacheEntry> memoryCache = new ConcurrentHashMap<>();
    private final Map<Long, Object> refreshLocks = new ConcurrentHashMap<>();
    private final Set<Long> refreshingUsers = ConcurrentHashMap.newKeySet();
    private final Map<Long, Long> backgroundRetryAfter = new ConcurrentHashMap<>();

    @Autowired
    public CodeforcesProfileService(
            UserRepository userRepository,
            CodeforcesApiService codeforcesApiService,
            CodeforcesProfileSnapshotRepository snapshotRepository,
            CodeforcesRatingHistoryRepository ratingHistoryRepository,
            @Value("${codeforces.profile-cache-minutes:30}") long cacheMinutes,
            @Qualifier("codeforcesBackgroundExecutor") Executor backgroundExecutor
    ) {
        this.userRepository = userRepository;
        this.codeforcesApiService = codeforcesApiService;
        this.snapshotRepository = snapshotRepository;
        this.ratingHistoryRepository = ratingHistoryRepository;
        this.cacheDuration = Duration.ofMinutes(Math.max(1, cacheMinutes));
        this.backgroundExecutor = backgroundExecutor;
    }

    CodeforcesProfileService(
            UserRepository userRepository,
            CodeforcesApiService codeforcesApiService,
            CodeforcesProfileSnapshotRepository snapshotRepository,
            CodeforcesRatingHistoryRepository ratingHistoryRepository,
            long cacheMinutes
    ) {
        this(userRepository, codeforcesApiService, snapshotRepository, ratingHistoryRepository,
                cacheMinutes, ForkJoinPool.commonPool());
    }

    public CodeforcesOverview getOverview(Long userId) {
        User user = requireUser(userId);
        String handle = normalizeHandle(user.getCodeforcesHandle());
        if (handle == null) {
            return emptyOverview();
        }

        CacheEntry cached = memoryCache.get(userId);
        if (cached != null && handle.equalsIgnoreCase(cached.handle()) && isFresh(cached.syncedAt())) {
            if (cached.overview().ratingStats() == null || cached.overview().activity() == null) {
                refreshInBackground(userId);
                return cached.overview().withStale(true);
            }
            return cached.overview().withStale(false);
        }

        Optional<CodeforcesProfileSnapshotRepository.Snapshot> stored = snapshotRepository.find(userId, handle);
        if (stored.isPresent()) {
            CodeforcesProfileSnapshotRepository.Snapshot snapshot = stored.get();
            memoryCache.put(userId, new CacheEntry(handle, snapshot.overview(), snapshot.syncedAt()));
            if (isFresh(snapshot.syncedAt()) && snapshot.overview().ratingStats() != null && snapshot.overview().activity() != null) {
                return snapshot.overview().withStale(false);
            }
            refreshInBackground(userId);
            return snapshot.overview().withStale(true);
        }

        CodeforcesOverview initial = initialOverview(user, handle);
        memoryCache.put(userId, new CacheEntry(handle, initial, null));
        refreshInBackground(userId);
        return initial;
    }

    public CodeforcesOverview refresh(Long userId) {
        synchronized (refreshLocks.computeIfAbsent(userId, ignored -> new Object())) {
            User user = requireUser(userId);
            String handle = normalizeHandle(user.getCodeforcesHandle());
            if (handle == null) {
                return emptyOverview();
            }
            return refreshNow(user, handle);
        }
    }

    /**
     * 为旧账号按需补齐完整 Rating 比赛历史。
     *
     * <p>导出只在本地历史为空时调用一次 user.rating，避免为补历史重新下载大量提交记录。
     */
    public void ensureRatingHistory(Long userId) {
        if (ratingHistoryRepository.existsByUserId(userId)) {
            return;
        }
        synchronized (refreshLocks.computeIfAbsent(userId, ignored -> new Object())) {
            if (ratingHistoryRepository.existsByUserId(userId)) {
                return;
            }
            User user = requireUser(userId);
            String handle = normalizeHandle(user.getCodeforcesHandle());
            if (handle == null) {
                return;
            }
            Optional<List<CodeforcesApiService.CodeforcesRatingChange>> result =
                    codeforcesApiService.getUserRatingHistory(handle);
            if (result.isEmpty()) {
                throw new BusinessException(
                        503,
                        "Codeforces Rating history sync failed for " + handle + "; please try again later"
                );
            }
            ratingHistoryRepository.replaceForUser(userId, result.get());
        }
    }

    public void refreshInBackground(Long userId) {
        if (System.nanoTime() < backgroundRetryAfter.getOrDefault(userId, Long.MIN_VALUE)) return;
        if (!refreshingUsers.add(userId)) {
            return;
        }
        try {
            CompletableFuture.runAsync(() -> {
                try {
                    refresh(userId);
                    backgroundRetryAfter.remove(userId);
                } catch (RuntimeException ex) {
                    backgroundRetryAfter.put(userId, System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(60));
                    log.warn("Background Codeforces profile refresh failed for userId={}: {}", userId, ex.getMessage());
                } finally {
                    refreshingUsers.remove(userId);
                }
            }, backgroundExecutor);
        } catch (RuntimeException rejected) {
            backgroundRetryAfter.put(userId, System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(60));
            refreshingUsers.remove(userId);
            log.warn("Background Codeforces profile refresh queue is full for userId={}", userId);
        }
    }

    private CodeforcesOverview refreshNow(User user, String handle) {
        Optional<List<CodeforcesApiService.CodeforcesSubmission>> submissionsResult =
                codeforcesApiService.getUserSubmissions(handle, SUBMISSION_LIMIT);
        Optional<List<CodeforcesApiService.CodeforcesRatingChange>> ratingResult =
                codeforcesApiService.getUserRatingHistory(handle);
        Optional<CodeforcesApiService.CodeforcesUserProfile> profileResult =
                codeforcesApiService.getUserInfo(handle);

        if (submissionsResult.isEmpty() || ratingResult.isEmpty()) {
            throw new BusinessException(503, "Codeforces API is unavailable; please try again later");
        }

        List<CodeforcesApiService.CodeforcesSubmission> submissions = submissionsResult.get();
        List<CodeforcesApiService.CodeforcesRatingChange> ratingChanges = ratingResult.get();
        Map<String, CodeforcesApiService.CodeforcesSubmission> solvedProblems = new LinkedHashMap<>();
        Set<String> attemptedProblems = new HashSet<>();
        int acceptedSubmissionCount = 0;
        Long lastSubmissionAt = null;
        Map<String, Integer> activityCounts = new java.util.TreeMap<>();
        var today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"));

        for (CodeforcesApiService.CodeforcesSubmission submission : submissions) {
            if (submission.creationTimeSeconds() != null
                    && (lastSubmissionAt == null || submission.creationTimeSeconds() > lastSubmissionAt)) {
                lastSubmissionAt = submission.creationTimeSeconds();
            }
            String problemKey = submission.problemKey();
            if (problemKey != null) {
                attemptedProblems.add(problemKey);
            }
            if ("OK".equalsIgnoreCase(submission.verdict())) {
                acceptedSubmissionCount++;
                if (submission.creationTimeSeconds() != null && submission.creationTimeSeconds() > 0) {
                    var day = Instant.ofEpochSecond(submission.creationTimeSeconds()).atZone(java.time.ZoneId.of("Asia/Shanghai")).toLocalDate();
                    if (!day.isBefore(today.minusDays(364)) && !day.isAfter(today)) activityCounts.merge(day.toString(), 1, Integer::sum);
                }
                if (problemKey != null) {
                    solvedProblems.putIfAbsent(problemKey, submission);
                }
            }
        }

        Map<String, Integer> tagCounts = new HashMap<>();
        Map<Integer, Integer> ratingCounts = new java.util.TreeMap<>();
        int unratedCount = 0;
        for (CodeforcesApiService.CodeforcesSubmission submission : solvedProblems.values()) {
            if (submission.rating() == null || submission.rating() <= 0) {
                unratedCount++;
            } else {
                ratingCounts.merge(submission.rating(), 1, Integer::sum);
            }
            for (String tag : submission.tags()) {
                tagCounts.merge(tag, 1, Integer::sum);
            }
        }
        List<CodeforcesOverview.TagStat> tagStats = tagCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .map(entry -> new CodeforcesOverview.TagStat(entry.getKey(), entry.getValue()))
                .toList();
        List<CodeforcesOverview.RatingStat> ratingStats = new ArrayList<>();
        ratingCounts.forEach((rating, count) -> ratingStats.add(new CodeforcesOverview.RatingStat(rating, count)));
        if (unratedCount > 0) ratingStats.add(new CodeforcesOverview.RatingStat(null, unratedCount));

        List<CodeforcesApiService.CodeforcesRatingChange> sortedChanges = new ArrayList<>(ratingChanges);
        sortedChanges.sort(Comparator.comparing(
                CodeforcesApiService.CodeforcesRatingChange::ratingUpdateTimeSeconds,
                Comparator.nullsLast(Comparator.reverseOrder())
        ));
        List<CodeforcesOverview.RecentContest> recentContests = sortedChanges.stream()
                .limit(RECENT_CONTEST_LIMIT)
                .map(this::toRecentContest)
                .toList();

        Integer currentRating = profileResult.map(CodeforcesApiService.CodeforcesUserProfile::rating)
                .orElseGet(() -> newestRating(sortedChanges, user.getCodeforcesRating()));
        Integer maxRating = profileResult.map(CodeforcesApiService.CodeforcesUserProfile::maxRating)
                .orElseGet(() -> maxRating(ratingChanges, user.getMaxRating()));
        Instant syncedAt = Instant.now();
        CodeforcesOverview overview = new CodeforcesOverview(
                handle,
                currentRating,
                maxRating,
                solvedProblems.size(),
                attemptedProblems.size(),
                acceptedSubmissionCount,
                ratingChanges.size(),
                lastSubmissionAt,
                submissions.size() >= SUBMISSION_LIMIT,
                syncedAt,
                false,
                tagStats,
                recentContests,
                List.copyOf(ratingStats),
                activityCounts.entrySet().stream().map(entry -> new com.whut.training.domain.dto.PlatformDetails.ActivityDay(entry.getKey(), entry.getValue())).toList()
        );

        if (!java.util.Objects.equals(user.getCodeforcesRating(), currentRating)
                || !java.util.Objects.equals(user.getMaxRating(), maxRating)) {
            userRepository.updateCodeforcesRatings(user.getId(), handle, currentRating, maxRating);
        }
        snapshotRepository.save(user.getId(), handle, overview);
        ratingHistoryRepository.replaceForUser(user.getId(), ratingChanges);
        memoryCache.put(user.getId(), new CacheEntry(handle, overview, syncedAt));
        return overview;
    }

    private CodeforcesOverview.RecentContest toRecentContest(
            CodeforcesApiService.CodeforcesRatingChange change
    ) {
        Integer delta = change.oldRating() == null || change.newRating() == null
                ? null
                : change.newRating() - change.oldRating();
        String url = change.contestId() == null
                ? null
                : "https://codeforces.com/contest/" + change.contestId();
        return new CodeforcesOverview.RecentContest(
                change.contestId(),
                change.contestName(),
                change.rank(),
                change.oldRating(),
                change.newRating(),
                delta,
                change.ratingUpdateTimeSeconds(),
                url
        );
    }

    private Integer newestRating(
            List<CodeforcesApiService.CodeforcesRatingChange> sortedChanges,
            Integer fallback
    ) {
        return sortedChanges.stream()
                .map(CodeforcesApiService.CodeforcesRatingChange::newRating)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(fallback);
    }

    private Integer maxRating(
            List<CodeforcesApiService.CodeforcesRatingChange> changes,
            Integer fallback
    ) {
        return changes.stream()
                .map(CodeforcesApiService.CodeforcesRatingChange::newRating)
                .filter(java.util.Objects::nonNull)
                .max(Integer::compareTo)
                .map(value -> fallback == null ? value : Math.max(value, fallback))
                .orElse(fallback);
    }

    private boolean isFresh(Instant syncedAt) {
        return syncedAt != null && syncedAt.plus(cacheDuration).isAfter(Instant.now());
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(404, "user not found: " + userId));
    }

    private String normalizeHandle(String handle) {
        if (handle == null || handle.isBlank()) {
            return null;
        }
        return handle.trim();
    }

    private CodeforcesOverview emptyOverview() {
        return new CodeforcesOverview(
                null, null, null, 0, 0, 0, 0, null,
                false, null, false, List.of(), List.of()
        );
    }

    /**
     * 首次没有统计快照时立即返回站内已有的基础资料，完整统计交给后台刷新。
     */
    private CodeforcesOverview initialOverview(User user, String handle) {
        return new CodeforcesOverview(
                handle,
                user.getCodeforcesRating(),
                user.getMaxRating(),
                0,
                0,
                0,
                0,
                null,
                false,
                null,
                true,
                List.of(),
                List.of()
        );
    }

    private record CacheEntry(String handle, CodeforcesOverview overview, Instant syncedAt) {
    }
}
