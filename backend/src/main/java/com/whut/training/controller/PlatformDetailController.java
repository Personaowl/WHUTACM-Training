package com.whut.training.controller;

import com.whut.training.common.ApiResponse;
import com.whut.training.context.UserContext;
import com.whut.training.domain.dto.PlatformDetails;
import com.whut.training.domain.entity.User;
import com.whut.training.exception.BusinessException;
import com.whut.training.service.PlatformDetailService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/users/{userId}/platform-details")
public class PlatformDetailController {
    private final PlatformDetailService service;
    public PlatformDetailController(PlatformDetailService service) { this.service = service; }
    @GetMapping public ApiResponse<List<PlatformDetails>> all(@PathVariable Long userId) { return ApiResponse.ok(service.all(owner(userId), false)); }
    @PostMapping("/refresh") public ApiResponse<List<PlatformDetails>> refresh(@PathVariable Long userId) { return ApiResponse.ok(service.all(owner(userId), true)); }
    @GetMapping("/{platform}") public ApiResponse<PlatformDetails> get(@PathVariable Long userId, @PathVariable String platform) { return ApiResponse.ok(service.read(owner(userId), platform, false)); }
    @PostMapping("/{platform}/refresh") public ApiResponse<PlatformDetails> refreshOne(@PathVariable Long userId, @PathVariable String platform) { return ApiResponse.ok(service.read(owner(userId), platform, true)); }
    private User owner(Long userId) {
        var user = UserContext.getCurrentUser();
        if (user == null) throw new BusinessException(401, "请先登录");
        if (!userId.equals(user.getId())) throw new BusinessException(403, "仅可查看本人平台记录");
        return user;
    }
}
