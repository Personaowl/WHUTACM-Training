package com.whut.training.service;

import com.whut.training.repository.CodeforcesProfileSnapshotRepository;
import org.springframework.stereotype.Component;

@Component
public class CodeforcesStatisticsProvider implements PlatformStatisticsProvider {
    private final CodeforcesProfileSnapshotRepository snapshots;

    public CodeforcesStatisticsProvider(CodeforcesProfileSnapshotRepository snapshots) {
        this.snapshots = snapshots;
    }

    @Override public String platform() { return "codeforces"; }

    @Override public Statistics read(Long userId, String accountId) {
        return snapshots.find(userId, accountId).map(snapshot -> {
            var overview = snapshot.overview();
            return new Statistics(overview.currentRating(), overview.maxRating(), overview.solvedCount(),
                    snapshot.syncedAt(), overview.submissionLimitReached());
        }).orElse(null);
    }
}
