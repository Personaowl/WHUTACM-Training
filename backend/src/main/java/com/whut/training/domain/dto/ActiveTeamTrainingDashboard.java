package com.whut.training.domain.dto;

import java.util.List;

/**
 * 管理员查看的现役队员训练看板。
 */
public record ActiveTeamTrainingDashboard(
        String date,
        Summary summary,
        List<TrendDay> sevenDayTrend,
        List<MemberTraining> members
) {
    public record Summary(
            int activeMemberCount,
            int todayCompletedCount,
            int sevenDayActiveCount,
            double sevenDayCompletionRate,
            int totalPoints
    ) {
    }

    public record TrendDay(
            String date,
            int completedMembers
    ) {
    }

    public record MemberTraining(
            Long userId,
            String username,
            String displayName,
            String realName,
            String college,
            String className,
            String avatarUrl,
            String codeforcesHandle,
            Integer codeforcesRating,
            Integer maxRating,
            Integer codeforcesSolvedCount,
            int totalPoints,
            boolean online,
            Long lastOnlineTimeSeconds,
            boolean todayCompleted,
            int sevenDayCompletedDays,
            int thirtyDayCompletedDays,
            int currentStreakDays,
            String lastTrainingDate,
            int thirtyDayPracticeDraws,
            int thirtyDayPracticeSolved
    ) {
    }
}
