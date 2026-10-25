package com.whut.training.domain.dto;

import java.time.Instant;

/** 仅已验证账号的统一展示模型；未同步的统计保持 null，不与真实的零混淆。 */
public record PlatformOverview(
        String platform,
        String accountId,
        String profileUrl,
        boolean connected,
        Integer rating,
        Integer maxRating,
        Integer solvedCount,
        Instant syncedAt,
        DataStatus dataStatus,
        boolean partial
) {
    public enum DataStatus { UNBOUND, NOT_SUPPORTED, PENDING, SYNCING, READY, UNAVAILABLE }
}
