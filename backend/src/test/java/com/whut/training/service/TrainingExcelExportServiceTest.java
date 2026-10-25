package com.whut.training.service;

import com.whut.training.common.TimeProvider;
import com.whut.training.service.exporting.TrainingExportDataLoader;
import com.whut.training.service.exporting.TrainingWorkbookWriter;
import com.whut.training.domain.dto.TrainingExportRequest;
import com.whut.training.domain.entity.User;
import com.whut.training.domain.enums.MemberType;
import com.whut.training.domain.enums.UserRole;
import com.whut.training.exception.BusinessException;
import com.whut.training.repository.CodeforcesRatingHistoryRepository;
import com.whut.training.repository.AtCoderTrackingRepository;
import com.whut.training.repository.TrainingDashboardRepository;
import com.whut.training.repository.UserRepository;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TrainingExcelExportServiceTest {

    @Test
    void createsAReadableWorkbookWithOneSheetPerSelectedDataType() throws Exception {
        UserRepository userRepository = mock(UserRepository.class);
        TrainingDashboardRepository dashboardRepository = mock(TrainingDashboardRepository.class);
        CodeforcesRatingHistoryRepository ratingRepository = mock(CodeforcesRatingHistoryRepository.class);
        CodeforcesProfileService profileService = mock(CodeforcesProfileService.class);
        AtCoderTrackingRepository atCoderRepository = mock(AtCoderTrackingRepository.class);
        TimeProvider timeProvider = mock(TimeProvider.class);
        LocalDate today = LocalDate.of(2026, 8, 1);
        LocalDate startDate = today.minusDays(6);

        User member = new User(7L, "owl", null, "password", UserRole.USER);
        member.setDisplayName("猫头鹰");
        member.setMemberType(MemberType.ACTIVE_TEAM);
        member.setCodeforcesHandle("Persona_owl");
        member.setCodeforcesRating(1961);

        when(timeProvider.today()).thenReturn(today);
        when(userRepository.findByMemberType(MemberType.ACTIVE_TEAM)).thenReturn(List.of(member));
        when(dashboardRepository.findActiveTeamDailyExport(startDate)).thenReturn(List.of(
                new TrainingDashboardRepository.DailyExportRow(
                        7L, "owl", "猫头鹰", today, "easy", "100-A", "Example",
                        1200, "math,greedy", "https://codeforces.com/problemset/problem/100/A",
                        true, 9_007_199_254_740_993L, "OK", 1200
                )
        ));
        when(ratingRepository.findActiveTeamHistory(startDate.atStartOfDay(
                java.time.ZoneId.of("Asia/Shanghai")).toEpochSecond())).thenReturn(List.of(
                new CodeforcesRatingHistoryRepository.RatingContestExportRow(
                        7L, "owl", "猫头鹰", "Persona_owl", 2048L, "Codeforces Round",
                        321, 1900, 1961, 61, 1_754_006_400L,
                        "https://codeforces.com/contest/2048"
                )
        ));
        when(atCoderRepository.findExportRows(startDate.atStartOfDay(
                java.time.ZoneId.of("Asia/Shanghai")).toEpochSecond())).thenReturn(List.of(
                new AtCoderTrackingRepository.AtCoderExportRow(
                        "abc460", "AtCoder Beginner Contest 460", 1_780_148_400L,
                        "https://atcoder.jp/contests/abc460", 7L, "owl", "猫头鹰", "Persona_owl",
                        false, null, "COMPLETED", true, 2, "abc460_a,abc460_b",
                        321, 1800, true, 1700, 1750, "2026-08-01T23:00:00+08:00"
                )
        ));

        TrainingExcelExportService service = new TrainingExcelExportService(
                new TrainingExportDataLoader(userRepository, dashboardRepository, ratingRepository, profileService, atCoderRepository, timeProvider),
                new TrainingWorkbookWriter()
        );
        TrainingExcelExportService.ExportResult result = service.export(
                new TrainingExportRequest("WEEK", true, true, true)
        );

        assertThat(result.filename()).contains("最近一周").endsWith("20260801.xlsx");
        verify(profileService).ensureRatingHistory(7L);
        assertThat(result.content()).isNotEmpty();
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(result.content()))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(4);
            assertThat(workbook.getSheetName(0)).isEqualTo("成员汇总");
            assertThat(workbook.getSheetName(1)).isEqualTo("每日一题");
            assertThat(workbook.getSheetName(2)).isEqualTo("CF Rating比赛");
            assertThat(workbook.getSheetName(3)).isEqualTo("AtCoder ABC");

            assertThat(workbook.getSheet("成员汇总").getRow(5).getCell(1).getStringCellValue())
                    .isEqualTo("猫头鹰");
            assertThat(workbook.getSheet("每日一题").getRow(4).getCell(0).getCellType())
                    .isEqualTo(CellType.NUMERIC);
            assertThat(workbook.getSheet("每日一题").getRow(4).getCell(9).getStringCellValue())
                    .isEqualTo("9007199254740993");
            assertThat(workbook.getSheet("CF Rating比赛").getRow(4).getCell(9).getNumericCellValue())
                    .isEqualTo(61);
        }
    }

    @Test
    void rejectsExportWithoutAnySelectedDataType() {
        TrainingExcelExportService service = new TrainingExcelExportService(
                new TrainingExportDataLoader(
                        mock(UserRepository.class),
                        mock(TrainingDashboardRepository.class),
                        mock(CodeforcesRatingHistoryRepository.class),
                        mock(CodeforcesProfileService.class),
                        mock(AtCoderTrackingRepository.class),
                        mock(TimeProvider.class)),
                new TrainingWorkbookWriter()
        );

        assertThatThrownBy(() -> service.export(new TrainingExportRequest("WEEK", false, false, false)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("at least one");
    }
}
