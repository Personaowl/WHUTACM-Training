package com.whut.training.service.exporting;

import com.whut.training.common.TimeProvider;
import com.whut.training.domain.dto.TrainingExportRequest;
import com.whut.training.domain.entity.User;
import com.whut.training.domain.enums.MemberType;
import com.whut.training.exception.BusinessException;
import com.whut.training.repository.AtCoderTrackingRepository;
import com.whut.training.repository.CodeforcesRatingHistoryRepository;
import com.whut.training.repository.TrainingDashboardRepository;
import com.whut.training.repository.UserRepository;
import com.whut.training.service.CodeforcesProfileService;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Selects the range and loads only the requested local export data. No workbook objects. */
@Component
public class TrainingExportDataLoader {
    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final UserRepository userRepository;
    private final TrainingDashboardRepository trainingDashboardRepository;
    private final CodeforcesRatingHistoryRepository ratingHistoryRepository;
    private final CodeforcesProfileService codeforcesProfileService;
    private final AtCoderTrackingRepository atCoderTrackingRepository;
    private final TimeProvider timeProvider;

    public TrainingExportDataLoader(
            UserRepository userRepository,
            TrainingDashboardRepository trainingDashboardRepository,
            CodeforcesRatingHistoryRepository ratingHistoryRepository,
            CodeforcesProfileService codeforcesProfileService,
            AtCoderTrackingRepository atCoderTrackingRepository,
            TimeProvider timeProvider
    ) {
        this.userRepository = userRepository;
        this.trainingDashboardRepository = trainingDashboardRepository;
        this.ratingHistoryRepository = ratingHistoryRepository;
        this.codeforcesProfileService = codeforcesProfileService;
        this.atCoderTrackingRepository = atCoderTrackingRepository;
        this.timeProvider = timeProvider;
    }

    public TrainingExportData load(TrainingExportRequest request) {
        if (request == null || (!request.includeDaily()
                && !request.includeCodeforcesContests()
                && !request.includeAtCoderContests())) {
            throw new BusinessException(400, "at least one export data type is required");
        }

        LocalDate today = timeProvider.today();
        LocalDate startDate = switch (request.exportRange()) {
            case WEEK -> today.minusDays(6);
            case MONTH -> today.minusDays(29);
            case ALL -> null;
        };
        Long startTimeSeconds = startDate == null
                ? null
                : startDate.atStartOfDay(ZoneId.of("Asia/Shanghai")).toEpochSecond();

        List<User> members = userRepository.findByMemberType(MemberType.ACTIVE_TEAM);
        if (request.includeCodeforcesContests()) {
            members.stream()
                    .filter(member -> member.getCodeforcesHandle() != null
                            && !member.getCodeforcesHandle().isBlank())
                    .forEach(member -> codeforcesProfileService.ensureRatingHistory(member.getId()));
        }
        List<TrainingDashboardRepository.DailyExportRow> dailyRows = request.includeDaily()
                ? trainingDashboardRepository.findActiveTeamDailyExport(startDate)
                : List.of();
        List<CodeforcesRatingHistoryRepository.RatingContestExportRow> contestRows =
                request.includeCodeforcesContests()
                        ? ratingHistoryRepository.findActiveTeamHistory(startTimeSeconds)
                        : List.of();
        List<AtCoderTrackingRepository.AtCoderExportRow> atCoderRows = request.includeAtCoderContests()
                ? atCoderTrackingRepository.findExportRows(startTimeSeconds)
                : List.of();

        String rangeName = switch (request.exportRange()) {
            case WEEK -> "最近一周";
            case MONTH -> "最近一个月";
            case ALL -> "全部";
        };
        String filename = "WHUT-ACM_现役队员训练数据_" + rangeName + "_" + FILE_DATE.format(today) + ".xlsx";

        List<TrainingExportData.Member> memberSnapshots = members.stream()
                .map(member -> new TrainingExportData.Member(member.getId(), member.getUsername(),
                        member.getDisplayName(), member.getCodeforcesHandle(), member.getCodeforcesRating()))
                .toList();
        return new TrainingExportData(request, startDate, today, filename, memberSnapshots, dailyRows, contestRows, atCoderRows);
    }
}
