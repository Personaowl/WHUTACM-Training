package com.whut.training.domain.dto;

public record UpsolvePlanView(Long id, String title, String link, String note,
                              boolean completed, String createdAt, String completedAt) { }
