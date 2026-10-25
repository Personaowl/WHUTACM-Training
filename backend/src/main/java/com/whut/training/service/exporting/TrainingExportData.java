package com.whut.training.service.exporting;

import com.whut.training.domain.dto.TrainingExportRequest;
import com.whut.training.repository.AtCoderTrackingRepository;
import com.whut.training.repository.CodeforcesRatingHistoryRepository;
import com.whut.training.repository.TrainingDashboardRepository;
import java.time.LocalDate;
import java.util.List;

/** Read-only export inputs. Member snapshots carry only fields needed by the workbook. */
public record TrainingExportData(
        TrainingExportRequest request, LocalDate startDate, LocalDate endDate, String filename,
        List<Member> members,
        List<TrainingDashboardRepository.DailyExportRow> dailyRows,
        List<CodeforcesRatingHistoryRepository.RatingContestExportRow> contestRows,
        List<AtCoderTrackingRepository.AtCoderExportRow> atCoderRows
) {
    public TrainingExportData {
        members = List.copyOf(members);
        dailyRows = List.copyOf(dailyRows);
        contestRows = List.copyOf(contestRows);
        atCoderRows = List.copyOf(atCoderRows);
    }

    public record Member(Long id, String username, String displayName, String codeforcesHandle, Integer codeforcesRating) { }
}
