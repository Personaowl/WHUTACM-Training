package com.whut.training.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpsolvePlanRequest(
        @NotBlank(message = "请输入题目链接") @Size(max = 1000) String link,
        @Size(max = 255) String title,
        @Size(max = 1000) String note
) { }
