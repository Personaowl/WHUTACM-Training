package com.whut.training.service;

import com.whut.training.domain.dto.AdminCreateUserRequest;
import com.whut.training.domain.dto.AtCoderBindingResponse;
import com.whut.training.domain.dto.AtCoderBindingStartRequest;
import com.whut.training.domain.dto.CodeforcesBindingResponse;
import com.whut.training.domain.dto.CodeforcesBindingStartRequest;
import com.whut.training.domain.dto.UserUpdateRequest;
import com.whut.training.domain.dto.UserRegisterRequest;
import com.whut.training.domain.entity.User;

import java.util.List;

/**
 * 用户服务。
 *
 * <p>负责注册、Codeforces 账号绑定、管理员创建用户、资料更新以及用户查询。
 */
public interface UserService {
    /**
     * 注册普通用户。
     *
     * @param request 注册请求。
     * @return 新用户。
     */
    User register(UserRegisterRequest request);

    /**
     * 管理员创建用户。
     *
     * @param request 创建请求。
     * @return 新用户。
     */
    User createByAdmin(User administrator, AdminCreateUserRequest request);

    /**
     * 由超级管理员将其他用户密码重置为平台统一临时密码。
     */
    void resetPasswordBySuperAdmin(User superAdmin, Long targetUserId);

    /**
     * 获取全部用户。
     *
     * @return 用户列表。
     */
    List<User> list();

    /**
     * 按 ID 查询用户。
     *
     * @param id 用户 ID。
     * @return 用户。
     */
    User getById(Long id);

    /**
     * 按用户名查询用户。
     *
     * @param username 站内用户名。
     * @return 用户。
     */
    User getByUsername(String username);

    /**
     * 更新指定用户资料。
     *
     * @param userId  用户 ID。
     * @param request 更新请求。
     * @return 更新后的用户。
     */
    User updateProfile(Long userId, UserUpdateRequest request);

    /**
     * 开始 Codeforces 账号所有权验证。
     */
    CodeforcesBindingResponse startCodeforcesBinding(Long userId, CodeforcesBindingStartRequest request);

    /**
     * 检查验证提交并完成 Codeforces 账号绑定。
     */
    User finishCodeforcesBinding(Long userId);

    AtCoderBindingResponse startAtCoderBinding(Long userId, AtCoderBindingStartRequest request);

    User finishAtCoderBinding(Long userId);
}
