package com.whut.training.service;

import com.whut.training.aspect.annotation.ServiceLog;
import com.whut.training.domain.entity.CfProblem;
import com.whut.training.exception.BusinessException;
import com.whut.training.repository.ProblemCatalogRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** One shared synchronization gate for daily generation and practice requests. */
@Service
@ServiceLog
public class ProblemPoolService {
    private static final Logger log = LoggerFactory.getLogger(ProblemPoolService.class);
    private final ProblemCatalogRepository catalogRepository;
    private final CodeforcesApiService codeforcesApiService;
    private final boolean initialSyncEnabled;
    private final AtomicBoolean problemSyncInProgress = new AtomicBoolean(false);

    public ProblemPoolService(ProblemCatalogRepository catalogRepository, CodeforcesApiService codeforcesApiService,
                              @Value("${app.daily-problem.initial-sync-enabled:true}") boolean initialSyncEnabled) {
        this.catalogRepository = catalogRepository;
        this.codeforcesApiService = codeforcesApiService;
        this.initialSyncEnabled = initialSyncEnabled;
    }

    /**
     * 应用启动后异步预同步 Codeforces 题库。
     * 避免首次请求时同步耗时过长导致前端超时。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initialSyncOnStartup() {
        if (!initialSyncEnabled || catalogRepository.countProblems() > 0) {
            return;
        }
        startProblemPoolSyncAsync();
    }

    /**
     * 定时同步题库。
     */
    @Scheduled(cron = "${app.daily-problem.sync-cron:0 0 */6 * * *}", zone = "${app.daily-problem.zone:Asia/Shanghai}")
    public void syncProblemPoolByScheduler() {
        syncProblemPoolIfIdle();
    }

    /**
     * 确保题库可用。
     */
    public void ensureAvailable() {
        long count = catalogRepository.countProblems();
        if (count > 0) {
            return;
        }
        startProblemPoolSyncAsync();
        throw new BusinessException(503, "problem pool is initializing, please retry shortly");
    }

    /**
     * 在后台触发一次题库同步。全局只允许一个同步任务运行，避免首次访问时重复拉取并阻塞请求线程。
     */
    private void startProblemPoolSyncAsync() {
        if (!problemSyncInProgress.compareAndSet(false, true)) {
            return;
        }
        Thread syncThread = new Thread(() -> {
            try {
                int synced = syncProblemPool();
                if (synced > 0) {
                    log.info("Codeforces problem pool sync completed: {} problems", synced);
                } else {
                    log.warn("Codeforces problem pool sync returned no problems; it will be retried later");
                }
            } catch (Exception ex) {
                log.warn("Codeforces problem pool sync failed; it will be retried later", ex);
            } finally {
                problemSyncInProgress.set(false);
            }
        }, "cf-problem-sync");
        syncThread.setDaemon(true);
        syncThread.start();
    }

    /**
     * 由定时任务同步题库；若已有同步任务则直接跳过本轮。
     */
    private int syncProblemPoolIfIdle() {
        if (!problemSyncInProgress.compareAndSet(false, true)) {
            log.debug("Codeforces problem pool sync skipped because another sync is in progress");
            return 0;
        }
        try {
            return syncProblemPool();
        } finally {
            problemSyncInProgress.set(false);
        }
    }

    /**
     * 从 Codeforces 同步题库。
     *
     * @return 同步条数。
     */
    private int syncProblemPool() {
        List<CfProblem> problems = codeforcesApiService.fetchProblemSet();
        if (problems.isEmpty()) {
            return 0;
        }
        return catalogRepository.upsertProblems(problems);
    }

}
