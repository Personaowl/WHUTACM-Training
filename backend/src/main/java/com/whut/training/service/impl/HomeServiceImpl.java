package com.whut.training.service.impl;

import com.whut.training.common.TimeProvider;
import com.whut.training.domain.dto.HomeOverview;
import com.whut.training.domain.dto.LeaderboardItem;
import com.whut.training.domain.entity.PushPoolItem;
import com.whut.training.repository.DailyProblemRepository;
import com.whut.training.service.DailyProblemService;
import com.whut.training.service.HomeService;
import com.whut.training.service.LeaderboardService;
import com.whut.training.service.PushPoolService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class HomeServiceImpl implements HomeService {

    private final LeaderboardService leaderboardService;
    private final DailyProblemRepository dailyProblemRepository;
    private final DailyProblemService dailyProblemService;
    private final PushPoolService pushPoolService;
    private final TimeProvider timeProvider;
    private final long cacheDurationNanos;
    private final Object cacheLock = new Object();
    private volatile OverviewCache cache = OverviewCache.empty();

    public HomeServiceImpl(LeaderboardService leaderboardService,
                           DailyProblemRepository dailyProblemRepository,
                           DailyProblemService dailyProblemService,
                           PushPoolService pushPoolService,
                           TimeProvider timeProvider,
                           @Value("${app.home.cache-ms:3000}") long cacheDurationMillis) {
        this.leaderboardService = leaderboardService;
        this.dailyProblemRepository = dailyProblemRepository;
        this.dailyProblemService = dailyProblemService;
        this.pushPoolService = pushPoolService;
        this.timeProvider = timeProvider;
        this.cacheDurationNanos = java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(
                Math.max(0, cacheDurationMillis)
        );
    }

    @Override
    public HomeOverview getOverview(int topLimit) {
        int normalizedLimit = Math.max(1, Math.min(50, topLimit));
        long now = System.nanoTime();
        OverviewCache current = cache;
        if (current.matches(normalizedLimit, now)) return current.value();

        synchronized (cacheLock) {
            current = cache;
            if (current.matches(normalizedLimit, now)) return current.value();
            HomeOverview overview = loadOverview(normalizedLimit);
            cache = new OverviewCache(normalizedLimit, overview, now + cacheDurationNanos);
            return overview;
        }
    }

    private HomeOverview loadOverview(int topLimit) {
        HomeOverview o = new HomeOverview();
        o.setTotalUsers(dailyProblemRepository.countActiveUsers(7));

        List<LeaderboardItem> top = leaderboardService.getTop(topLimit, 0);
        o.setTopUsers(top);

        LocalDate today = timeProvider.today();
        boolean problemPoolReady = dailyProblemService.ensureTodaySlots();
        o.setProblemPoolInitializing(!problemPoolReady);
        var slots = dailyProblemRepository.findCurrentDailySlotsByDate(today);
        if (slots != null && !slots.isEmpty()) {
            List<com.whut.training.domain.dto.ProblemView> views = slots.stream()
                    .map(s -> new com.whut.training.domain.dto.ProblemView(
                            s.slot() != null ? s.slot().toUpperCase() : "DAILY",
                            s.date().toString(),
                            s.problemKey(),
                            s.contestId(),
                            s.problemIndex(),
                            s.name(),
                            s.rating(),
                            s.tags(),
                            s.sourceUrl()
                    )).toList();
            o.setTodayProblem(views.isEmpty() ? null : views);
        } else {
            o.setTodayProblem(null);
        }

        Optional<PushPoolItem> todayPush = pushPoolService.getTodayPush();
        o.setTodayPushProblem(todayPush.orElse(null));

        int todayCheckedIn = dailyProblemRepository.countCheckedInUsersByDate(today);
        int todaySubmissions = dailyProblemRepository.countSubmissionsByDate(today);
        o.setDailySubmissionSummary(new HomeOverview.DailySubmissionSummary(todaySubmissions, todayCheckedIn));

        return o;
    }

    private record OverviewCache(int topLimit, HomeOverview value, long expiresAtNanos) {
        private static OverviewCache empty() {
            return new OverviewCache(-1, null, Long.MIN_VALUE);
        }

        private boolean matches(int requestedLimit, long nowNanos) {
            return value != null && topLimit == requestedLimit && nowNanos < expiresAtNanos;
        }
    }
}
