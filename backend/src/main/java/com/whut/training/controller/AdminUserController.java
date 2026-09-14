package com.whut.training.controller;

import com.whut.training.common.ApiResponse;
import com.whut.training.context.UserContext;
import com.whut.training.domain.dto.AdminCreateUserRequest;
import com.whut.training.domain.entity.User;
import com.whut.training.domain.enums.MemberType;
import com.whut.training.domain.enums.UserRole;
import com.whut.training.exception.BusinessException;
import com.whut.training.repository.UserRepository;
import com.whut.training.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 管理员用户接口。
 */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final UserService userService;
    private final UserRepository userRepository;

    public AdminUserController(UserService userService, UserRepository userRepository) {
        this.userService = userService;
        this.userRepository = userRepository;
    }

    /**
     * 管理员创建用户。
     */
    @PostMapping
    public ApiResponse<User> createByAdmin(@Valid @RequestBody AdminCreateUserRequest request) {
        User currentUser = UserContext.getCurrentUser();
        if (currentUser == null || (currentUser.getRole() != UserRole.ADMIN && currentUser.getRole() != UserRole.SUPER_ADMIN)) {
            throw new BusinessException(403, "admin role required");
        }
        return ApiResponse.ok(userService.createByAdmin(currentUser, request));
    }

    /**
     * 超管理员修改用户角色。
     */
    @PutMapping("/{id}/role")
    public ApiResponse<User> changeRole(@PathVariable Long id, @RequestBody Map<String, String> body) {
        User currentUser = UserContext.getCurrentUser();
        if (currentUser == null || currentUser.getRole() != UserRole.SUPER_ADMIN) {
            throw new BusinessException(403, "super admin role required");
        }
        String newRole = body.get("role");
        if (newRole == null || newRole.isBlank()) {
            throw new BusinessException(400, "role is required");
        }
        UserRole targetRole;
        try {
            targetRole = UserRole.valueOf(newRole.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(400, "invalid role: " + newRole);
        }
        User target = userService.getById(id);
        if (target.getRole() == UserRole.SUPER_ADMIN
                && targetRole != UserRole.SUPER_ADMIN
                && userRepository.countByRole(UserRole.SUPER_ADMIN) <= 1) {
            throw new BusinessException(409, "cannot demote the last super admin");
        }
        String fromRole = target.getRole() == null ? null : target.getRole().name();
        userRepository.updateRole(id, targetRole.name());
        userRepository.insertRoleChangeLog(id, currentUser.getId(), fromRole, targetRole.name());
        return ApiResponse.ok(userService.getById(id));
    }

    /**
     * 管理员修改训练队成员类型。
     */
    @PutMapping("/{id}/member-type")
    public ApiResponse<User> changeMemberType(@PathVariable Long id, @RequestBody Map<String, String> body) {
        User currentUser = UserContext.getCurrentUser();
        if (currentUser == null
                || (currentUser.getRole() != UserRole.ADMIN && currentUser.getRole() != UserRole.SUPER_ADMIN)) {
            throw new BusinessException(403, "admin role required");
        }

        String rawMemberType = body.get("memberType");
        if (rawMemberType == null || rawMemberType.isBlank()) {
            throw new BusinessException(400, "memberType is required");
        }

        MemberType memberType;
        try {
            memberType = MemberType.valueOf(rawMemberType.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(400, "invalid member type: " + rawMemberType);
        }

        userService.getById(id);
        userRepository.updateMemberType(id, memberType);
        return ApiResponse.ok(userService.getById(id));
    }

    /**
     * 超级管理员重置其他用户密码为 123456，同时注销该用户的全部既有会话。
     */
    @PostMapping("/{id}/reset-password")
    public ApiResponse<Map<String, String>> resetPassword(@PathVariable Long id) {
        User currentUser = UserContext.getCurrentUser();
        if (currentUser == null || currentUser.getRole() != UserRole.SUPER_ADMIN) {
            throw new BusinessException(403, "super admin role required");
        }
        userService.resetPasswordBySuperAdmin(currentUser, id);
        return ApiResponse.ok(Map.of("message", "password reset to 123456"));
    }
}
