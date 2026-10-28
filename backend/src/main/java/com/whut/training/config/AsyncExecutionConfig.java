package com.whut.training.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/** Bounded executors keep slow external OJ calls away from servlet and common-pool threads. */
@Configuration
public class AsyncExecutionConfig {

    @Bean(name = "platformDetailExecutor")
    public Executor platformDetailExecutor() {
        return executor("platform-detail-", 2, 16);
    }

    @Bean(name = "platformStatisticsExecutor")
    public Executor platformStatisticsExecutor() {
        return executor("platform-stats-", 2, 40);
    }

    @Bean(name = "codeforcesBackgroundExecutor")
    public Executor codeforcesBackgroundExecutor() {
        return executor("cf-profile-", 2, 40);
    }

    @Bean(name = "dailyCheckInExecutor")
    public Executor dailyCheckInExecutor() {
        return executor("daily-checkin-", 2, 100);
    }

    @Bean(name = "contestFetchExecutor")
    public Executor contestFetchExecutor() {
        return executor("contest-fetch-", 4, 16);
    }

    private ThreadPoolTaskExecutor executor(String prefix, int workers, int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(workers);
        executor.setMaxPoolSize(workers);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(prefix);
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }
}
