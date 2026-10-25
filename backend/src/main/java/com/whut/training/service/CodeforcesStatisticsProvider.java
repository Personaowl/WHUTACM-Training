package com.whut.training.service;

import com.whut.training.repository.CodeforcesProfileSnapshotRepository;
import org.springframework.stereotype.Component;

@Component
public class CodeforcesStatisticsProvider implements PlatformStatisticsProvider {
    private final CodeforcesProfileSnapshotRepository snapshots;
    private final CodeforcesProfileService profiles;
    private final boolean enabled;

    public CodeforcesStatisticsProvider(CodeforcesProfileSnapshotRepository snapshots, CodeforcesProfileService profiles,
            @org.springframework.beans.factory.annotation.Value("${app.platform-statistics.enabled:true}") boolean enabled) {
        this.snapshots = snapshots;
        this.profiles = profiles;
        this.enabled = enabled;
    }

    @Override public String platform() { return "codeforces"; }

    @Override public Statistics read(Long userId, String accountId) {
        if (enabled) {
            var overview = profiles.getOverview(userId); // existing local-first background refresh
            if (!accountId.equalsIgnoreCase(overview.handle()) || overview.syncedAt() == null) return null;
            return new Statistics(overview.currentRating(), overview.maxRating(), overview.solvedCount(),
                    overview.syncedAt(), overview.submissionLimitReached(), overview.stale());
        }
        return snapshots.find(userId, accountId).map(snapshot -> {
            var overview = snapshot.overview();
            return new Statistics(overview.currentRating(), overview.maxRating(), overview.solvedCount(),
                    snapshot.syncedAt(), overview.submissionLimitReached());
        }).orElse(null);
    }
}
