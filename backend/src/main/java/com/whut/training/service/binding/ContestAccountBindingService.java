package com.whut.training.service.binding;

import com.whut.training.aspect.annotation.ServiceLog;
import com.whut.training.common.TimeProvider;
import com.whut.training.domain.dto.AtCoderBindingResponse;
import com.whut.training.domain.dto.AtCoderBindingStartRequest;
import com.whut.training.domain.dto.CodeforcesBindingResponse;
import com.whut.training.domain.dto.CodeforcesBindingStartRequest;
import com.whut.training.domain.entity.User;
import com.whut.training.exception.BusinessException;
import com.whut.training.repository.UserRepository;
import com.whut.training.service.AtCoderApiService;
import com.whut.training.service.CodeforcesApiService;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/** Owns CF submission proof and AtCoder affiliation proof, independently of site profiles. */
@Service
@ServiceLog
public class ContestAccountBindingService {
    private static final long CODEFORCES_BINDING_TTL_SECONDS = 120;
    private static final String CODEFORCES_VERIFICATION_URL = "https://codeforces.com/contest/1/problem/A";
    private static final long ATCODER_BINDING_TTL_SECONDS = 600;

    private final UserRepository userRepository;
    private final CodeforcesApiService codeforcesApiService;
    private final AtCoderApiService atCoderApiService;
    private final TimeProvider timeProvider;

    public ContestAccountBindingService(UserRepository userRepository, CodeforcesApiService codeforcesApiService,
                                        AtCoderApiService atCoderApiService, TimeProvider timeProvider) {
        this.userRepository = userRepository;
        this.codeforcesApiService = codeforcesApiService;
        this.atCoderApiService = atCoderApiService;
        this.timeProvider = timeProvider;
    }

    private User findUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "user not found: " + id));
    }

    public CodeforcesBindingResponse startCodeforcesBinding(Long userId, CodeforcesBindingStartRequest request) {
        User user = findUser(userId);
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

    public User finishCodeforcesBinding(Long userId) {
        User user = findUser(userId);
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

    public AtCoderBindingResponse startAtCoderBinding(Long userId, AtCoderBindingStartRequest request) {
        User user = findUser(userId);
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

    public User finishAtCoderBinding(Long userId) {
        User user = findUser(userId);
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
        user.setUid(parseUidFromAvatarUrl(profile.avatarUrl()));
    }

    private void clearPendingCodeforcesBinding(User user) {
        user.setPendingCodeforcesHandle(null);
        user.setCodeforcesBindingStartedAtSeconds(null);
    }

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
