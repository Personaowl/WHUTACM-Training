package com.whut.training.domain.entity;

import java.time.LocalDate;

/**
 * 每日题位实体，记录档位（easy/mid/hard）以及是否已被重抽。
 */
public record DailyProblemSlot(
        Long id,
        LocalDate date,
        String slot,
        String problemKey,
        Integer contestId,
        String problemIndex,
        String name,
        Integer rating,
        String tags,
        String sourceUrl,
        boolean isRedrawn
) {
}
