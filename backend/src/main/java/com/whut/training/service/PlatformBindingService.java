package com.whut.training.service;

import com.whut.training.common.TimeProvider;
import com.whut.training.exception.BusinessException;
import com.whut.training.repository.PlatformBindingRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class PlatformBindingService {
    private final PlatformBindingRepository repository;
    private final PublicProfileVerifier verifier;
    private final TimeProvider time;
    private final TransactionTemplate transaction;
    private final SecureRandom random = new SecureRandom();
    public PlatformBindingService(PlatformBindingRepository repository, PublicProfileVerifier verifier,
                                  TimeProvider time, PlatformTransactionManager manager) {
        this.repository=repository; this.verifier=verifier; this.time=time; this.transaction=new TransactionTemplate(manager);
    }
    public record View(String platform, String accountId, Long verifiedAtSeconds, String pendingAccountId,
                       String verificationToken, Long expiresAtSeconds, String profileUrl) {}
    public View get(Long userId, String platform) {
        platform(platform);
        return view(platform, repository.find(userId, platform, false).orElse(null));
    }
    public View start(Long userId, String platform, String uid) {
        platform(platform);
        if (uid == null || !uid.matches("[1-9][0-9]{0,17}")) throw new BusinessException(400, "请输入平台主页中的数字 UID（不超过18位）");
        byte[] bytes = new byte[16]; random.nextBytes(bytes);
        // Nowcoder allows only 25 introduction characters. Keep all 128 random bits in 24 ASCII characters.
        String token = "nowcoder".equals(platform)
                ? "W-" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
                : "WHUT-" + HexFormat.of().formatHex(bytes);
        transaction.executeWithoutResult(status -> {
            repository.ensure(userId, platform);
            var old = repository.find(userId, platform, true).orElseThrow();
            long now = time.nowEpochSecond();
            if (old.startedAt() > 0 && now - old.startedAt() < 10) throw new BusinessException(429, "请等待10秒后重新发起验证");
            repository.start(userId, platform, uid, token, now);
        });
        return get(userId, platform);
    }
    public View finish(Long userId, String platform) {
        platform(platform);
        var pending = repository.find(userId, platform, false).orElseThrow(() -> new BusinessException(400, "请先开始验证"));
        requirePending(pending);
        if (!repository.claimAttempt(userId, platform, time.nowEpochSecond())) throw new BusinessException(429, "请间隔5秒后重试");
        String introduction = verifier.introduction(platform, pending.pendingAccountId());
        if (!introduction.contains(pending.token())) throw new BusinessException(400, "尚未在该账号个人简介中发现验证码，请保存后稍等再验证");
        try {
            transaction.executeWithoutResult(status -> {
                var current = repository.find(userId, platform, true).orElseThrow();
                requirePending(current);
                if (!pending.token().equals(current.token()) || !pending.pendingAccountId().equals(current.pendingAccountId()))
                    throw new BusinessException(409, "验证已重新开始，请使用最新验证码");
                repository.finish(userId, platform, current.pendingAccountId(), time.nowEpochSecond());
            });
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(409, "该平台账号已绑定其他成员");
        }
        return get(userId, platform);
    }
    private void requirePending(PlatformBindingRepository.Binding binding) {
        if (binding.token() == null || binding.pendingAccountId() == null) throw new BusinessException(400, "请先开始验证");
        if (time.nowEpochSecond() >= binding.expiresAt()) throw new BusinessException(400, "验证码已过期，请重新开始验证");
    }
    private View view(String platform, PlatformBindingRepository.Binding binding) {
        boolean active = binding != null && binding.token() != null && time.nowEpochSecond() < binding.expiresAt();
        String uid = active ? binding.pendingAccountId() : binding == null ? null : binding.accountId();
        return new View(platform, binding == null ? null : binding.accountId(), binding == null ? null : binding.verifiedAt(),
                active ? binding.pendingAccountId() : null, active ? binding.token() : null,
                active ? binding.expiresAt() : null, uid == null ? null : verifier.profileUrl(platform, uid));
    }
    private void platform(String platform) {
        if (!"luogu".equals(platform) && !"nowcoder".equals(platform)) throw new BusinessException(400, "不支持的平台");
    }
}
