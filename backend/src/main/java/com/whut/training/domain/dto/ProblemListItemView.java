package com.whut.training.domain.dto;

/** 题单中的单道题目快照。 */
public record ProblemListItemView(
        Long id,
        Long listId,
        String title,
        String link,
        String note,
        String problemKey,
        Integer rating,
        String tags,
        int sortOrder,
        Long addedBy,
        String createdAt
) {
}
