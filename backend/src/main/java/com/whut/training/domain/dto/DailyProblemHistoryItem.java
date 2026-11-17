package com.whut.training.domain.dto;

/**
 * 每日题历史条目（基于 slot）。
 *
 * @param date         日期。
 * @param slot         题位（easy/mid/hard），兼容旧单题时为 daily。
 * @param problemKey   题目唯一键。
 * @param name         题目标题。
 * @param rating       难度。
 * @param tags         题目标签。
 * @param sourceUrl    题目链接。
 * @param isRedrawn    是否已被重抽。
 * @param checkedIn    是否已打卡。
 * @param submissionId 提交 ID。
 * @param verdict      判题结果。
 * @param score        得分。
 */
public record DailyProblemHistoryItem(
        String date,
        String slot,
        String problemKey,
        String name,
        Integer rating,
        String tags,
        String sourceUrl,
        boolean isRedrawn,
        boolean checkedIn,
        Long submissionId,
        String verdict,
        Integer score
) {
}
