package com.whut.training.domain.dto;

import java.util.List;

/**
 * 今日题响应，包含各档位与用户打卡状态。
 *
 * @param problems 今日题位列表。
 * @param checkedIn 是否已打卡（任意 slot 通过）。
 * @param score     当日当前最高得分。
 */
public record DailyProblemTodayResponse(
        List<ProblemView> problems,
        boolean checkedIn,
        Integer score
) {
}
