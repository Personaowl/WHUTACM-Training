package com.whut.training.domain.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record WeeklyTrainingReport(String weekStart, String weekEnd, String today,
                                   List<Member> members) {
    public record Member(Long userId, String username, String displayName, String realName,
                         String college, String className, String avatarUrl, String memberType,
                         String automaticStatus, String effectiveStatus, String manualStatus,
                         int dailyCompletedDays, List<Day> days, String contestStatus,
                         List<Contest> contests, Explanation explanation, long version,
                         boolean canExplain, List<Audit> history) {}

    public record Day(String date, boolean completed, boolean easyCompleted, boolean hardCompleted,
                      int score, String status) {}
    public record Contest(String contestId, String name, String url, long startTimeSeconds,
                          String status, Integer acCount, String checkedAt) {}
    public record Explanation(String content, String submittedAt, String reviewStatus,
                              String reviewNote, String reviewer, String reviewedAt) {}
    public record Audit(Long id, String action, String actor, String message, String resultStatus,
                        String createdAt) {}

    public record ExplanationRequest(@NotBlank @Size(max = 2000) String content,
                                     @NotNull @Min(0) Long version) {}
    public record ReviewRequest(
            @NotBlank @Pattern(regexp = "APPROVE|REJECT|ADJUST") String action,
            @Pattern(regexp = "AUTO|COMPLETED|EXEMPT|INCOMPLETE") String status,
            @NotBlank @Size(max = 2000) String note,
            @NotNull @Min(0) Long version) {}
}
