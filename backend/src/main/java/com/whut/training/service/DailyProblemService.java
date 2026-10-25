package com.whut.training.service;

import com.whut.training.domain.dto.*;
import com.whut.training.domain.entity.User;

import java.time.LocalDate;
import java.util.List;

/**
 * 每日题服务。
 *
 * <p>负责生成今日题、校验打卡、查询历史和管理员重生成今日题。
 */
public interface DailyProblemService {
    /**
     * 获取今日题。
     *
     * @param user 当前用户。
     * @return 今日题与打卡状态。
     */
    DailyProblemTodayResponse getToday(User user);

    /**
     * 校验今日题打卡提交。
     *
     * @param user         当前用户。
     * @param submissionId Codeforces 提交 ID。
     * @return 打卡结果。
     */
    CheckInResultResponse checkIn(User user, Long submissionId);

    /**
     * 校验指定日期的每日题提交。异步任务在入队时固定日期，避免排队跨过零点后误校验下一日题目。
     */
    CheckInResultResponse checkIn(User user, Long submissionId, LocalDate targetDate);

    /**
     * 获取每日题历史。
     *
     * @param user  当前用户。
     * @param limit 历史天数限制。
     * @return 历史列表。
     */
    List<DailyProblemHistoryItem> getHistory(User user, int days);

    /**
     * 管理员重生成今日题。
     *
     * @param adminUser 管理员用户。
     * @return 最新题目视图。
     */
    ProblemView regenerateTodayByAdmin(User adminUser);

    /**
     * 管理员对指定 slot（easy/hard）进行单题重抽。
     *
     * @param adminUser 管理员用户。
     * @param date      要重抽的日期（可为 null，表示今日）。
     * @param slot      slot 名称，eg. "easy" 或 "hard"。
     * @param confirm   是否立即生效（当前实现标记历史并插入新题，confirm 用于未来策略）。
     * @return 新插入的题目视图。
     */
    ProblemView adminRedrawSlot(User adminUser, java.time.LocalDate date, String slot, boolean confirm);

    /**
     * 确保当日题位（easy/hard）已生成。供首页等场景在读取 slot 之前调用。
     */
    boolean ensureTodaySlots();

}
