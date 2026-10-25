package com.whut.training.config;

import com.whut.training.service.PublicPlatformStatisticsService;
import com.whut.training.service.PlatformStatisticsProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PlatformStatisticsConfig {
    @Bean PlatformStatisticsProvider atcoderStatistics(PublicPlatformStatisticsService service) { return provider("atcoder", service); }
    @Bean PlatformStatisticsProvider luoguStatistics(PublicPlatformStatisticsService service) { return provider("luogu", service); }
    @Bean PlatformStatisticsProvider nowcoderStatistics(PublicPlatformStatisticsService service) { return provider("nowcoder", service); }

    private PlatformStatisticsProvider provider(String platform, PublicPlatformStatisticsService service) {
        return new PlatformStatisticsProvider() {
            public String platform() { return platform; }
            public Statistics read(Long userId, String accountId) { return service.read(userId, platform, accountId); }
        };
    }
}
