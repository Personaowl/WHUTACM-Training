package com.whut.training.service;

import com.whut.training.common.TimeProvider;
import com.whut.training.domain.dto.CheckInResultResponse;
import com.whut.training.domain.dto.DailyCheckInJobResponse;
import com.whut.training.domain.entity.User;
import com.whut.training.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.time.LocalDate;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/** Asynchronous facade that prevents Codeforces rate-limit waiting from occupying HTTP request threads. */
@Service
public class DailyCheckInJobService {
    private static final Logger log = LoggerFactory.getLogger(DailyCheckInJobService.class);
    private static final long DEDUPLICATION_MILLIS = 10 * 60 * 1000L;
    private static final long RETENTION_MILLIS = 60 * 60 * 1000L;
    private static final int MAX_RETAINED_JOBS = 2000;

    private final DailyProblemService dailyProblemService;
    private final TimeProvider timeProvider;
    private final Executor executor;
    private final Map<String, Job> jobs = new ConcurrentHashMap<>();
    private final Map<JobKey, String> jobIdsByRequest = new ConcurrentHashMap<>();

    public DailyCheckInJobService(
            DailyProblemService dailyProblemService,
            TimeProvider timeProvider,
            @Qualifier("dailyCheckInExecutor") Executor executor
    ) {
        this.dailyProblemService = dailyProblemService;
        this.timeProvider = timeProvider;
        this.executor = executor;
    }

    public synchronized DailyCheckInJobResponse submit(User user, Long submissionId) {
        long now = System.currentTimeMillis();
        LocalDate targetDate = timeProvider.today();
        JobKey key = new JobKey(user.getId(), submissionId, targetDate);
        String existingId = jobIdsByRequest.get(key);
        Job existing = existingId == null ? null : jobs.get(existingId);
        if (existing != null && (existing.isActive()
                || ("SUCCEEDED".equals(existing.status) && now - existing.createdAtMillis < DEDUPLICATION_MILLIS))) {
            return existing.response();
        }

        if (jobs.size() >= MAX_RETAINED_JOBS) {
            cleanupExpiredJobs();
            if (jobs.size() >= MAX_RETAINED_JOBS) {
                throw new BusinessException(503, "校验请求较多，请稍后重试");
            }
        }

        Job job = new Job(UUID.randomUUID().toString(), user.getId(), submissionId, targetDate, now);
        jobs.put(job.id, job);
        jobIdsByRequest.put(key, job.id);
        try {
            executor.execute(() -> run(job, user));
        } catch (RuntimeException rejected) {
            job.fail(503, "打卡校验队列暂时已满，请稍后重试");
        }
        return job.response();
    }

    public DailyCheckInJobResponse get(String jobId, Long requesterId) {
        Job job = jobs.get(jobId);
        if (job == null) throw new BusinessException(404, "打卡校验任务不存在或已过期");
        if (!job.userId.equals(requesterId)) throw new BusinessException(403, "forbidden");
        return job.response();
    }

    private void run(Job job, User user) {
        job.running();
        try {
            job.succeed(dailyProblemService.checkIn(user, job.submissionId, job.targetDate));
        } catch (BusinessException ex) {
            job.fail(ex.getCode(), ex.getMessage());
        } catch (Exception ex) {
            log.error("Asynchronous daily check-in failed jobId={} userId={}", job.id, job.userId, ex);
            job.fail(500, "打卡校验失败，请稍后重试");
        }
    }

    @Scheduled(fixedDelayString = "${app.daily-check-in.cleanup-delay-ms:600000}")
    public synchronized void cleanupExpiredJobs() {
        long cutoff = System.currentTimeMillis() - RETENTION_MILLIS;
        jobs.entrySet().removeIf(entry -> {
            Job job = entry.getValue();
            if (job.isActive() || job.createdAtMillis >= cutoff) return false;
            jobIdsByRequest.remove(new JobKey(job.userId, job.submissionId, job.targetDate), job.id);
            return true;
        });
    }

    private record JobKey(Long userId, Long submissionId, LocalDate targetDate) {
    }

    private static final class Job {
        private final String id;
        private final Long userId;
        private final Long submissionId;
        private final LocalDate targetDate;
        private final long createdAtMillis;
        private volatile String status = "PENDING";
        private volatile String message = "校验任务已进入队列";
        private volatile Integer errorCode;
        private volatile CheckInResultResponse result;

        private Job(String id, Long userId, Long submissionId, LocalDate targetDate, long createdAtMillis) {
            this.id = id;
            this.userId = userId;
            this.submissionId = submissionId;
            this.targetDate = targetDate;
            this.createdAtMillis = createdAtMillis;
        }

        private void running() {
            status = "RUNNING";
            message = "正在等待 Codeforces 校验";
        }

        private boolean isActive() {
            return "PENDING".equals(status) || "RUNNING".equals(status);
        }

        private void succeed(CheckInResultResponse value) {
            result = value;
            message = "校验完成";
            status = "SUCCEEDED";
        }

        private void fail(int code, String value) {
            errorCode = code;
            message = value == null || value.isBlank() ? "校验失败" : value;
            status = "FAILED";
        }

        private DailyCheckInJobResponse response() {
            return new DailyCheckInJobResponse(id, status, message, errorCode, result);
        }
    }
}
