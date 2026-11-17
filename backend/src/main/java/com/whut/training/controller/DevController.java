package com.whut.training.controller;

import com.whut.training.common.AdjustableTimeProvider;
import com.whut.training.common.ApiResponse;
import com.whut.training.common.TimeProvider;
import com.whut.training.context.UserContext;
import com.whut.training.domain.entity.User;
import com.whut.training.repository.DailyProblemRepository;
import com.whut.training.repository.UserRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 开发调试接口（仅 dev 环境可用）。
 *
 * <p>提供时间调节与强制打卡能力，方便开发阶段测试定时任务和日期相关逻辑。
 */
@RestController
@RequestMapping("/api/dev")
@Profile("dev")
public class DevController {

    private final AdjustableTimeProvider timeProvider;
    private final DailyProblemRepository dailyProblemRepository;
    private final UserRepository userRepository;

    public DevController(TimeProvider timeProvider,
                         DailyProblemRepository dailyProblemRepository,
                         UserRepository userRepository) {
        if (!(timeProvider instanceof AdjustableTimeProvider)) {
            throw new IllegalStateException("DevController requires AdjustableTimeProvider");
        }
        this.timeProvider = (AdjustableTimeProvider) timeProvider;
        this.dailyProblemRepository = dailyProblemRepository;
        this.userRepository = userRepository;
    }

    /** 检查 dev 模式是否激活（前端据此决定是否显示调试面板） */
    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        return ApiResponse.ok(Map.of(
                "active", true,
                "profile", "dev"
        ));
    }

    /** 获取当前开发时间设置 */
    @GetMapping("/time")
    public ApiResponse<Map<String, Object>> getTime() {
        LocalDateTime fixed = timeProvider.getFixedTime();
        return ApiResponse.ok(Map.of(
                "fixed", fixed != null,
                "dateTime", fixed != null ? fixed.toString() : LocalDateTime.now().toString(),
                "today", timeProvider.today().toString()
        ));
    }

    /** 设置开发环境时间 */
    @PostMapping("/time")
    public ApiResponse<Map<String, Object>> setTime(@RequestBody Map<String, String> body) {
        String dateTimeStr = body.get("dateTime");
        if (dateTimeStr == null || dateTimeStr.isBlank()) {
            return ApiResponse.fail(400, "dateTime is required");
        }
        try {
            LocalDateTime dateTime = LocalDateTime.parse(dateTimeStr);
            timeProvider.setFixedTime(dateTime);
            return ApiResponse.ok(Map.of(
                    "fixed", true,
                    "dateTime", dateTime.toString(),
                    "today", timeProvider.today().toString()
            ));
        } catch (Exception e) {
            return ApiResponse.fail(400, "invalid dateTime format, use ISO format: 2026-05-25T10:00:00");
        }
    }

    /** 重置开发环境时间为系统时间 */
    @PostMapping("/time/reset")
    public ApiResponse<Map<String, Object>> resetTime() {
        timeProvider.reset();
        return ApiResponse.ok(Map.of(
                "fixed", false,
                "dateTime", LocalDateTime.now().toString(),
                "today", timeProvider.today().toString()
        ));
    }

    /**
     * 强制打卡 —— 跳过 Codeforces 提交校验，直接将指定日期的题目标记为已完成。
     *
     * <p>请求体：{ "date": "2026-05-25", "slot": "easy|mid|hard", "userId": 1 }
     * 不传 userId 则使用当前登录用户；不传 slot 则按 Easy、Mid、Hard 顺序寻找有效题位。
     */
    @PostMapping("/check-in")
    public ApiResponse<Map<String, Object>> forceCheckIn(@RequestBody Map<String, String> body) {
        User currentUser = UserContext.getCurrentUser();
        if (currentUser == null) {
            return ApiResponse.fail(401, "unauthorized");
        }

        // 确定目标用户
        Long targetUserId = currentUser.getId();
        String userIdStr = body.get("userId");
        if (userIdStr != null && !userIdStr.isBlank()) {
            targetUserId = Long.valueOf(userIdStr);
        }

        // 确定日期
        String dateStr = body.get("date");
        LocalDate date;
        if (dateStr != null && !dateStr.isBlank()) {
            date = LocalDate.parse(dateStr);
        } else {
            date = timeProvider.today();
        }

        // 找到该日期的题位
        String slot = body.get("slot");
        var slots = dailyProblemRepository.findDailySlotsByDate(date);

        com.whut.training.domain.entity.DailyProblemSlot matchedSlot = null;
        if (slots != null && !slots.isEmpty()) {
            if (slot != null && !slot.isBlank()) {
                String lowerSlot = slot.toLowerCase();
                matchedSlot = slots.stream()
                        .filter(s -> !s.isRedrawn() && s.slot().equalsIgnoreCase(lowerSlot))
                        .findFirst().orElse(null);
            }
            if (matchedSlot == null) {
                // try first non-redrawn slot
                matchedSlot = slots.stream()
                        .filter(s -> !s.isRedrawn())
                        .findFirst().orElse(null);
            }
        }

        if (matchedSlot == null) {
            return ApiResponse.fail(404, "daily slot not found");
        }

        int score = matchedSlot.rating() == null ? 0 : matchedSlot.rating();
        String matchedSlotName = matchedSlot.slot();

        // 检查是否已打卡
        var existingSlot = dailyProblemRepository.findUserDailySlotStatus(targetUserId, date, matchedSlot.problemKey());
        int slotScore = existingSlot
                .map(com.whut.training.domain.entity.UserDailyStatus::score)
                .map(oldScore -> Math.max(oldScore, score))
                .orElse(score);
        dailyProblemRepository.saveUserDailySlotStatus(
                targetUserId,
                date,
                matchedSlot.slot(),
                matchedSlot.problemKey(),
                0L,
                "OK (dev)",
                slotScore
        );

        var existingDay = dailyProblemRepository.findUserDailyStatus(targetUserId, date);
        int oldDayScore = existingDay
                .map(com.whut.training.domain.entity.UserDailyStatus::score)
                .orElse(0);
        int nextDayScore = Math.max(
                oldDayScore,
                dailyProblemRepository.maxUserDailySlotScore(targetUserId, date)
        );
        dailyProblemRepository.upsertUserDailyStatus(targetUserId, date, 0L, "OK (dev)", nextDayScore);
        if (nextDayScore > oldDayScore) {
            userRepository.incrementTotalPoints(targetUserId, nextDayScore - oldDayScore);
        }

        return ApiResponse.ok(Map.of(
                "date", date.toString(),
                "slot", matchedSlotName,
                "score", slotScore,
                "dailyScore", nextDayScore,
                "message", "force check-in success"
        ));
    }
}
