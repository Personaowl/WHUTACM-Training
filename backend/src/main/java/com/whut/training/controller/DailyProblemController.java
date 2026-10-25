package com.whut.training.controller;

import com.whut.training.common.ApiResponse;
import com.whut.training.context.UserContext;
import com.whut.training.domain.dto.*;
import com.whut.training.domain.entity.User;
import com.whut.training.exception.BusinessException;
import com.whut.training.service.DailyProblemService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 每日一题接口。
 *
 * <p>包括今日题获取、每日打卡、历史查询。所有接口都要求已登录。
 */
@RestController
@RequestMapping("/api")
public class DailyProblemController {

    private final DailyProblemService dailyProblemService;
    private final com.whut.training.service.DailyCheckInJobService dailyCheckInJobService;

    /**
     * 创建每日题控制器。
     *
     * @param dailyProblemService 每日题业务服务。
     */
    public DailyProblemController(
            DailyProblemService dailyProblemService,
            com.whut.training.service.DailyCheckInJobService dailyCheckInJobService
    ) {
        this.dailyProblemService = dailyProblemService;
        this.dailyCheckInJobService = dailyCheckInJobService;
    }

    /**
     * 获取今日题。
     *
     * @return 今日题与当前用户打卡状态。
     */
    @GetMapping("/daily-problem/today")
    public ApiResponse<DailyProblemTodayResponse> getToday() {
        return ApiResponse.ok(dailyProblemService.getToday(requireCurrentUser()));
    }

    /**
     * 今日题打卡。
     *
     * @param request 打卡请求体，包含 submissionId。
     * @return 打卡结果。
     */
    @PostMapping("/daily-problem/check-in")
    public ApiResponse<DailyCheckInJobResponse> checkIn(@Valid @RequestBody DailyProblemCheckInRequest request) {
        return ApiResponse.ok(dailyCheckInJobService.submit(requireCurrentUser(), request.submissionId()));
    }

    /** Reads an asynchronous check-in without holding a servlet thread during the CF rate-limit wait. */
    @GetMapping("/daily-problem/check-in/{jobId}")
    public ApiResponse<DailyCheckInJobResponse> checkInStatus(@PathVariable String jobId) {
        User user = requireCurrentUser();
        return ApiResponse.ok(dailyCheckInJobService.get(jobId, user.getId()));
    }

    /**
     * 查询每日题历史。
     *
     * @param days 查询天数，0 或不传表示全部记录。
     * @return 历史列表。
     */
    @GetMapping("/daily-problem/history")
    public ApiResponse<List<DailyProblemHistoryItem>> history(@RequestParam(defaultValue = "0") int days) {
        return ApiResponse.ok(dailyProblemService.getHistory(requireCurrentUser(), days));
    }

    /**
     * 读取当前登录用户，未登录则抛出 401。
     *
     * @return 当前用户。
     */
    private User requireCurrentUser() {
        User user = UserContext.getCurrentUser();
        if (user == null) {
            throw new BusinessException(401, "unauthorized");
        }
        return user;
    }
}
