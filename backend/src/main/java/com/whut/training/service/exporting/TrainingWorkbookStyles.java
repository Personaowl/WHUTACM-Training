package com.whut.training.service.exporting;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** Styles are created once per workbook, never shared across requests. */
final class TrainingWorkbookStyles {
    final CellStyle title;
    final CellStyle meta;
    final CellStyle header;
    final CellStyle body;
    final CellStyle bodyAlternate;
    final CellStyle integer;
    final CellStyle percent;
    final CellStyle date;
    final CellStyle dateTime;
    final CellStyle hyperlink;
    final CellStyle completed;
    final CellStyle pending;
    final CellStyle positive;
    final CellStyle negative;

    TrainingWorkbookStyles(XSSFWorkbook workbook) {
        title = workbook.createCellStyle();
        setFillColor(title, "132238");
        title.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        title.setAlignment(HorizontalAlignment.LEFT);
        title.setVerticalAlignment(VerticalAlignment.CENTER);
        Font titleFont = workbook.createFont();
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 16);
        titleFont.setColor(IndexedColors.WHITE.getIndex());
        title.setFont(titleFont);

        meta = workbook.createCellStyle();
        setFillColor(meta, "E8EEF5");
        meta.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        meta.setVerticalAlignment(VerticalAlignment.CENTER);
        Font metaFont = workbook.createFont();
        setFontColor(metaFont, "44546A");
        metaFont.setFontHeightInPoints((short) 10);
        meta.setFont(metaFont);

        header = workbook.createCellStyle();
        setFillColor(header, "183651");
        header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        header.setAlignment(HorizontalAlignment.CENTER);
        header.setVerticalAlignment(VerticalAlignment.CENTER);
        header.setBorderBottom(BorderStyle.THIN);
        header.setBottomBorderColor(IndexedColors.BLUE_GREY.getIndex());
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerFont.setColor(IndexedColors.WHITE.getIndex());
        header.setFont(headerFont);

        body = bodyStyle(workbook, "FFFFFF");
        bodyAlternate = bodyStyle(workbook, "F6F8FA");
        integer = workbook.createCellStyle();
        integer.cloneStyleFrom(body);
        integer.setDataFormat(workbook.createDataFormat().getFormat("#,##0"));
        integer.setAlignment(HorizontalAlignment.RIGHT);
        percent = workbook.createCellStyle();
        percent.cloneStyleFrom(body);
        percent.setDataFormat(workbook.createDataFormat().getFormat("0.0%"));
        percent.setAlignment(HorizontalAlignment.RIGHT);
        date = workbook.createCellStyle();
        date.cloneStyleFrom(body);
        date.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd"));
        dateTime = workbook.createCellStyle();
        dateTime.cloneStyleFrom(body);
        dateTime.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd hh:mm"));
        hyperlink = workbook.createCellStyle();
        hyperlink.cloneStyleFrom(body);
        Font linkFont = workbook.createFont();
        setFontColor(linkFont, "0563C1");
        linkFont.setUnderline(Font.U_SINGLE);
        hyperlink.setFont(linkFont);

        completed = statusStyle(workbook, "EAF8ED", "216E39");
        pending = statusStyle(workbook, "FDECEC", "B42318");
        positive = statusStyle(workbook, "EAF8ED", "216E39");
        positive.setDataFormat(workbook.createDataFormat().getFormat("+0;-0;0"));
        negative = statusStyle(workbook, "FDECEC", "B42318");
        negative.setDataFormat(workbook.createDataFormat().getFormat("+0;-0;0"));
    }

    private static CellStyle bodyStyle(XSSFWorkbook workbook, String fill) {
        CellStyle style = workbook.createCellStyle();
        setFillColor(style, fill);
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.HAIR);
        style.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        Font font = workbook.createFont();
        setFontColor(font, "132238");
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        return style;
    }

    private static CellStyle statusStyle(XSSFWorkbook workbook, String fill, String fontColor) {
        CellStyle style = bodyStyle(workbook, fill);
        style.setAlignment(HorizontalAlignment.CENTER);
        Font font = workbook.createFont();
        font.setBold(true);
        setFontColor(font, fontColor);
        style.setFont(font);
        return style;
    }

    private static XSSFColor color(String hex) {
        return new XSSFColor(java.awt.Color.decode("#" + hex), null);
    }

    private static void setFillColor(CellStyle style, String hex) {
        ((XSSFCellStyle) style).setFillForegroundColor(color(hex));
    }

    private static void setFontColor(Font font, String hex) {
        ((XSSFFont) font).setColor(color(hex));
    }
}
