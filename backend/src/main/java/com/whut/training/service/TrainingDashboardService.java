package com.whut.training.service;

import com.whut.training.common.TimeProvider;
import com.whut.training.domain.dto.ActiveTeamTrainingDashboard;
import com.whut.training.domain.entity.User;
import com.whut.training.domain.enums.MemberType;
import com.whut.training.repository.CodeforcesProfileSnapshotRepository;
import com.whut.training.repository.TrainingDashboardRepository;
import com.whut.training.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 现役队员训练看板聚合服务。
 */
@Service
public class TrainingDashboardService {

    private static final int ACTIVITY_WINDOW_DAYS = 365;

    private final UserRepository userRepository;
    private final TrainingDashboardRepository trainingDashboardRepository;
    private final CodeforcesProfileSnapshotRepository snapshotRepository;
    private final TimeProvider timeProvider;

    public TrainingDashboardService(
            UserRepository userRepository,
            TrainingDashboardRepository trainingDashboardRepository,
            CodeforcesProfileSnapshotRepository snapshotRepository,
            TimeProvider timeProvider
    ) {
        this.userRepository = userRepository;
        this.trainingDashboardRepository = trainingDashboardRepository;
        this.snapshotRepository = snapshotRepository;
        this.timeProvider = timeProvider;
    }

    public ActiveTeamTrainingDashboard getDashboard() {
        LocalDate today = timeProvider.today();
        LocalDate activityStart = today.minusDays(ACTIVITY_WINDOW_DAYS - 1L);
        LocalDate thirtyDayStart = today.minusDays(29);
        LocalDate sevenDayStart = today.minusDays(6);

        List<User> activeMembers = userRepository.findByMemberType(MemberType.ACTIVE_TEAM);
        List<TrainingDashboardRepository.DailyActivity> activities =
                trainingDashboardRepository.findActiveTeamDailyActivity(activityStart, today);
        Map<Long, TrainingDashboardRepository.PracticeSummary> practiceByUser = new HashMap<>();
        for (TrainingDashboardRepository.PracticeSummary practice :
                trainingDashboardRepository.findActiveTeamPracticeSummary(thirtyDayStart, today)) {
            practiceByUser.put(practice.userId(), practice);
        }
        Map<Long, CodeforcesProfileSnapshotRepository.Snapshot> snapshots =
                snapshotRepository.findAllForActiveTeam();

        Map<Long, Map<LocalDate, Integer>> activityByUser = new HashMap<>();
        for (TrainingDashboardRepository.DailyActivity activity : activities) {
            activityByUser
                    .computeIfAbsent(activity.userId(), ignored -> new HashMap<>())
                    .merge(activity.date(), activity.score(), Math::max);
        }

        int todayCompleted = 0;
        int sevenDayActive = 0;
        int sevenDayCompletionDays = 0;
        int totalPoints = 0;
        List<ActiveTeamTrainingDashboard.MemberTraining> memberRows = new ArrayList<>();

        for (User user : activeMembers) {
            Map<LocalDate, Integer> userActivity = activityByUser.getOrDefault(user.getId(), Map.of());
            boolean completedToday = userActivity.containsKey(today);
            int sevenDays = countDatesSince(userActivity.keySet(), sevenDayStart);
            int thirtyDays = countDatesSince(userActivity.keySet(), thirtyDayStart);
            int streakDays = calculateCurrentStreak(userActivity.keySet(), today, activityStart);
            String lastTrainingDate = userActivity.keySet().stream()
                    .max(LocalDate::compareTo)
                    .map(LocalDate::toString)
                    .orElse(null);
            TrainingDashboardRepository.PracticeSummary practice = practiceByUser.get(user.getId());
            CodeforcesProfileSnapshotRepository.Snapshot snapshot = snapshots.get(user.getId());
            Integer solvedCount = snapshot == null ? null : snapshot.overview().solvedCount();
            int memberPoints = user.getTotalPoints() == null ? 0 : user.getTotalPoints();

            if (completedToday) todayCompleted += 1;
            if (sevenDays > 0) sevenDayActive += 1;
            sevenDayCompletionDays += sevenDays;
            totalPoints += memberPoints;

            memberRows.add(new ActiveTeamTrainingDashboard.MemberTraining(
                    user.getId(),
                    user.getUsername(),
                    user.getDisplayName(),
                    user.getRealName(),
                    user.getCollege(),
                    user.getClassName(),
                    user.getAvatarUrl(),
                    user.getCodeforcesHandle(),
                    user.getCodeforcesRating(),
                    user.getMaxRating(),
                    solvedCount,
                    memberPoints,
                    Boolean.TRUE.equals(user.getOnline()),
                    user.getLastOnlineTimeSeconds(),
                    completedToday,
                    sevenDays,
                    thirtyDays,
                    streakDays,
                    lastTrainingDate,
                    practice == null ? 0 : practice.drawCount(),
                    practice == null ? 0 : practice.solvedCount()
            ));
        }

        memberRows.sort(Comparator
                .comparing(ActiveTeamTrainingDashboard.MemberTraining::todayCompleted).reversed()
                .thenComparing(ActiveTeamTrainingDashboard.MemberTraining::sevenDayCompletedDays, Comparator.reverseOrder())
                .thenComparing(ActiveTeamTrainingDashboard.MemberTraining::totalPoints, Comparator.reverseOrder())
                .thenComparing(ActiveTeamTrainingDashboard.MemberTraining::username, String.CASE_INSENSITIVE_ORDER));

        List<ActiveTeamTrainingDashboard.TrendDay> trend = new ArrayList<>();
        for (int offset = 6; offset >= 0; offset -= 1) {
            LocalDate date = today.minusDays(offset);
            int completedMembers = 0;
            for (User user : activeMembers) {
                if (activityByUser.getOrDefault(user.getId(), Map.of()).containsKey(date)) {
                    completedMembers += 1;
                }
            }
            trend.add(new ActiveTeamTrainingDashboard.TrendDay(date.toString(), completedMembers));
        }

        double completionRate = activeMembers.isEmpty()
                ? 0.0
                : Math.round(sevenDayCompletionDays * 1000.0 / (activeMembers.size() * 7.0)) / 10.0;

        return new ActiveTeamTrainingDashboard(
                today.toString(),
                new ActiveTeamTrainingDashboard.Summary(
                        activeMembers.size(),
                        todayCompleted,
                        sevenDayActive,
                        completionRate,
                        totalPoints
                ),
                trend,
                List.copyOf(memberRows)
        );
    }

    private int countDatesSince(Set<LocalDate> dates, LocalDate startDate) {
        int count = 0;
        for (LocalDate date : dates) {
            if (!date.isBefore(startDate)) count += 1;
        }
        return count;
    }

    private int calculateCurrentStreak(Set<LocalDate> dates, LocalDate today, LocalDate earliestDate) {
        if (dates.isEmpty()) return 0;
        LocalDate cursor = dates.contains(today) ? today : today.minusDays(1);
        int streak = 0;
        while (!cursor.isBefore(earliestDate) && dates.contains(cursor)) {
            streak += 1;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }
}
