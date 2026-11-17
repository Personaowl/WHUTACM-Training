package com.whut.training.controller;

import com.whut.training.common.ApiResponse;
import com.whut.training.context.UserContext;
import com.whut.training.domain.dto.ProblemView;
import com.whut.training.domain.entity.User;
import com.whut.training.exception.BusinessException;
import com.whut.training.service.DailyProblemService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员每日题接口。
 *
 * <p>用于管理员手动重生成当天的每日题；真正的业务权限也在服务层再次校验。
 */
@RestController
@RequestMapping("/api/admin/daily-problem")
public class AdminDailyProblemController {

    private final DailyProblemService dailyProblemService;

    /**
     * 创建管理员每日题控制器。
     *
     * @param dailyProblemService 每日题业务服务。
     */
    public AdminDailyProblemController(DailyProblemService dailyProblemService) {
        this.dailyProblemService = dailyProblemService;
    }

    /**
     * 重新生成今日题。
     *
     * @return 最新的今日题视图。
     */
    @PostMapping("/regenerate")
    public ApiResponse<ProblemView> regenerateToday() {
        User user = UserContext.getCurrentUser();
        if (user == null) {
            throw new BusinessException(401, "unauthorized");
        }
        return ApiResponse.ok(dailyProblemService.regenerateTodayByAdmin(user));
    }

    /**
     * 管理员对指定 slot（easy/mid/hard）进行重抽。
     * 示例：POST /api/admin/daily-problem/redraw?slot=easy&confirm=false
     */
    @PostMapping("/redraw")
    public ApiResponse<ProblemView> redrawSlot(@RequestParam(name = "date", required = false) String dateStr,
                                               @RequestParam(name = "slot") String slot,
                                               @RequestParam(name = "confirm", defaultValue = "false") boolean confirm) {
        User user = UserContext.getCurrentUser();
        if (user == null) {
            throw new BusinessException(401, "unauthorized");
        }
        java.time.LocalDate date = null;
        if (dateStr != null && !dateStr.isBlank()) {
            date = java.time.LocalDate.parse(dateStr);
        }
        return ApiResponse.ok(dailyProblemService.adminRedrawSlot(user, date, slot, confirm));
    }
}
