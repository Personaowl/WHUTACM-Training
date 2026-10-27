package com.whut.training.domain.dto;

import java.time.Instant;
import java.util.List;

/**
 * 用户的 Codeforces 公开统计快照。
 */
public record CodeforcesOverview(
        String handle,
        Integer currentRating,
        Integer maxRating,
        int solvedCount,
        int attemptedCount,
        int acceptedSubmissionCount,
        int ratedContestCount,
        Long lastSubmissionAtSeconds,
        boolean submissionLimitReached,
        Instant syncedAt,
        boolean stale,
        List<TagStat> tagStats,
        List<RecentContest> recentContests,
        List<RatingStat> ratingStats
) {
    /** Legacy constructors/snapshots have no difficulty distribution until their next refresh. */
    public CodeforcesOverview(String handle, Integer currentRating, Integer maxRating,
                              int solvedCount, int attemptedCount, int acceptedSubmissionCount,
                              int ratedContestCount, Long lastSubmissionAtSeconds,
                              boolean submissionLimitReached, Instant syncedAt, boolean stale,
                              List<TagStat> tagStats, List<RecentContest> recentContests) {
        this(handle, currentRating, maxRating, solvedCount, attemptedCount, acceptedSubmissionCount,
                ratedContestCount, lastSubmissionAtSeconds, submissionLimitReached, syncedAt,
                stale, tagStats, recentContests, null);
    }

    public CodeforcesOverview withStale(boolean nextStale) {
        return new CodeforcesOverview(
                handle,
                currentRating,
                maxRating,
                solvedCount,
                attemptedCount,
                acceptedSubmissionCount,
                ratedContestCount,
                lastSubmissionAtSeconds,
                submissionLimitReached,
                syncedAt,
                nextStale,
                tagStats,
                recentContests,
                ratingStats
        );
    }

    public record TagStat(String tag, int count) {
    }

    /** A null rating represents solved problems without a public difficulty. */
    public record RatingStat(Integer rating, int count) {
    }

    public record RecentContest(
            Long contestId,
            String contestName,
            Integer rank,
            Integer oldRating,
            Integer newRating,
            Integer ratingChange,
            Long ratingUpdateTimeSeconds,
            String url
    ) {
    }
}
