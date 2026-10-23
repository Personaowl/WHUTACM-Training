package com.whut.training.controller;

import com.whut.training.common.ApiResponse;
import com.whut.training.context.UserContext;
import com.whut.training.domain.dto.PlatformOverview;
import com.whut.training.exception.BusinessException;
import com.whut.training.service.PlatformOverviewService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/users/{userId}/platform-overviews")
public class PlatformOverviewController {
    private final PlatformOverviewService service;
    public PlatformOverviewController(PlatformOverviewService service) { this.service = service; }

    @GetMapping
    public ApiResponse<List<PlatformOverview>> get(@PathVariable Long userId) {
        var user = UserContext.getCurrentUser();
        if (user == null) throw new BusinessException(401, "请先登录");
        if (!userId.equals(user.getId())) throw new BusinessException(403, "仅可查看本人平台账号");
        return ApiResponse.ok(service.get(user));
    }
}
