package com.whut.training.service;

import com.whut.training.domain.dto.PlatformOverview;
import com.whut.training.domain.dto.PlatformOverview.DataStatus;
import com.whut.training.domain.entity.User;
import com.whut.training.exception.BusinessException;
import com.whut.training.repository.PlatformBindingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PlatformOverviewService {
    private static final Logger log = LoggerFactory.getLogger(PlatformOverviewService.class);
    private static final List<String> PLATFORMS = List.of("codeforces", "atcoder", "luogu", "nowcoder");
    private final PlatformBindingRepository bindings;
    private final Map<String, PlatformStatisticsProvider> providers;

    public PlatformOverviewService(PlatformBindingRepository bindings, List<PlatformStatisticsProvider> providers) {
        this.bindings = bindings;
        this.providers = providers.stream().collect(Collectors.toUnmodifiableMap(PlatformStatisticsProvider::platform, Function.identity()));
    }

    public List<PlatformOverview> get(User user) {
        if (user == null || user.getId() == null) throw new BusinessException(401, "请先登录");
        return PLATFORMS.stream().map(platform -> overview(user, platform)).toList();
    }

    private PlatformOverview overview(User user, String platform) {
        String account = switch (platform) {
            case "codeforces" -> user.getCodeforcesHandle();
            case "atcoder" -> user.getAtcoderHandle();
            default -> bindings.find(user.getId(), platform, false).map(PlatformBindingRepository.Binding::accountId).orElse(null);
        };
        if (account == null || account.isBlank()) return empty(platform, null, DataStatus.UNBOUND);
        var provider = providers.get(platform);
        if (provider == null) return empty(platform, account, DataStatus.NOT_SUPPORTED);
        try {
            var stats = provider.read(user.getId(), account);
            if (stats == null) return empty(platform, account, DataStatus.PENDING);
            return new PlatformOverview(platform, account, profileUrl(platform, account), true, stats.rating(), stats.maxRating(),
                    stats.solvedCount(), stats.syncedAt(), DataStatus.READY, stats.partial());
        } catch (RuntimeException error) {
            log.warn("Local platform overview unavailable platform={} userId={} cause={}", platform, user.getId(), error.getClass().getSimpleName());
            return empty(platform, account, DataStatus.UNAVAILABLE);
        }
    }

    private PlatformOverview empty(String platform, String account, DataStatus status) {
        return new PlatformOverview(platform, account, account == null ? null : profileUrl(platform, account),
                account != null, null, null, null, null, status, false);
    }

    private String profileUrl(String platform, String account) {
        String encoded = java.net.URLEncoder.encode(account, java.nio.charset.StandardCharsets.UTF_8);
        return switch (platform) {
            case "codeforces" -> "https://codeforces.com/profile/" + encoded;
            case "atcoder" -> "https://atcoder.jp/users/" + encoded;
            case "luogu" -> "https://www.luogu.com.cn/user/" + encoded;
            default -> "https://www.nowcoder.com/users/" + encoded;
        };
    }
}
