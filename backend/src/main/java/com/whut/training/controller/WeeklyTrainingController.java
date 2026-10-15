package com.whut.training.controller;

import com.whut.training.common.ApiResponse;
import com.whut.training.context.UserContext;
import com.whut.training.domain.dto.WeeklyTrainingReport;
import com.whut.training.service.WeeklyTrainingService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class WeeklyTrainingController {
    private final WeeklyTrainingService service;

    public WeeklyTrainingController(WeeklyTrainingService service) { this.service = service; }

    @GetMapping("/weekly-training/me")
    public ApiResponse<WeeklyTrainingReport> personal(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week) {
        return ApiResponse.ok(service.personal(UserContext.getCurrentUser(), week));
    }

    @PostMapping("/weekly-training/me/explanation")
    public ApiResponse<WeeklyTrainingReport> explain(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week,
            @Valid @RequestBody WeeklyTrainingReport.ExplanationRequest request) {
        return ApiResponse.ok(service.explain(UserContext.getCurrentUser(), week, request));
    }

    @GetMapping("/admin/weekly-training")
    public ApiResponse<WeeklyTrainingReport> dashboard(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week) {
        return ApiResponse.ok(service.dashboard(UserContext.getCurrentUser(), week));
    }

    @PostMapping("/admin/weekly-training/{userId}/review")
    public ApiResponse<WeeklyTrainingReport> review(@PathVariable Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week,
            @Valid @RequestBody WeeklyTrainingReport.ReviewRequest request) {
        return ApiResponse.ok(service.review(UserContext.getCurrentUser(), week, userId, request));
    }
}
