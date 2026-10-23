package com.whut.training.controller;

import com.whut.training.common.ApiResponse;
import com.whut.training.context.UserContext;
import com.whut.training.domain.dto.ProblemListDetail;
import com.whut.training.domain.dto.ProblemListItemRequest;
import com.whut.training.domain.dto.ProblemListItemView;
import com.whut.training.domain.dto.ProblemListSaveRequest;
import com.whut.training.domain.dto.ProblemListSummary;
import com.whut.training.domain.entity.User;
import com.whut.training.exception.BusinessException;
import com.whut.training.service.ProblemListUseCase;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 登录用户的个人题单与全员协作共享题单接口。 */
@RestController
@RequestMapping("/api/problem-lists")
public class ProblemListController {

    private final ProblemListUseCase problemListService;

    public ProblemListController(ProblemListUseCase problemListService) {
        this.problemListService = problemListService;
    }

    @GetMapping
    public ApiResponse<List<ProblemListSummary>> list() {
        return ApiResponse.ok(problemListService.listVisible(requireCurrentUser()));
    }

    @GetMapping("/{id}")
    public ApiResponse<ProblemListDetail> get(@PathVariable Long id) {
        return ApiResponse.ok(problemListService.get(requireCurrentUser(), id));
    }

    @PostMapping
    public ApiResponse<ProblemListDetail> create(@Valid @RequestBody ProblemListSaveRequest request) {
        return ApiResponse.ok(problemListService.create(requireCurrentUser(), request));
    }

    @PatchMapping("/{id}")
    public ApiResponse<ProblemListDetail> update(
            @PathVariable Long id,
            @Valid @RequestBody ProblemListSaveRequest request
    ) {
        return ApiResponse.ok(problemListService.update(requireCurrentUser(), id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        problemListService.delete(requireCurrentUser(), id);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/items")
    public ApiResponse<ProblemListItemView> addItem(
            @PathVariable Long id,
            @Valid @RequestBody ProblemListItemRequest request
    ) {
        return ApiResponse.ok(problemListService.addItem(requireCurrentUser(), id, request));
    }

    @PatchMapping("/{id}/items/{itemId}")
    public ApiResponse<ProblemListItemView> updateItem(
            @PathVariable Long id,
            @PathVariable Long itemId,
            @Valid @RequestBody ProblemListItemRequest request
    ) {
        return ApiResponse.ok(problemListService.updateItem(requireCurrentUser(), id, itemId, request));
    }

    @DeleteMapping("/{id}/items/{itemId}")
    public ApiResponse<Void> deleteItem(@PathVariable Long id, @PathVariable Long itemId) {
        problemListService.deleteItem(requireCurrentUser(), id, itemId);
        return ApiResponse.ok();
    }

    private User requireCurrentUser() {
        User user = UserContext.getCurrentUser();
        if (user == null) throw new BusinessException(401, "unauthorized");
        return user;
    }
}
