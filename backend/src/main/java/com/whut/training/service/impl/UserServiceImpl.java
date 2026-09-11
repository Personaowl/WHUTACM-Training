package com.whut.training.service.impl;

import com.whut.training.aspect.annotation.ServiceLog;
import com.whut.training.common.TimeProvider;
import com.whut.training.domain.dto.AdminCreateUserRequest;
import com.whut.training.domain.dto.AtCoderBindingResponse;
import com.whut.training.domain.dto.AtCoderBindingStartRequest;
import com.whut.training.domain.dto.CodeforcesBindingResponse;
import com.whut.training.domain.dto.CodeforcesBindingStartRequest;
import com.whut.training.domain.dto.UserUpdateRequest;
import com.whut.training.domain.dto.UserRegisterRequest;
import com.whut.training.domain.entity.User;
import com.whut.training.domain.enums.UserRole;
import com.whut.training.exception.BusinessException;
import com.whut.training.repository.UserRepository;
import com.whut.training.service.CodeforcesApiService;
import com.whut.training.service.AtCoderApiService;
import com.whut.training.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Locale;
import java.util.UUID;

/**
 * 用户服务实现。
 *
 * <p>负责站内账号注册、Codeforces 所有权验证绑定、管理员创建用户、
 * 用户资料更新和查询。站内用户名与 Codeforces Handle 相互独立，密码使用 BCrypt 存储。
 */
@Service
@ServiceLog
public class UserServiceImpl implements UserService {

    private static final long CODEFORCES_BINDING_TTL_SECONDS = 120;
    private static final String CODEFORCES_VERIFICATION_URL = "https://codeforces.com/contest/1/problem/A";
    private static final long ATCODER_BINDING_TTL_SECONDS = 600;

    private final UserRepository userRepository;
    private final CodeforcesApiService codeforcesApiService;
    private final AtCoderApiService atCoderApiService;
    private final TimeProvider timeProvider;
    private final PasswordEncoder passwordEncoder;

    /**
     * 创建用户服务实现。
     *
     * @param userRepository      用户仓储。
     * @param codeforcesApiService Codeforces API 服务。
     */
    public UserServiceImpl(UserRepository userRepository, CodeforcesApiService codeforcesApiService,
                           AtCoderApiService atCoderApiService, TimeProvider timeProvider,
                           PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.codeforcesApiService = codeforcesApiService;
        this.atCoderApiService = atCoderApiService;
        this.timeProvider = timeProvider;
        this.passwordEncoder = passwordEncoder;
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
        try {
            URI uri = URI.create(avatar);
            if ("https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null) {
                return avatar;
            }
        } catch (IllegalArgumentException ignored) {
            // converted to a stable client error below
        }
        throw new BusinessException(400, "avatar must be a HTTPS URL or a PNG/JPEG/WebP data image");
    }

    @Override
    public CodeforcesBindingResponse startCodeforcesBinding(Long userId, CodeforcesBindingStartRequest request) {
        User user = getById(userId);
        String handle = request.getHandle().trim();

        Optional<User> boundUser = userRepository.findByCodeforcesHandle(handle);
        if (boundUser.isPresent() && !boundUser.get().getId().equals(userId)) {
            throw new BusinessException(400, "this Codeforces handle is already bound");
        }

        if (codeforcesApiService.getUserInfo(handle).isEmpty()) {
            throw new BusinessException(400, "Codeforces handle does not exist or Codeforces API is unavailable");
        }

        long startedAtSeconds = timeProvider.nowEpochSecond();
        user.setPendingCodeforcesHandle(handle);
        user.setCodeforcesBindingStartedAtSeconds(startedAtSeconds);
        userRepository.save(user);

        return new CodeforcesBindingResponse(
                handle,
                startedAtSeconds + CODEFORCES_BINDING_TTL_SECONDS,
                CODEFORCES_VERIFICATION_URL,
                "Submit a compilation error to Codeforces 1A within 2 minutes, then finish verification"
        );
    }

