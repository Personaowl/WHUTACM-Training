package com.whut.training.domain.dto;

import java.time.Instant;
import java.util.List;

/** Public-source details. Null sections are unavailable, empty lists are measured empty. */
public record PlatformDetails(String platform, String accountId, Instant syncedAt, String status,
        boolean partial, String note, List<CodeforcesOverview.TagStat> tagStats,
        List<DifficultyStat> difficultyStats, List<ContestStat> recentContests,
        List<ActivityDay> activity, String activityMetric, List<ContestStat> ratingHistory) {
    public PlatformDetails(String platform, String accountId, Instant syncedAt, String status,
            boolean partial, String note, List<CodeforcesOverview.TagStat> tagStats,
            List<DifficultyStat> difficultyStats, List<ContestStat> recentContests,
            List<ActivityDay> activity, String activityMetric) {
        this(platform, accountId, syncedAt, status, partial, note, tagStats, difficultyStats,
                recentContests, activity, activityMetric, null);
    }
    public record DifficultyStat(String label, int count, String color) { }
    public record ContestStat(String contestId, String contestName, Integer rank, Integer oldRating,
            Integer newRating, Integer ratingChange, Long ratingUpdateTimeSeconds, String url) { }
    public record ActivityDay(String date, int count) { }
    public PlatformDetails withStatus(String nextStatus) {
        return new PlatformDetails(platform, accountId, syncedAt, nextStatus, partial, note,
                tagStats, difficultyStats, recentContests, activity, activityMetric, ratingHistory);
    }
    public PlatformDetails withoutRatingHistory() {
        return new PlatformDetails(platform, accountId, syncedAt, status, partial, note,
                tagStats, difficultyStats, recentContests, activity, activityMetric, null);
    }
}
