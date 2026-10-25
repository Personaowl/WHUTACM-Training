package com.whut.training.controller;

import com.whut.training.common.ApiResponse;
import com.whut.training.context.UserContext;
import com.whut.training.domain.dto.AtCoderBindingResponse;
import com.whut.training.domain.dto.AtCoderBindingStartRequest;
import com.whut.training.domain.dto.CodeforcesBindingResponse;
import com.whut.training.domain.dto.CodeforcesBindingStartRequest;
import com.whut.training.domain.entity.User;
import com.whut.training.exception.BusinessException;
import com.whut.training.service.binding.ContestAccountBindingService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class ContestAccountBindingController {
    private final ContestAccountBindingService accountBindingService;

    public ContestAccountBindingController(ContestAccountBindingService accountBindingService) {
        this.accountBindingService = accountBindingService;
    }

    /**
     * 为当前登录用户开始 Codeforces 账号所有权验证。
     */
    @PostMapping("/{id}/codeforces-binding/start")
    public ApiResponse<CodeforcesBindingResponse> startCodeforcesBinding(
            @PathVariable Long id,
            @Valid @RequestBody CodeforcesBindingStartRequest request
    ) {
        requireSelf(id);
        return ApiResponse.ok(accountBindingService.startCodeforcesBinding(id, request));
    }

    /**
     * 查询验证提交并完成 Codeforces 账号绑定。
     */
    @PostMapping("/{id}/codeforces-binding/finish")
    public ApiResponse<User> finishCodeforcesBinding(@PathVariable Long id) {
        requireSelf(id);
        return ApiResponse.ok(accountBindingService.finishCodeforcesBinding(id));
    }

    @PostMapping("/{id}/atcoder-binding/start")
    public ApiResponse<AtCoderBindingResponse> startAtCoderBinding(
            @PathVariable Long id,
            @Valid @RequestBody AtCoderBindingStartRequest request
    ) {
        requireSelf(id);
        return ApiResponse.ok(accountBindingService.startAtCoderBinding(id, request));
    }

    @PostMapping("/{id}/atcoder-binding/finish")
    public ApiResponse<User> finishAtCoderBinding(@PathVariable Long id) {
        requireSelf(id);
        return ApiResponse.ok(accountBindingService.finishAtCoderBinding(id));
    }

    private void requireSelf(Long id) {
        User currentUser = UserContext.getCurrentUser();
        if (currentUser == null) {
            throw new BusinessException(401, "unauthorized");
        }
        if (!currentUser.getId().equals(id)) {
            throw new BusinessException(403, "you can only operate your own account");
        }
    }

}