    @Override
    public User finishCodeforcesBinding(Long userId) {
        User user = getById(userId);
        String pendingHandle = user.getPendingCodeforcesHandle();
        Long startedAtSeconds = user.getCodeforcesBindingStartedAtSeconds();
        if (pendingHandle == null || pendingHandle.isBlank() || startedAtSeconds == null) {
            throw new BusinessException(400, "Codeforces binding has not been started");
        }

        long nowSeconds = timeProvider.nowEpochSecond();
        if (nowSeconds - startedAtSeconds > CODEFORCES_BINDING_TTL_SECONDS) {
            clearPendingCodeforcesBinding(user);
            userRepository.save(user);
            throw new BusinessException(400, "Codeforces binding verification expired; please start again");
        }

        Optional<Boolean> verification =
                codeforcesApiService.hasOwnershipVerificationSubmission(pendingHandle, startedAtSeconds);
        if (verification.isEmpty()) {
            throw new BusinessException(503, "Codeforces API is unavailable; please try again");
        }
        if (!verification.get()) {
            throw new BusinessException(400, "No qualifying compilation-error submission was found");
        }

        Optional<User> boundUser = userRepository.findByCodeforcesHandle(pendingHandle);
        if (boundUser.isPresent() && !boundUser.get().getId().equals(userId)) {
            throw new BusinessException(400, "this Codeforces handle is already bound");
        }

        CodeforcesApiService.CodeforcesUserProfile profile = codeforcesApiService.getUserInfo(pendingHandle)
                .orElseThrow(() -> new BusinessException(503, "Codeforces API is unavailable; please try again"));
        user.setCodeforcesHandle(pendingHandle);
        syncCodeforcesProfile(user, profile);
        clearPendingCodeforcesBinding(user);
        return userRepository.save(user);
    }

    @Override
    public AtCoderBindingResponse startAtCoderBinding(Long userId, AtCoderBindingStartRequest request) {
        User user = getById(userId);
        String handle = request.handle().trim();
        Optional<User> boundUser = userRepository.findByAtcoderHandle(handle);
        if (boundUser.isPresent() && !boundUser.get().getId().equals(userId)) {
            throw new BusinessException(400, "this AtCoder handle is already bound");
        }
        if (atCoderApiService.getPublicProfile(handle).isEmpty()) {
            throw new BusinessException(400, "AtCoder Handle 不存在或 AtCoder 暂时无法访问");
        }
        long startedAt = timeProvider.nowEpochSecond();
        String token = "WHUT-ACM-" + UUID.randomUUID().toString()
                .replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
        user.setPendingAtcoderHandle(handle);
        user.setAtcoderBindingToken(token);
        user.setAtcoderBindingStartedAtSeconds(startedAt);
        userRepository.save(user);
        return new AtCoderBindingResponse(handle, token, startedAt + ATCODER_BINDING_TTL_SECONDS,
                atCoderApiService.profileUrl(handle),
                "请临时将 AtCoder 个人资料中的 Affiliation 修改为验证码，然后完成验证");
    }

    @Override
    public User finishAtCoderBinding(Long userId) {
        User user = getById(userId);
        String handle = user.getPendingAtcoderHandle();
        String token = user.getAtcoderBindingToken();
        Long startedAt = user.getAtcoderBindingStartedAtSeconds();
        if (handle == null || token == null || startedAt == null) {
            throw new BusinessException(400, "AtCoder 绑定尚未开始");
        }
        long now = timeProvider.nowEpochSecond();
        if (now - startedAt > ATCODER_BINDING_TTL_SECONDS) {
            clearPendingAtCoderBinding(user);
            userRepository.save(user);
            throw new BusinessException(400, "AtCoder 验证已过期，请重新开始");
        }
        AtCoderApiService.AtCoderPublicProfile profile = atCoderApiService.getPublicProfile(handle)
                .orElseThrow(() -> new BusinessException(503, "AtCoder 暂时无法访问，请稍后重试"));
        if (profile.affiliation() == null
                || !profile.affiliation().toUpperCase(Locale.ROOT).contains(token.toUpperCase(Locale.ROOT))) {
            throw new BusinessException(400, "未在 AtCoder Affiliation 中检测到验证码");
        }
        Optional<User> boundUser = userRepository.findByAtcoderHandle(handle);
        if (boundUser.isPresent() && !boundUser.get().getId().equals(userId)) {
            throw new BusinessException(400, "this AtCoder handle is already bound");
        }
        user.setAtcoderHandle(handle);
        user.setAtcoderVerifiedAtSeconds(now);
        clearPendingAtCoderBinding(user);
        return userRepository.save(user);
    }

