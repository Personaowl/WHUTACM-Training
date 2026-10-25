package com.whut.training.service.impl;

import com.whut.training.aspect.annotation.ServiceLog;
import com.whut.training.domain.dto.AdminCreateUserRequest;
import com.whut.training.domain.dto.UserUpdateRequest;
import com.whut.training.domain.dto.UserRegisterRequest;
import com.whut.training.domain.entity.User;
import com.whut.training.domain.enums.UserRole;
import com.whut.training.exception.BusinessException;
import com.whut.training.repository.UserRepository;
import com.whut.training.repository.AuthTokenSessionRepository;
import com.whut.training.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Comparator;
import java.util.List;

/**
 * 用户服务实现。
 *
 * <p>负责站内账号注册、管理员创建用户、
 * 用户资料更新和查询。站内用户名与 Codeforces Handle 相互独立，密码使用 BCrypt 存储。
 */
@Service
@ServiceLog
public class UserServiceImpl implements UserService {

    private static final String ADMIN_RESET_PASSWORD = "123456";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthTokenSessionRepository authTokenSessionRepository;

    public UserServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder,
                           AuthTokenSessionRepository authTokenSessionRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authTokenSessionRepository = authTokenSessionRepository;
    }

    /**
     * 注册普通用户。
     *
     * @param request 注册请求。
     * @return 新用户。
     */
    @Override
    public User register(UserRegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BusinessException(400, "username already exists");
        }

        User user = new User(
                null,
                request.getUsername(),
                normalizeNullableText(request.getEmail()),
                passwordEncoder.encode(request.getPassword()),
                UserRole.USER
        );
        user.setDisplayName(request.getDisplayName().trim());
        user.setRealName(normalizeNullableText(request.getRealName()));
        user.setCollege(normalizeNullableText(request.getCollege()));
        user.setClassName(normalizeNullableText(request.getClassName()));
        return userRepository.save(user);
    }

    /**
     * 管理员创建用户。
     *
     * @param request 创建请求。
     * @return 新用户。
     */
    @Override
    public User createByAdmin(User administrator, AdminCreateUserRequest request) {
        if (administrator == null
                || (administrator.getRole() != UserRole.ADMIN && administrator.getRole() != UserRole.SUPER_ADMIN)) {
            throw new BusinessException(403, "admin role required");
        }
        if (request.getRole() != UserRole.USER && administrator.getRole() != UserRole.SUPER_ADMIN) {
            throw new BusinessException(403, "only a super admin can create privileged accounts");
        }
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BusinessException(400, "username already exists");
        }
        User user = new User(
                null,
                request.getUsername(),
                request.getEmail(),
                passwordEncoder.encode(request.getPassword()),
                request.getRole()
        );
        return userRepository.save(user);
    }

    /**
     * 获取全部用户。
     *
     * @return 用户列表。
     */
    @Override
    public List<User> list() {
        return userRepository.findAll().stream()
                .sorted(Comparator.comparing(User::getId))
                .toList();
    }

    /**
     * 按 ID 查询用户。
     *
     * @param id 用户 ID。
     * @return 用户。
     */
    @Override
    public User getById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "user not found: " + id));
    }

    /**
     * 按用户名查询用户。
     *
     * @param username 用户名。
     * @return 用户。
     */
    @Override
    public User getByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(404, "user not found: " + username));
    }

    /**
     * 更新用户资料。
     *
     * <p>站内用户名和 Codeforces Handle 相互独立。邮箱支持置空，密码留空则不更新。
     *
     * @param userId  用户 ID。
     * @param request 更新请求。
     * @return 更新后的用户。
     */
    @Override
    public User updateProfile(Long userId, UserUpdateRequest request) {
        User user = getById(userId);
        if (request == null) {
            return user;
        }

        if (request.username() != null) {
            String nextUsername = request.username().trim();
            if (nextUsername.isEmpty()) {
                throw new BusinessException(400, "username cannot be empty");
            }
            if (!nextUsername.equals(user.getUsername())) {
                if (userRepository.existsByUsername(nextUsername)) {
                    throw new BusinessException(400, "username already exists");
                }
                user.setUsername(nextUsername);
            }
        }

        if (request.email() != null) {
            user.setEmail(normalizeNullableText(request.email()));
        }

        if (request.password() != null) {
            String password = request.password().trim();
            if (!password.isEmpty()) {
                if (password.length() < 8) {
                    throw new BusinessException(400, "password length must be at least 8");
                }
                user.setPassword(passwordEncoder.encode(password));
            }
        }

        if (request.displayName() != null) {
            user.setDisplayName(normalizeNullableText(request.displayName()));
        }

        if (request.realName() != null) {
            user.setRealName(normalizeNullableText(request.realName()));
        }

        if (request.college() != null) {
            user.setCollege(normalizeNullableText(request.college()));
        }

        if (request.className() != null) {
            user.setClassName(normalizeNullableText(request.className()));
        }

        if (request.avatar() != null) {
            String avatarUrl = validateAvatar(normalizeNullableText(request.avatar()));
            user.setAvatarUrl(avatarUrl);
            user.setAvatarCustomized(avatarUrl != null);
        }

        if (request.bio() != null) {
            user.setBio(normalizeNullableText(request.bio()));
        }

        if (request.showProblemTags() != null) {
            user.setShowProblemTags(request.showProblemTags());
        }

        return userRepository.save(user);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public void resetPasswordBySuperAdmin(User superAdmin, Long targetUserId) {
        if (superAdmin == null || superAdmin.getRole() != UserRole.SUPER_ADMIN) {
            throw new BusinessException(403, "super admin role required");
        }
        if (targetUserId == null || targetUserId.equals(superAdmin.getId())) {
            throw new BusinessException(400, "cannot reset your own password from admin management");
        }

        User target = getById(targetUserId);
        if (target.getRole() == UserRole.SUPER_ADMIN) {
            throw new BusinessException(403, "cannot reset another super admin password");
        }

        target.setPassword(passwordEncoder.encode(ADMIN_RESET_PASSWORD));
        userRepository.save(target);
        authTokenSessionRepository.deleteByUserId(targetUserId);
    }

    private String validateAvatar(String avatar) {
        if (avatar == null) {
            return null;
        }
        if (avatar.length() > 700_000) {
            throw new BusinessException(400, "avatar is too large");
        }
        if (avatar.matches("^data:image/(png|jpeg|webp);base64,[A-Za-z0-9+/=\\r\\n]+$")) {
            return avatar;
        }
        throw new BusinessException(400, "请上传 PNG、JPEG 或 WebP 图片作为头像");
    }

    /**
     * 将空白文本规范化为 null。
     *
     * @param value 原始文本。
     * @return 规范化后的文本。
     */
    private String normalizeNullableText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

}
