package com.whut.training.controller;

import com.whut.training.common.ApiResponse;
import com.whut.training.context.UserContext;
import com.whut.training.domain.dto.CodeforcesBindingResponse;
import com.whut.training.domain.dto.AtCoderBindingResponse;
import com.whut.training.domain.dto.AtCoderBindingStartRequest;
import com.whut.training.domain.dto.CodeforcesBindingStartRequest;
import com.whut.training.domain.dto.CodeforcesOverview;
import com.whut.training.domain.dto.DailyHeatmapItem;
import com.whut.training.domain.dto.PublicUserProfile;
import com.whut.training.domain.dto.UserUpdateRequest;
import com.whut.training.domain.dto.UserRegisterRequest;
import com.whut.training.domain.entity.User;
import com.whut.training.domain.enums.UserRole;
import com.whut.training.exception.BusinessException;
import com.whut.training.repository.DailyProblemRepository;
import com.whut.training.service.UserService;
import com.whut.training.service.CodeforcesProfileService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户接口。
 *
 * <p>提供注册、用户列表、按 ID 查询以及当前用户资料修改能力。当前实现中，修改自己资料需要已登录。
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final DailyProblemRepository dailyProblemRepository;
    private final CodeforcesProfileService codeforcesProfileService;

    /**
     * 创建用户控制器。
     *
     * @param userService 用户服务。
     */
    public UserController(
            UserService userService,
            DailyProblemRepository dailyProblemRepository,
            CodeforcesProfileService codeforcesProfileService
    ) {
        this.userService = userService;
        this.dailyProblemRepository = dailyProblemRepository;
        this.codeforcesProfileService = codeforcesProfileService;
    }

    /**
     * 注册新用户。
     *
     * @param request 注册请求体，包含站内用户名、邮箱和密码。
     * @return 注册成功后的用户信息。
     */
    @PostMapping("/register")
    public ApiResponse<User> register(@Valid @RequestBody UserRegisterRequest request) {
        return ApiResponse.ok(userService.register(request));
    }

    /**
     * 获取全部用户列表。
     *
     * @return 按 ID 排序的用户列表。
     */
    @GetMapping
    public ApiResponse<List<User>> list() {
        requireAdministrator();
        return ApiResponse.ok(userService.list());
    }

    /**
     * 根据 ID 查询用户。
     *
     * <p>普通用户只能查看自己的信息，管理员可查看任意用户。
     *
     * @param id 用户 ID。
     * @return 对应用户信息。
     */
    @GetMapping("/{id}")
    public ApiResponse<User> getById(@PathVariable Long id) {
        User currentUser = UserContext.getCurrentUser();
        if (currentUser == null) {
            throw new BusinessException(401, "unauthorized");
        }
        if (!currentUser.getId().equals(id) && !isAdministrator(currentUser)) {
            throw new BusinessException(403, "forbidden");
        }
        return ApiResponse.ok(userService.getById(id));
    }

    /**
     * 获取成员的安全公开资料。任意已登录成员均可访问。
     */
    @GetMapping("/{id}/public-profile")
    public ApiResponse<PublicUserProfile> getPublicProfile(@PathVariable Long id) {
        requireAuthenticated();
        return ApiResponse.ok(PublicUserProfile.from(userService.getById(id)));
    }

    /**
     * 获取成员的 Codeforces 统计快照。任意已登录成员均可访问。
     */
    @GetMapping("/{id}/codeforces-overview")
    public ApiResponse<CodeforcesOverview> getCodeforcesOverview(@PathVariable Long id) {
        requireAuthenticated();
        return ApiResponse.ok(codeforcesProfileService.getOverview(id));
    }

    /**
     * 强制刷新 Codeforces 统计。仅本人或管理员可执行。
     */
    @PostMapping("/{id}/codeforces-overview/refresh")
    public ApiResponse<CodeforcesOverview> refreshCodeforcesOverview(@PathVariable Long id) {
        User currentUser = requireAuthenticated();
        boolean administrator = currentUser.getRole() == com.whut.training.domain.enums.UserRole.ADMIN
                || currentUser.getRole() == com.whut.training.domain.enums.UserRole.SUPER_ADMIN;
        if (!currentUser.getId().equals(id) && !administrator) {
            throw new BusinessException(403, "forbidden");
        }
        return ApiResponse.ok(codeforcesProfileService.refresh(id));
    }

    /**
     * 修改当前登录用户资料。
     *
     * <p>该接口允许修改站内用户名、邮箱和密码；Codeforces 账号通过独立接口绑定。
     *
     * @param request 资料更新请求体，允许为空。
     * @return 更新后的用户信息。
     */
    @PatchMapping("/{id}")
    public ApiResponse<User> updateById(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) UserUpdateRequest request
    ) {
        User currentUser = UserContext.getCurrentUser();
        if (currentUser == null) {
            throw new BusinessException(401, "unauthorized");
        }
        // 修改他人账号（包含密码）仅允许超级管理员，避免普通管理员接管高权限账号。
        if (!currentUser.getId().equals(id) && currentUser.getRole() != UserRole.SUPER_ADMIN) {
            throw new BusinessException(403, "forbidden");
        }
        return ApiResponse.ok(userService.updateProfile(id, request));
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
        return ApiResponse.ok(userService.startCodeforcesBinding(id, request));
    }

    /**
     * 查询验证提交并完成 Codeforces 账号绑定。
     */
    @PostMapping("/{id}/codeforces-binding/finish")
    public ApiResponse<User> finishCodeforcesBinding(@PathVariable Long id) {
        requireSelf(id);
        return ApiResponse.ok(userService.finishCodeforcesBinding(id));
    }

    @PostMapping("/{id}/atcoder-binding/start")
    public ApiResponse<AtCoderBindingResponse> startAtCoderBinding(
            @PathVariable Long id,
            @Valid @RequestBody AtCoderBindingStartRequest request
    ) {
        requireSelf(id);
        return ApiResponse.ok(userService.startAtCoderBinding(id, request));
    }

    @PostMapping("/{id}/atcoder-binding/finish")
    public ApiResponse<User> finishAtCoderBinding(@PathVariable Long id) {
        requireSelf(id);
        return ApiResponse.ok(userService.finishAtCoderBinding(id));
    }

    /**
     * 获取用户每日提交热图数据。
     *
     * @param id   用户 ID。
     * @param days 查询天数，默认 180。
     * @return 热图数据列表。
     */
    @GetMapping("/{id}/daily-heatmap")
    public ApiResponse<List<DailyHeatmapItem>> dailyHeatmap(@PathVariable Long id, @RequestParam(defaultValue = "180") int days) {
        User currentUser = UserContext.getCurrentUser();
        if (currentUser == null) {
            throw new BusinessException(401, "unauthorized");
        }
        if (!currentUser.getId().equals(id) && !isAdministrator(currentUser)) {
            throw new BusinessException(403, "forbidden");
        }
        if (days < 1) days = 180;
        if (days > 365) days = 365;
        return ApiResponse.ok(dailyProblemRepository.findHeatmapForUser(id, days));
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

    private User requireAuthenticated() {
        User currentUser = UserContext.getCurrentUser();
        if (currentUser == null) {
            throw new BusinessException(401, "unauthorized");
        }
        return currentUser;
    }

    private void requireAdministrator() {
        User currentUser = requireAuthenticated();
        if (!isAdministrator(currentUser)) {
            throw new BusinessException(403, "admin role required");
        }
    }

    private boolean isAdministrator(User user) {
        return user.getRole() == UserRole.ADMIN || user.getRole() == UserRole.SUPER_ADMIN;
    }
}
