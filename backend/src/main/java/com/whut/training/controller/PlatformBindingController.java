package com.whut.training.controller;

import com.whut.training.common.ApiResponse;
import com.whut.training.context.UserContext;
import com.whut.training.exception.BusinessException;
import com.whut.training.service.PlatformBindingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users/{userId}/platform-bindings/{platform}")
public class PlatformBindingController {
    private final PlatformBindingService service;
    public PlatformBindingController(PlatformBindingService service) { this.service=service; }
    public record StartRequest(@NotBlank @Pattern(regexp="[1-9][0-9]{0,17}") String uid) {}
    @GetMapping public ApiResponse<PlatformBindingService.View> get(@PathVariable Long userId, @PathVariable String platform) {
        self(userId); return ApiResponse.ok(service.get(userId,platform));
    }
    @PostMapping("/start") public ApiResponse<PlatformBindingService.View> start(@PathVariable Long userId, @PathVariable String platform,
                                                                              @Valid @RequestBody StartRequest request) {
        self(userId); return ApiResponse.ok(service.start(userId,platform,request.uid()));
    }
    @PostMapping("/finish") public ApiResponse<PlatformBindingService.View> finish(@PathVariable Long userId, @PathVariable String platform) {
        self(userId); return ApiResponse.ok(service.finish(userId,platform));
    }
    private void self(Long userId) {
        var user=UserContext.getCurrentUser();
        if(user==null) throw new BusinessException(401,"请先登录");
        if(!userId.equals(user.getId())) throw new BusinessException(403,"仅可操作本人绑定");
    }
}
