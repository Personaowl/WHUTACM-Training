package com.whut.training.domain.dto;

import com.whut.training.domain.entity.User;

import java.util.List;

/** A request-scoped response; personal points must never enter the shared overview cache. */
public record HomeOverviewResponse(
        int totalUsers,
        List<LeaderboardItem> topUsers,
        Object todayProblem,
        Object todayPushProblem,
        HomeOverview.DailySubmissionSummary dailySubmissionSummary,
        boolean problemPoolInitializing,
        Long currentUserId,
        int currentUserPoints
) {
    public static HomeOverviewResponse from(HomeOverview overview, User user) {
        return new HomeOverviewResponse(
                overview.getTotalUsers(), overview.getTopUsers(), overview.getTodayProblem(),
                overview.getTodayPushProblem(), overview.getDailySubmissionSummary(),
                overview.isProblemPoolInitializing(), user.getId(),
                user.getTotalPoints() == null ? 0 : user.getTotalPoints()
        );
    }
}