    private void clearPendingAtCoderBinding(User user) {
        user.setPendingAtcoderHandle(null);
        user.setAtcoderBindingToken(null);
        user.setAtcoderBindingStartedAtSeconds(null);
    }

    /**
     * 将 Codeforces 统计信息写回用户实体。
     *
     * @param user    用户。
     * @param profile Codeforces 资料。
     */
    private void syncCodeforcesProfile(User user, CodeforcesApiService.CodeforcesUserProfile profile) {
        user.setCodeforcesRating(profile.rating());
        user.setMaxRating(profile.maxRating());
        user.setOnline(profile.online());
        user.setLastOnlineTimeSeconds(profile.lastOnlineTimeSeconds());
        if (!Boolean.TRUE.equals(user.getAvatarCustomized())) {
            user.setAvatarUrl(profile.avatarUrl());
        }
        user.setUid(parseUidFromAvatarUrl(profile.avatarUrl()));
    }

    /**
     * 在必要时补齐 Codeforces 资料。
     *
     * @param user    用户。
     * @param profile Codeforces 资料。
     */
    private void enrichFromCodeforcesIfNeeded(User user, CodeforcesApiService.CodeforcesUserProfile profile) {
        boolean needEnrich = user.getCodeforcesRating() == null
                || user.getMaxRating() == null
                || user.getOnline() == null
                || user.getLastOnlineTimeSeconds() == null
                || user.getAvatarUrl() == null
                || user.getAvatarUrl().isBlank();
        if (!needEnrich) {
            user.setUid(parseUidFromAvatarUrl(user.getAvatarUrl()));
            return;
        }

        if (user.getCodeforcesRating() == null) {
            user.setCodeforcesRating(profile.rating());
        }
        if (user.getMaxRating() == null) {
            user.setMaxRating(profile.maxRating());
        }
        if (user.getOnline() == null) {
            user.setOnline(profile.online());
        }
        if (user.getLastOnlineTimeSeconds() == null) {
            user.setLastOnlineTimeSeconds(profile.lastOnlineTimeSeconds());
        }
        if (user.getAvatarUrl() == null || user.getAvatarUrl().isBlank()) {
            user.setAvatarUrl(profile.avatarUrl());
        }
        user.setUid(parseUidFromAvatarUrl(user.getAvatarUrl()));
    }

    private void clearPendingCodeforcesBinding(User user) {
        user.setPendingCodeforcesHandle(null);
        user.setCodeforcesBindingStartedAtSeconds(null);
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

    /**
     * 从头像 URL 中解析 UID。
     *
     * @param avatarUrl 头像地址。
     * @return UID；无法解析时返回 null。
     */
    private Long parseUidFromAvatarUrl(String avatarUrl) {
        if (avatarUrl == null || avatarUrl.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(avatarUrl.trim());
            String path = uri.getPath();
            if (path == null || path.isBlank()) {
                return null;
            }
            String[] segments = path.split("/");
            for (String segment : segments) {
                if (segment == null || segment.isBlank()) {
                    continue;
                }
                if (segment.chars().allMatch(Character::isDigit)) {
                    return Long.parseLong(segment);
                }
                break;
            }
            return null;
        } catch (Exception ex) {
            return null;
        }
    }
}
