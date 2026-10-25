package com.whut.training.controller;

import com.whut.training.common.ApiResponse;
import com.whut.training.context.UserContext;
import com.whut.training.domain.dto.PracticeDrawRequest;
import com.whut.training.domain.dto.PracticeDrawResponse;
import com.whut.training.domain.dto.PracticeHistoryItem;
import com.whut.training.domain.entity.User;
import com.whut.training.exception.BusinessException;
import com.whut.training.service.PracticeService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** Authenticated practice endpoints; existing paths and response envelopes are retained. */
@RestController
@RequestMapping("/api")
public class PracticeController {
    private final PracticeService practiceService;
    public PracticeController(PracticeService practiceService) {
        this.practiceService = practiceService;
    }

    /**
     * 自主抽题。
     *
     * @param request 可选抽题条件，主要是难度范围。
     * @return 抽到的练习题。
     */
    @PostMapping("/practice/draw")
    public ApiResponse<PracticeDrawResponse> draw(@RequestBody(required = false) PracticeDrawRequest request) {
        Integer minRating = request == null ? null : request.minRating();
        Integer maxRating = request == null ? null : request.maxRating();
        List<String> tags = request == null ? List.of() : request.tags();
        return ApiResponse.ok(practiceService.drawPracticeProblem(requireCurrentUser(), minRating, maxRating, tags));
    }

    /**
     * 获取自主练习可选标签。
     */
    @GetMapping("/practice/tags")
    public ApiResponse<List<String>> practiceTags() {
        requireCurrentUser();
        return ApiResponse.ok(practiceService.getAvailablePracticeTags());
    }

    /**
     * 获取自主练习历史。
     *
     * @param limit 最大条数，默认 20。
     * @return 练习历史列表。
     */
    @GetMapping("/practice/history")
    public ApiResponse<List<PracticeHistoryItem>> practiceHistory(@RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(practiceService.getPracticeHistory(requireCurrentUser(), limit));
    }

    /**
     * 删除一条自主练习记录。
     *
     * @param drawId 抽题记录 ID。
     * @return 操作结果。
     */
    @DeleteMapping("/practice/{drawId}")
    public ApiResponse<Void> deletePracticeDraw(@PathVariable Long drawId) {
        boolean deleted = practiceService.deletePracticeDraw(requireCurrentUser(), drawId);
        if (!deleted) {
            throw new BusinessException(404, "practice draw not found");
        }
        return ApiResponse.ok(null);
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
