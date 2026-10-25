package com.whut.training.service;

import com.whut.training.common.TimeProvider;
import com.whut.training.service.exporting.TrainingExportDataLoader;
import com.whut.training.service.exporting.TrainingWorkbookWriter;
import com.whut.training.domain.dto.TrainingExportRequest;
import com.whut.training.domain.entity.User;
import com.whut.training.domain.enums.MemberType;
import com.whut.training.domain.enums.UserRole;
import com.whut.training.exception.BusinessException;
import com.whut.training.repository.AtCoderTrackingRepository;
import com.whut.training.repository.CodeforcesRatingHistoryRepository;
import com.whut.training.repository.TrainingDashboardRepository;
import com.whut.training.repository.UserRepository;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TrainingExportContractTest {
    private final UserRepository users = mock(UserRepository.class);
    private final TrainingDashboardRepository daily = mock(TrainingDashboardRepository.class);
    private final CodeforcesRatingHistoryRepository contests = mock(CodeforcesRatingHistoryRepository.class);
    private final CodeforcesProfileService profiles = mock(CodeforcesProfileService.class);
    private final AtCoderTrackingRepository atcoder = mock(AtCoderTrackingRepository.class);
    private final TimeProvider time = mock(TimeProvider.class);
    private final LocalDate today = LocalDate.of(2026, 10, 25);
    private final TrainingExcelExportService service = new TrainingExcelExportService(
            new TrainingExportDataLoader(users, daily, contests, profiles, atcoder, time), new TrainingWorkbookWriter());

    private User member(Long id, String handle) {
        User member = new User(id, "member-" + id, null, "secret-hash", UserRole.USER);
        member.setDisplayName("=SUM(A1:A2)");
        member.setCodeforcesHandle(handle);
        member.setCodeforcesRating(1961);
        return member;
    }
    private void fixture() {
        when(time.today()).thenReturn(today);
        when(users.findByMemberType(MemberType.ACTIVE_TEAM)).thenReturn(List.of(member(7L, "owl"), member(8L, null)));
        when(daily.findActiveTeamDailyExport(nullable(LocalDate.class))).thenReturn(List.of(
                row(today, "easy", true, 1200), row(today, "hard", true, 700), row(today.minusDays(1), "easy", false, 0)));
        when(contests.findActiveTeamHistory(nullable(Long.class))).thenReturn(List.of(
                new CodeforcesRatingHistoryRepository.RatingContestExportRow(7L, "member-7", "Name", "owl", 1L,
                        "Earlier", 321, 1900, 1961, 61, 1700000000L, "https://codeforces.com/contest/1"),
                new CodeforcesRatingHistoryRepository.RatingContestExportRow(7L, "member-7", "Name", "owl", 2L,
                        "Later", 500, 1961, 1950, -11, 1800000000L, "https://codeforces.com/contest/2")));
        when(atcoder.findExportRows(nullable(Long.class))).thenReturn(List.of(
                new AtCoderTrackingRepository.AtCoderExportRow("abc460", "ABC 460", 1780148400L,
                        "https://atcoder.jp/contests/abc460", 7L, "member-7", "Name", "owl", true,
                        "Exam", "ABSENT", false, null, null, null, null, false, null, null, null)));
    }
    private TrainingDashboardRepository.DailyExportRow row(LocalDate date, String slot, boolean done, int score) {
        return new TrainingDashboardRepository.DailyExportRow(7L, "member-7", "Name", date, slot,
                "123-A", "Example", 1200, "math", "https://codeforces.com/problemset/problem/123/A",
                done, done ? 9007199254740993L : null, done ? "OK" : null, score);
    }
    private XSSFWorkbook workbook(TrainingExportRequest request) throws Exception {
        return new XSSFWorkbook(new ByteArrayInputStream(service.export(request).content()));
    }

    @Test void allTwentyOneRangeAndSheetSelectionsKeepOrderAndQueryCutoffs() throws Exception {
        fixture();
        for (String range : List.of("WEEK", "MONTH", "ALL")) {
            LocalDate start = range.equals("WEEK") ? today.minusDays(6) : range.equals("MONTH") ? today.minusDays(29) : null;
            Long epoch = start == null ? null : start.atStartOfDay(ZoneId.of("Asia/Shanghai")).toEpochSecond();
            for (int mask = 1; mask <= 7; mask++) {
                clearInvocations(users, daily, contests, profiles, atcoder);
                boolean d = (mask & 1) != 0, c = (mask & 2) != 0, a = (mask & 4) != 0;
                var request = new TrainingExportRequest(range, d, c, a);
                var result = service.export(request);
                assertThat(result.filename()).endsWith("20261025.xlsx");
                try (var book = new XSSFWorkbook(new ByteArrayInputStream(result.content()))) {
                    var names = new java.util.ArrayList<String>();
                    names.add("成员汇总");
                    if (d) names.add("每日一题");
                    if (c) names.add("CF Rating比赛");
                    if (a) names.add("AtCoder ABC");
                    assertThat(java.util.stream.IntStream.range(0, book.getNumberOfSheets()).mapToObj(book::getSheetName).toList()).isEqualTo(names);
                    assertThat(book.getActiveSheetIndex()).isZero();
                }
                verify(users).findByMemberType(MemberType.ACTIVE_TEAM);
                if (d) verify(daily).findActiveTeamDailyExport(start); else verifyNoInteractions(daily);
                if (c) { verify(contests).findActiveTeamHistory(epoch); verify(profiles).ensureRatingHistory(7L); verify(profiles, never()).ensureRatingHistory(8L); }
                else verifyNoInteractions(contests, profiles);
                if (a) verify(atcoder).findExportRows(epoch); else verifyNoInteractions(atcoder);
            }
        }
    }

    @Test void summaryNumbersFormatsLinksAndLongIdentifiersRemainStable() throws Exception {
        fixture();
        try (var book = workbook(new TrainingExportRequest("WEEK", true, true, true))) {
            var summary = book.getSheetAt(0);
            var row = summary.getRow(5);
            assertThat(row.getCell(1).getCellType()).isEqualTo(CellType.STRING);
            assertThat(row.getCell(1).getStringCellValue()).isEqualTo("=SUM(A1:A2)");
            assertThat(row.getCell(3).getNumericCellValue()).isEqualTo(3);
            assertThat(row.getCell(4).getNumericCellValue()).isEqualTo(2);
            assertThat(row.getCell(5).getNumericCellValue()).isEqualTo(2.0 / 3);
            assertThat(row.getCell(5).getCellStyle().getDataFormatString()).isEqualTo("0.0%");
            assertThat(row.getCell(6).getNumericCellValue()).isEqualTo(1);
            assertThat(row.getCell(7).getNumericCellValue()).isEqualTo(2);
            assertThat(row.getCell(8).getNumericCellValue()).isEqualTo(50);
            assertThat(row.getCell(9).getNumericCellValue()).isEqualTo(1950);
            assertThat(summary.getRow(6).getCell(10).getStringCellValue()).isEqualTo("未绑定 Codeforces");
            var dailySheet = book.getSheet("每日一题");
            assertThat(dailySheet.getRow(4).getCell(9).getStringCellValue()).isEqualTo("9007199254740993");
            assertThat(dailySheet.getRow(4).getCell(0).getCellStyle().getDataFormatString()).isEqualTo("yyyy-mm-dd");
            assertThat(dailySheet.getRow(4).getCell(12).getHyperlink().getAddress()).isEqualTo("https://codeforces.com/problemset/problem/123/A");
            assertThat(dailySheet.getColumnWidth(12)).isEqualTo(38 * 256);
            assertThat(dailySheet.getPaneInformation().getHorizontalSplitPosition()).isEqualTo((short) 4);
            assertThat(book.getSheet("CF Rating比赛").getRow(5).getCell(9).getNumericCellValue()).isEqualTo(-11);
            assertThat(book.getSheet("AtCoder ABC").getRow(4).getCell(6).getStringCellValue()).isEqualTo("已豁免");
            assertThat(book.getNumCellStyles()).isLessThan(25);
        }
    }

    @Test void unselectedDailyAndContestMetricsStayBlankInsteadOfInventingZeros() throws Exception {
        fixture();
        try (var book = workbook(new TrainingExportRequest("ALL", false, false, true))) {
            var row = book.getSheetAt(0).getRow(5);
            assertThat(row.getCell(3).getCellType()).isEqualTo(CellType.BLANK);
            assertThat(row.getCell(7).getCellType()).isEqualTo(CellType.BLANK);
            assertThat(row.getCell(9).getCellType()).isEqualTo(CellType.BLANK);
            assertThat(book.getSheetAt(0).getRow(1).getCell(0).getStringCellValue()).contains("全部历史数据");
        }
    }

    @Test void invalidSelectionDoesNotReadTimeRepositoriesOrExternalProfiles() {
        for (var request : java.util.Arrays.asList(null, new TrainingExportRequest("WEEK", false, false, false))) {
            assertThatThrownBy(() -> service.export(request)).isInstanceOfSatisfying(BusinessException.class,
                    error -> assertThat(error.getCode()).isEqualTo(400));
        }
        verifyNoInteractions(users, daily, contests, profiles, atcoder, time);
    }
}
