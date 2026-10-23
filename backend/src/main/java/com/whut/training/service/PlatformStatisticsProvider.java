package com.whut.training.service;

import java.time.Instant;

/** 平台统计扩展点。实现只能读取本地快照，外部同步应由独立任务完成。 */
public interface PlatformStatisticsProvider {
    String platform();
    Statistics read(Long userId, String accountId);

    record Statistics(Integer rating, Integer maxRating, Integer solvedCount, Instant syncedAt, boolean partial) { }
}
