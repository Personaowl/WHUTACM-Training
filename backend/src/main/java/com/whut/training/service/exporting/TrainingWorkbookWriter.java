package com.whut.training.service.exporting;

import com.whut.training.domain.dto.TrainingExportRequest;
import com.whut.training.repository.AtCoderTrackingRepository;
import com.whut.training.repository.CodeforcesRatingHistoryRepository;
import com.whut.training.repository.TrainingDashboardRepository;
import org.apache.poi.common.usermodel.HyperlinkType;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.Date;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Renders already-loaded data. Does not query repositories or contact external platforms. */
@Component
public class TrainingWorkbookWriter {
    public byte[] write(TrainingExportData data) {
        var request = data.request();
        var startDate = data.startDate();
        var today = data.endDate();
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            TrainingWorkbookStyles styles = new TrainingWorkbookStyles(workbook);
            writeSummarySheet(workbook, styles, data.members(), data.dailyRows(), data.contestRows(), request, startDate, today);
            if (request.includeDaily()) writeDailySheet(workbook, styles, data.dailyRows(), startDate, today);
            if (request.includeCodeforcesContests()) writeContestSheet(workbook, styles, data.contestRows(), startDate, today);
            if (request.includeAtCoderContests()) writeAtCoderSheet(workbook, styles, data.atCoderRows(), startDate, today);
            workbook.setActiveSheet(0);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("failed to generate training workbook", ex);
        }
    }

    private void writeSummarySheet(
            XSSFWorkbook workbook,
            TrainingWorkbookStyles styles,
            List<TrainingExportData.Member> members,
            List<TrainingDashboardRepository.DailyExportRow> dailyRows,
            List<CodeforcesRatingHistoryRepository.RatingContestExportRow> contestRows,
            TrainingExportRequest request,
            LocalDate startDate,
            LocalDate endDate
    ) {
        Sheet sheet = workbook.createSheet("成员汇总");
        sheet.setDisplayGridlines(false);
        String[] headers = {
                "站内账号", "成员姓名", "Codeforces账号", "每日题题目数", "每日题完成数",
                "每日题完成率", "每日题活跃天数", "CF Rated比赛数", "Rating总变化",
                "当前Rating", "数据说明"
        };
        createTitle(sheet, styles, headers.length, "WHUT-ACM 现役队员训练数据汇总");
        Row meta = sheet.createRow(1);
        textCell(meta, 0, "导出范围：" + rangeLabel(startDate, endDate), styles.meta);
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, headers.length - 1));
        Row note = sheet.createRow(2);
        textCell(note, 0, "导出内容：" + selectedTypesLabel(request), styles.meta);
        sheet.addMergedRegion(new CellRangeAddress(2, 2, 0, headers.length - 1));
        int headerRowIndex = 4;
        createHeader(sheet, headerRowIndex, headers, styles);

        Map<Long, DailyStats> dailyStats = aggregateDaily(dailyRows);
        Map<Long, ContestStats> contestStats = aggregateContests(contestRows);

        int rowIndex = headerRowIndex + 1;
        for (TrainingExportData.Member member : members) {
            Row row = sheet.createRow(rowIndex++);
            CellStyle base = row.getRowNum() % 2 == 0 ? styles.bodyAlternate : styles.body;
            DailyStats daily = dailyStats.getOrDefault(member.id(), new DailyStats());
            ContestStats contests = contestStats.getOrDefault(member.id(), new ContestStats());
            textCell(row, 0, member.username(), base);
            textCell(row, 1, displayName(member), base);
            textCell(row, 2, member.codeforcesHandle(), base);
            nullableNumberCell(row, 3, request.includeDaily() ? daily.assigned : null, styles.integer);
            nullableNumberCell(row, 4, request.includeDaily() ? daily.completed : null, styles.integer);
            if (request.includeDaily()) {
                numberCell(row, 5, daily.assigned == 0 ? 0 : daily.completed * 1.0 / daily.assigned, styles.percent);
                numberCell(row, 6, daily.activeDates.size(), styles.integer);
            } else {
                textCell(row, 5, null, base);
                textCell(row, 6, null, base);
            }
            nullableNumberCell(row, 7, request.includeCodeforcesContests() ? contests.count : null, styles.integer);
            nullableNumberCell(row, 8, request.includeCodeforcesContests() ? contests.ratingDelta : null, styles.integer);
            nullableNumberCell(row, 9, request.includeCodeforcesContests()
                    ? (contests.latestRating == null ? member.codeforcesRating() : contests.latestRating)
                    : null, styles.integer);
            textCell(row, 10, buildMemberNote(request, member, contests), base);
        }
        finishTable(sheet, headerRowIndex, rowIndex - 1, headers.length,
                new int[]{16, 18, 20, 15, 15, 16, 16, 18, 16, 15, 32});
    }

    private void writeDailySheet(
            XSSFWorkbook workbook,
            TrainingWorkbookStyles styles,
            List<TrainingDashboardRepository.DailyExportRow> rows,
            LocalDate startDate,
            LocalDate endDate
    ) {
        Sheet sheet = workbook.createSheet("每日一题");
        sheet.setDisplayGridlines(false);
        String[] headers = {
                "日期", "站内账号", "成员姓名", "题目类型", "题目编号", "题目名称",
                "Rating", "标签", "完成状态", "提交ID", "判题结果", "获得积分", "题目链接"
        };
        createTitle(sheet, styles, headers.length, "每日一题完成明细");
        createRangeMeta(sheet, styles, headers.length, startDate, endDate);
        int headerRowIndex = 3;
        createHeader(sheet, headerRowIndex, headers, styles);
        int rowIndex = headerRowIndex + 1;
        for (TrainingDashboardRepository.DailyExportRow item : rows) {
            Row row = sheet.createRow(rowIndex++);
            CellStyle base = row.getRowNum() % 2 == 0 ? styles.bodyAlternate : styles.body;
            dateCell(row, 0, item.date(), styles.date);
            textCell(row, 1, item.username(), base);
            textCell(row, 2, item.displayName(), base);
            textCell(row, 3, slotLabel(item.slot()), base);
            textCell(row, 4, item.problemKey(), base);
            textCell(row, 5, item.problemName(), base);
            nullableNumberCell(row, 6, item.rating(), styles.integer);
            textCell(row, 7, item.tags(), base);
            textCell(row, 8, item.completed() ? "已完成" : "未完成",
                    item.completed() ? styles.completed : styles.pending);
            textCell(row, 9, item.submissionId() == null ? null : String.valueOf(item.submissionId()), base);
            textCell(row, 10, item.verdict(), base);
            nullableNumberCell(row, 11, item.score(), styles.integer);
            hyperlinkCell(workbook, row, 12, item.sourceUrl(), styles.hyperlink);
        }
        finishTable(sheet, headerRowIndex, rowIndex - 1, headers.length,
                new int[]{13, 16, 18, 13, 15, 32, 11, 30, 13, 16, 14, 13, 38});
    }

    private void writeContestSheet(
            XSSFWorkbook workbook,
            TrainingWorkbookStyles styles,
            List<CodeforcesRatingHistoryRepository.RatingContestExportRow> rows,
            LocalDate startDate,
            LocalDate endDate
    ) {
        Sheet sheet = workbook.createSheet("CF Rating比赛");
        sheet.setDisplayGridlines(false);
        String[] headers = {
                "比赛时间", "站内账号", "成员姓名", "CF账号", "比赛ID", "比赛名称",
                "排名", "赛前Rating", "赛后Rating", "Rating变化", "比赛链接"
        };
        createTitle(sheet, styles, headers.length, "Codeforces Rated 比赛完成明细");
        createRangeMeta(sheet, styles, headers.length, startDate, endDate);
        int headerRowIndex = 3;
        createHeader(sheet, headerRowIndex, headers, styles);
        int rowIndex = headerRowIndex + 1;
        for (CodeforcesRatingHistoryRepository.RatingContestExportRow item : rows) {
            Row row = sheet.createRow(rowIndex++);
            CellStyle base = row.getRowNum() % 2 == 0 ? styles.bodyAlternate : styles.body;
            dateTimeCell(row, 0, item.ratingUpdateTimeSeconds(), styles.dateTime);
            textCell(row, 1, item.username(), base);
            textCell(row, 2, item.displayName(), base);
            textCell(row, 3, item.codeforcesHandle(), base);
            textCell(row, 4, item.contestId() == null ? null : String.valueOf(item.contestId()), base);
            textCell(row, 5, item.contestName(), base);
            nullableNumberCell(row, 6, item.rank(), styles.integer);
            nullableNumberCell(row, 7, item.oldRating(), styles.integer);
            nullableNumberCell(row, 8, item.newRating(), styles.integer);
            CellStyle changeStyle = item.ratingChange() == null
                    ? styles.integer
                    : item.ratingChange() >= 0 ? styles.positive : styles.negative;
            nullableNumberCell(row, 9, item.ratingChange(), changeStyle);
            hyperlinkCell(workbook, row, 10, item.contestUrl(), styles.hyperlink);
        }
        finishTable(sheet, headerRowIndex, rowIndex - 1, headers.length,
                new int[]{20, 16, 18, 20, 13, 38, 12, 14, 14, 14, 38});
    }

    private void writeAtCoderSheet(
            XSSFWorkbook workbook,
            TrainingWorkbookStyles styles,
            List<AtCoderTrackingRepository.AtCoderExportRow> rows,
            LocalDate startDate,
            LocalDate endDate
    ) {
        Sheet sheet = workbook.createSheet("AtCoder ABC");
        sheet.setDisplayGridlines(false);
        String[] headers = {
                "比赛时间", "比赛ID", "比赛名称", "站内账号", "成员姓名", "AtCoder账号",
                "检查状态", "是否参赛", "AC数", "通过题目", "比赛排名", "Performance",
                "赛前Rating", "赛后Rating", "豁免原因", "最近检查", "比赛链接"
        };
        createTitle(sheet, styles, headers.length, "AtCoder Beginner Contest 周赛完成明细");
        createRangeMeta(sheet, styles, headers.length, startDate, endDate);
        int headerRowIndex = 3;
        createHeader(sheet, headerRowIndex, headers, styles);
        int rowIndex = headerRowIndex + 1;
        for (AtCoderTrackingRepository.AtCoderExportRow item : rows) {
            Row row = sheet.createRow(rowIndex++);
            CellStyle base = row.getRowNum() % 2 == 0 ? styles.bodyAlternate : styles.body;
            dateTimeCell(row, 0, item.startTimeSeconds(), styles.dateTime);
            textCell(row, 1, item.contestId(), base);
            textCell(row, 2, item.contestName(), base);
            textCell(row, 3, item.username(), base);
            textCell(row, 4, item.displayName(), base);
            textCell(row, 5, item.atcoderHandle(), base);
            String status = item.exempted() ? "EXEMPT" : item.status();
            textCell(row, 6, atCoderStatusLabel(status),
                    "COMPLETED".equals(status) ? styles.completed : styles.pending);
            textCell(row, 7, item.participated() ? "是" : "否", base);
            nullableNumberCell(row, 8, item.acCount(), styles.integer);
            textCell(row, 9, item.solvedProblemIds(), base);
            nullableNumberCell(row, 10, item.contestRank(), styles.integer);
            nullableNumberCell(row, 11, item.performance(), styles.integer);
            nullableNumberCell(row, 12, item.oldRating(), styles.integer);
            nullableNumberCell(row, 13, item.newRating(), styles.integer);
            textCell(row, 14, item.exemptionReason(), base);
            textCell(row, 15, item.checkedAt(), base);
            hyperlinkCell(workbook, row, 16, item.contestUrl(), styles.hyperlink);
        }
        finishTable(sheet, headerRowIndex, rowIndex - 1, headers.length,
                new int[]{20, 13, 38, 16, 18, 20, 16, 12, 10, 30, 12, 15, 14, 14, 28, 24, 38});
    }

    private Map<Long, DailyStats> aggregateDaily(List<TrainingDashboardRepository.DailyExportRow> rows) {
        Map<Long, DailyStats> result = new HashMap<>();
        for (TrainingDashboardRepository.DailyExportRow row : rows) {
            DailyStats stats = result.computeIfAbsent(row.userId(), ignored -> new DailyStats());
            stats.assigned += 1;
            if (row.completed()) {
                stats.completed += 1;
                stats.activeDates.add(row.date());
            }
        }
        return result;
    }

    private Map<Long, ContestStats> aggregateContests(
            List<CodeforcesRatingHistoryRepository.RatingContestExportRow> rows
    ) {
        Map<Long, ContestStats> result = new HashMap<>();
        for (CodeforcesRatingHistoryRepository.RatingContestExportRow row : rows) {
            ContestStats stats = result.computeIfAbsent(row.userId(), ignored -> new ContestStats());
            stats.count += 1;
            stats.ratingDelta += row.ratingChange() == null ? 0 : row.ratingChange();
            if (stats.latestTime == null || (row.ratingUpdateTimeSeconds() != null
                    && row.ratingUpdateTimeSeconds() > stats.latestTime)) {
                stats.latestTime = row.ratingUpdateTimeSeconds();
                stats.latestRating = row.newRating();
            }
        }
        return result;
    }

    private void createTitle(Sheet sheet, TrainingWorkbookStyles styles, int columnCount, String title) {
        Row row = sheet.createRow(0);
        row.setHeightInPoints(30);
        textCell(row, 0, title, styles.title);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, columnCount - 1));
    }

    private void createRangeMeta(Sheet sheet, TrainingWorkbookStyles styles, int columnCount, LocalDate startDate, LocalDate endDate) {
        Row meta = sheet.createRow(1);
        textCell(meta, 0, "数据范围：" + rangeLabel(startDate, endDate), styles.meta);
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, columnCount - 1));
    }

    private void createHeader(Sheet sheet, int rowIndex, String[] headers, TrainingWorkbookStyles styles) {
        Row row = sheet.createRow(rowIndex);
        row.setHeightInPoints(24);
        for (int column = 0; column < headers.length; column += 1) {
            textCell(row, column, headers[column], styles.header);
        }
    }

    private void finishTable(Sheet sheet, int headerRow, int lastRow, int columnCount, int[] widths) {
        sheet.createFreezePane(0, headerRow + 1);
        if (lastRow >= headerRow) {
            sheet.setAutoFilter(new CellRangeAddress(headerRow, Math.max(headerRow, lastRow), 0, columnCount - 1));
        }
        for (int column = 0; column < widths.length; column += 1) {
            sheet.setColumnWidth(column, Math.min(60, widths[column]) * 256);
        }
    }

    private void textCell(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value == null ? "" : value);
        cell.setCellStyle(style);
    }

    private void numberCell(Row row, int column, double value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private void nullableNumberCell(Row row, int column, Number value, CellStyle style) {
        Cell cell = row.createCell(column);
        if (value != null) cell.setCellValue(value.doubleValue());
        cell.setCellStyle(style);
    }

    private void dateCell(Row row, int column, LocalDate value, CellStyle style) {
        Cell cell = row.createCell(column);
        if (value != null) cell.setCellValue(Date.valueOf(value));
        cell.setCellStyle(style);
    }

    private void dateTimeCell(Row row, int column, Long epochSeconds, CellStyle style) {
        Cell cell = row.createCell(column);
        if (epochSeconds != null) cell.setCellValue(new java.util.Date(epochSeconds * 1000));
        cell.setCellStyle(style);
    }

    private void hyperlinkCell(XSSFWorkbook workbook, Row row, int column, String url, CellStyle style) {
        Cell cell = row.createCell(column);
        if (url != null && !url.isBlank()) {
            cell.setCellValue(url);
            var hyperlink = workbook.getCreationHelper().createHyperlink(HyperlinkType.URL);
            hyperlink.setAddress(url);
            cell.setHyperlink(hyperlink);
        }
        cell.setCellStyle(style);
    }

    private String selectedTypesLabel(TrainingExportRequest request) {
        java.util.ArrayList<String> names = new java.util.ArrayList<>();
        if (request.includeDaily()) names.add("每日一题");
        if (request.includeCodeforcesContests()) names.add("CF Rating 比赛");
        if (request.includeAtCoderContests()) names.add("AtCoder ABC");
        return String.join("、", names);
    }

    private String atCoderStatusLabel(String status) {
        if (status == null) return "待同步";
        return switch (status) {
            case "COMPLETED" -> "已完成";
            case "PARTICIPATED" -> "已参赛未达标";
            case "ABSENT" -> "缺席";
            case "UNBOUND" -> "未绑定";
            case "EXEMPT" -> "已豁免";
            case "DATA_ERROR" -> "数据异常";
            case "UPCOMING" -> "待比赛";
            default -> "等待同步";
        };
    }

    private String rangeLabel(LocalDate startDate, LocalDate endDate) {
        return startDate == null ? "全部历史数据（截至 " + endDate + "）" : startDate + " 至 " + endDate;
    }

    private String buildMemberNote(
            TrainingExportRequest request,
            TrainingExportData.Member member,
            ContestStats contestStats
    ) {
        if (!request.includeCodeforcesContests()) return "";
        if (member.codeforcesHandle() == null || member.codeforcesHandle().isBlank()) return "未绑定 Codeforces";
        if (contestStats.count == 0) return "选定范围内无已同步 Rated 比赛";
        return "CF 比赛数据来自平台本地同步记录";
    }

    private String displayName(TrainingExportData.Member user) {
        return user.displayName() == null || user.displayName().isBlank()
                ? user.username()
                : user.displayName();
    }

    private String slotLabel(String slot) {
        if ("easy".equalsIgnoreCase(slot)) return "简单题";
        if ("hard".equalsIgnoreCase(slot)) return "困难题";
        return "每日题";
    }

    private static final class DailyStats {
        private int assigned;
        private int completed;
        private final Set<LocalDate> activeDates = new HashSet<>();
    }

    private static final class ContestStats {
        private int count;
        private int ratingDelta;
        private Long latestTime;
        private Integer latestRating;
    }

}
