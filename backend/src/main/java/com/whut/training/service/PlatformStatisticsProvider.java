package com.whut.training.service;

import java.time.Instant;

/** 平台统计扩展点。读取本地快照，可提交后台更新，但不能等待外部网络。 */
public interface PlatformStatisticsProvider {
    String platform();
    Statistics read(Long userId, String accountId);

    record Statistics(Integer rating, Integer maxRating, Integer solvedCount, Instant syncedAt, boolean partial, boolean refreshing) {
        public Statistics(Integer rating, Integer maxRating, Integer solvedCount, Instant syncedAt, boolean partial) {
            this(rating, maxRating, solvedCount, syncedAt, partial, false);
        }
    }
}
