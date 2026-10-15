package com.whut.training.service.impl;

import com.whut.training.aspect.annotation.ServiceLog;
import com.whut.training.common.TimeProvider;
import com.whut.training.domain.dto.*;
import com.whut.training.domain.entity.CfProblem;
import com.whut.training.domain.entity.DailyProblem;
import com.whut.training.domain.entity.User;
import com.whut.training.domain.entity.UserPracticeDraw;
import com.whut.training.domain.enums.UserRole;
import com.whut.training.exception.BusinessException;
import com.whut.training.repository.DailyProblemRepository;
import com.whut.training.service.CodeforcesApiService;
import com.whut.training.service.DailyProblemService;
import com.whut.training.service.DailyCheckInRecorder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 每日题与练习题服务实现。
 *
 * <p>负责从 Codeforces 拉取题库、自动生成每日题、保存打卡与练习记录。当前实现对外部 API 的失败多采用空结果回落，只有在题库不可用时才显式抛出 503；这会掩盖一部分瞬时网络问题，后续适合补充重试与告警。
 */
@Service
@ServiceLog
public class DailyProblemServiceImpl implements DailyProblemService {

    private static final Logger log = LoggerFactory.getLogger(DailyProblemServiceImpl.class);

    private final DailyProblemRepository dailyProblemRepository;
    private final CodeforcesApiService codeforcesApiService;
    private final TimeProvider timeProvider;
    private final Object generationLock = new Object();
    private final DailyCheckInRecorder checkInRecorder;
    private final AtomicBoolean problemSyncInProgress = new AtomicBoolean(false);
    private final int defaultMinRating;
    private final int defaultMaxRating;
    private final int noRepeatDays;
    private final int easyMinRating;
    private final int easyMaxRating;
    private final int hardMinRating;
    private final int hardMaxRating;
    private final boolean initialSyncEnabled;

    public DailyProblemServiceImpl(
            DailyProblemRepository dailyProblemRepository,
            CodeforcesApiService codeforcesApiService,
            TimeProvider timeProvider,
            DailyCheckInRecorder checkInRecorder,
            @Value("${app.daily-problem.min-rating:1200}") int defaultMinRating,
            @Value("${app.daily-problem.max-rating:2000}") int defaultMaxRating,
            @Value("${app.daily-problem.no-repeat-days:90}") int noRepeatDays,
            @Value("${app.daily-problem.easy-min-rating:800}") int easyMinRating,
            @Value("${app.daily-problem.easy-max-rating:1800}") int easyMaxRating,
            @Value("${app.daily-problem.hard-min-rating:1900}") int hardMinRating,
            @Value("${app.daily-problem.hard-max-rating:3000}") int hardMaxRating,
            @Value("${app.daily-problem.initial-sync-enabled:true}") boolean initialSyncEnabled
    ) {
        this.dailyProblemRepository = dailyProblemRepository;
        this.codeforcesApiService = codeforcesApiService;
        this.timeProvider = timeProvider;
        this.checkInRecorder = checkInRecorder;
        this.defaultMinRating = defaultMinRating;
        this.defaultMaxRating = defaultMaxRating;
        this.noRepeatDays = noRepeatDays;
        this.easyMinRating = easyMinRating;
        this.easyMaxRating = easyMaxRating;
        this.hardMinRating = hardMinRating;
        this.hardMaxRating = hardMaxRating;
        this.initialSyncEnabled = initialSyncEnabled;
    }

    /**
     * 应用启动后异步预同步 Codeforces 题库。
     * 避免首次请求时同步耗时过长导致前端超时。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initialSyncOnStartup() {
        if (!initialSyncEnabled || dailyProblemRepository.countProblems() > 0) {
            return;
        }
        startProblemPoolSyncAsync();
    }

    /**
     * 定时生成今日题。
     */
    @Scheduled(cron = "${app.daily-problem.cron:0 5 0 * * *}", zone = "${app.daily-problem.zone:Asia/Shanghai}")
    public void generateDailyProblemByScheduler() {
        LocalDate today = timeProvider.today();
        ensureDailyProblem(today, false, "scheduler");
        ensureDailySlots(today, false, "scheduler");
    }

    /**
     * 定时同步题库。
     */
    @Scheduled(cron = "${app.daily-problem.sync-cron:0 0 */6 * * *}", zone = "${app.daily-problem.zone:Asia/Shanghai}")
    public void syncProblemPoolByScheduler() {
        syncProblemPoolIfIdle();
    }

    /**
     * 获取今日题与打卡状态（返回 easy/hard 多题位）。
     *
     * @param user 当前用户。
     * @return 今日题响应。
     */
    @Override
    public DailyProblemTodayResponse getToday(User user) {
        LocalDate today = timeProvider.today();
        ensureDailySlots(today, false, "api");

        List<com.whut.training.domain.entity.DailyProblemSlot> slots =
                dailyProblemRepository.findCurrentDailySlotsByDate(today);
        Optional<com.whut.training.domain.entity.UserDailyStatus> statusOptional =
                dailyProblemRepository.findUserDailyStatus(user.getId(), today);

        List<ProblemView> problems;
        if (slots != null && !slots.isEmpty()) {
            problems = slots.stream()
                    .filter(s -> !s.isRedrawn())
                    .map(s -> {
                        var slotStatus = dailyProblemRepository.findUserDailySlotStatus(
                                user.getId(),
                                s.date(),
                                s.problemKey()
                        );
                        // Daily score is the maximum score across every slot.  It must never be
                        // used to infer an individual slot's state: doing so couples Easy and
                        // Hard, and can mark a different slot as completed for legacy or
                        // regenerated daily records.  The slot-status table is the source of
                        // truth once slots exist.
                        boolean checkedIn = slotStatus.isPresent();
                        Integer score = slotStatus
                                .map(com.whut.training.domain.entity.UserDailyStatus::score)
                                .orElse(0);
                        return new ProblemView(
                                s.slot().toUpperCase(),
                                s.date().toString(),
                                s.problemKey(),
                                s.contestId(),
                                s.problemIndex(),
                                s.name(),
                                s.rating(),
                                s.tags(),
                                s.sourceUrl(),
                                checkedIn,
                                score
                        );
                    })
                    .toList();
        } else {
            // fallback to legacy single daily problem
            DailyProblem dailyProblem = ensureDailyProblem(today, false, "api");
            problems = List.of(toProblemView(
                    "DAILY",
                    dailyProblem,
                    statusOptional.isPresent(),
                    statusOptional.map(com.whut.training.domain.entity.UserDailyStatus::score).orElse(0)
            ));
        }

        return new DailyProblemTodayResponse(
                problems,
                statusOptional.isPresent(),
                statusOptional.map(com.whut.training.domain.entity.UserDailyStatus::score).orElse(0)
        );
    }

    /**
     * 校验并保存今日题打卡。
     *
     * @param user         当前用户。
     * @param submissionId Codeforces 提交 ID。
     * @return 打卡结果。
     */
    @Override
    public CheckInResultResponse checkIn(User user, Long submissionId) {
        return checkIn(user, submissionId, timeProvider.today());
    }

    @Override
    public CheckInResultResponse checkIn(User user, Long submissionId, LocalDate targetDate) {
        String codeforcesHandle = requireCodeforcesHandle(user);
        LocalDate today = targetDate == null ? timeProvider.today() : targetDate;
        // support multi-slot daily: try to find matching slot (easy/hard) first
        ensureDailySlots(today, false, "api");

        List<com.whut.training.domain.entity.DailyProblemSlot> slots = dailyProblemRepository.findCurrentDailySlotsByDate(today);
        List<com.whut.training.domain.entity.DailyProblemSlot> activeSlots = slots == null
                ? List.of()
                : slots.stream()
                .filter(slot -> !slot.isRedrawn())
                .toList();

        // Fetch the submission once, then match it against both active slots locally.
        CodeforcesApiService.SubmissionStatus submissionStatus = codeforcesApiService
                .getSubmissionStatus(codeforcesHandle, submissionId)
                .orElseThrow(() -> new BusinessException(
                        400,
                        "未在该用户最近30条提交中找到此ID，请确认账号与提交编号（submissionId=" + submissionId + "）"
                ));
        com.whut.training.domain.entity.DailyProblemSlot matchedSlot = activeSlots.stream()
                .filter(slot -> sameProblem(submissionStatus, slot.contestId(), slot.problemIndex()))
                .findFirst()
                .orElse(null);

        // fallback to original single daily problem
        com.whut.training.domain.entity.DailyProblem single = activeSlots.isEmpty()
                ? ensureDailyProblem(today, false, "api")
                : null;
        if (matchedSlot == null && (single == null
                || !sameProblem(submissionStatus, single.contestId(), single.problemIndex()))) {
            throw new BusinessException(
                    400,
                    "提交不匹配今日题目，实际题目为 "
                            + submissionStatus.contestId() + submissionStatus.problemIndex()
            );
        }

        if (!"OK".equalsIgnoreCase(submissionStatus.verdict())) {
            throw new BusinessException(400, "submission is not accepted, verdict=" + submissionStatus.verdict());
        }

        final int newScore;
        if (matchedSlot != null) {
            newScore = matchedSlot.rating() == null ? 0 : matchedSlot.rating();
        } else if (single != null) {
            newScore = single.rating() == null ? 0 : single.rating();
        } else {
            newScore = 0;
        }

        String slot = matchedSlot == null ? "daily" : matchedSlot.slot();
        String problemKey = matchedSlot == null ? single.problemKey() : matchedSlot.problemKey();
        return checkInRecorder.record(user.getId(), today, slot, problemKey, submissionId,
                submissionStatus.verdict(), newScore);
    }

    /**
     * 获取用户每日题历史（基于 slot 表，包含 is_redrawn）。
     *
     * @param user 当前用户。
     * @param days 查询天数，0 表示全部。
     * @return 历史列表。
     */
    @Override
    public List<DailyProblemHistoryItem> getHistory(User user, int days) {
        LocalDate today = timeProvider.today();
        ensureDailySlots(today, false, "history");
        LocalDate startDate;
        if (days <= 0) {
            startDate = LocalDate.of(2020, 1, 1);
        } else {
            startDate = today.minusDays(days - 1L);
        }
        return dailyProblemRepository.findDailyHistoryForUser(user.getId(), startDate, today);
    }

    /**
     * 按难度范围抽取练习题。
     *
     * @param user      当前用户。
     * @param minRating 最小 rating。
     * @param maxRating 最大 rating。
     * @return 抽题响应。
     */
    @Override
    public PracticeDrawResponse drawPracticeProblem(User user, Integer minRating, Integer maxRating, List<String> tags) {
        ensureProblemPoolAvailable();
        int resolvedMinRating = minRating == null ? defaultMinRating : minRating;
        int resolvedMaxRating = maxRating == null ? defaultMaxRating : maxRating;
        if (resolvedMinRating > resolvedMaxRating) {
            throw new BusinessException(400, "invalid rating range");
        }

        List<String> normalizedTags = tags == null ? List.of() : tags.stream()
                .filter(java.util.Objects::nonNull)
                .map(String::trim)
                .filter(tag -> !tag.isEmpty())
                .map(tag -> tag.toLowerCase(java.util.Locale.ROOT))
                .distinct()
                .toList();
        if (normalizedTags.size() > 5 || normalizedTags.stream().anyMatch(tag -> tag.length() > 64)) {
            throw new BusinessException(400, "标签选择无效，最多选择 5 个标签");
        }

        CfProblem problem = dailyProblemRepository.findRandomProblem(
                        resolvedMinRating,
                        resolvedMaxRating,
                        normalizedTags
                )
                .orElseThrow(() -> new BusinessException(404, "没有符合当前难度和标签条件的题目"));
        UserPracticeDraw draw = dailyProblemRepository.insertPracticeDraw(user.getId(), timeProvider.today(), problem);
        return new PracticeDrawResponse(
                draw.id(),
                new ProblemView(
                        "PRACTICE",
                        draw.drawDate().toString(),
                        draw.problemKey(),
                        draw.contestId(),
                        draw.problemIndex(),
                        draw.name(),
                        draw.rating(),
                        draw.tags(),
                        draw.sourceUrl()
                )
        );
    }

    @Override
    public List<String> getAvailablePracticeTags() {
        return dailyProblemRepository.findAvailableProblemTags();
    }

    private String requireCodeforcesHandle(User user) {
        if (user == null || user.getCodeforcesHandle() == null || user.getCodeforcesHandle().isBlank()) {
            throw new BusinessException(400, "please bind your Codeforces account first");
        }
        return user.getCodeforcesHandle();
    }

    @Override
    public List<PracticeHistoryItem> getPracticeHistory(User user, int limit) {
        List<UserPracticeDraw> draws = dailyProblemRepository.findPracticeDrawsByUserId(user.getId(), Math.max(1, Math.min(limit, 100)));
        return draws.stream()
                .map(d -> new PracticeHistoryItem(
                        d.id(),
                        d.drawDate().toString(),
                        d.problemKey(),
                        d.contestId(),
                        d.problemIndex(),
                        d.name(),
                        d.rating(),
                        d.tags(),
                        d.sourceUrl(),
                        null, // drawnAt not stored in current entity
                        d.submissionId(),
                        d.verdict()
                ))
                .toList();
    }

    @Override
    public boolean ensureTodaySlots() {
        try {
            ensureDailySlots(timeProvider.today(), false, "api-home");
            return true;
        } catch (BusinessException ex) {
            if (ex.getCode() != 503) {
                throw ex;
            }
            log.debug("Problem pool is still initializing; home overview will be returned without daily slots");
            return false;
        }
    }

    @Override
    public boolean deletePracticeDraw(User user, Long drawId) {
        return dailyProblemRepository.deletePracticeDraw(drawId, user.getId());
    }

    /**
     * 管理员重生成今日题。
     *
     * @param adminUser 管理员用户。
     * @return 最新题视图。
     */
    @Override
    public ProblemView regenerateTodayByAdmin(User adminUser) {
        if (adminUser == null || (adminUser.getRole() != UserRole.ADMIN && adminUser.getRole() != UserRole.SUPER_ADMIN)) {
            throw new BusinessException(403, "admin role required");
        }
        DailyProblem dailyProblem = ensureDailyProblem(timeProvider.today(), true, "admin");
        return toProblemView("DAILY", dailyProblem);
    }

    @Override
    public ProblemView adminRedrawSlot(User adminUser, java.time.LocalDate date, String slot, boolean confirm) {
        if (adminUser == null || (adminUser.getRole() != UserRole.ADMIN && adminUser.getRole() != UserRole.SUPER_ADMIN)) {
            throw new BusinessException(403, "admin role required");
        }
        if (!"easy".equalsIgnoreCase(slot) && !"hard".equalsIgnoreCase(slot)) {
            throw new BusinessException(400, "invalid slot");
        }
        LocalDate target = date == null ? timeProvider.today() : date;
        synchronized (generationLock) {
            // 生成、旧题失效、新题落库必须视为一个临界区，避免连续点击或并发请求插入多个有效题位。
            ensureDailySlots(target, false, "admin-redraw");
            dailyProblemRepository.markActiveSlotsRedrawn(target, slot);

            ensureProblemPoolAvailable();
            LocalDate noRepeatAfterDate = target.minusDays(Math.max(1, noRepeatDays));
            CfProblem picked = "easy".equalsIgnoreCase(slot)
                    ? dailyProblemRepository.findRandomProblem(easyMinRating, easyMaxRating, noRepeatAfterDate)
                    .or(() -> dailyProblemRepository.findRandomProblem(easyMinRating, easyMaxRating))
                    .orElseThrow(() -> new BusinessException(500, "failed to select easy problem"))
                    : dailyProblemRepository.findRandomProblem(hardMinRating, hardMaxRating, noRepeatAfterDate)
                    .or(() -> dailyProblemRepository.findRandomProblem(hardMinRating, hardMaxRating))
                    .orElseThrow(() -> new BusinessException(500, "failed to select hard problem"));

            var newSlot = dailyProblemRepository.insertDailySlot(target, slot, picked, "admin-redraw");
            // confirm currently does not change backfill behavior; saved for future semantics
            return new ProblemView(
                    slot.toUpperCase(),
                    newSlot.date().toString(),
                    newSlot.problemKey(),
                    newSlot.contestId(),
                    newSlot.problemIndex(),
                    newSlot.name(),
                    newSlot.rating(),
                    newSlot.tags(),
                    newSlot.sourceUrl()
            );
        }
    }

    /**
     * 确保某一天存在每日题。
     *
     * @param date            日期。
     * @param forceRegenerate 是否强制重建。
     * @param generatedBy     生成来源。
     * @return 每日题记录。
     */
    private DailyProblem ensureDailyProblem(LocalDate date, boolean forceRegenerate, String generatedBy) {
        synchronized (generationLock) {
            if (!forceRegenerate) {
                Optional<DailyProblem> existing = dailyProblemRepository.findDailyByDate(date);
                if (existing.isPresent()) {
                    return existing.get();
                }
            } else {
                dailyProblemRepository.deleteDailyByDate(date);
                // also clear slot table if forcing regenerate
                dailyProblemRepository.deleteDailySlotsByDate(date);
            }

            ensureProblemPoolAvailable();
            LocalDate noRepeatAfterDate = date.minusDays(Math.max(1, noRepeatDays));
            CfProblem problem = dailyProblemRepository
                    .findRandomProblem(defaultMinRating, defaultMaxRating, noRepeatAfterDate)
                    .or(() -> dailyProblemRepository.findRandomProblem(defaultMinRating, defaultMaxRating))
                    .orElseThrow(() -> new BusinessException(500, "failed to select daily problem"));
            return dailyProblemRepository.insertDailyProblem(date, problem, generatedBy);
        }
    }

    /**
     * 确保当日的两个题位（easy/hard）存在；若不存在则生成并落库。
     */
    private void ensureDailySlots(LocalDate date, boolean forceRegenerate, String generatedBy) {
        synchronized (generationLock) {
            boolean hasActiveEasy = false;
            boolean hasActiveHard = false;
            if (!forceRegenerate) {
                List<com.whut.training.domain.entity.DailyProblemSlot> exist = dailyProblemRepository.findCurrentDailySlotsByDate(date);
                hasActiveEasy = exist != null && exist.stream()
                        .anyMatch(slot -> !slot.isRedrawn() && "easy".equalsIgnoreCase(slot.slot()));
                hasActiveHard = exist != null && exist.stream()
                        .anyMatch(slot -> !slot.isRedrawn() && "hard".equalsIgnoreCase(slot.slot()));
                if (hasActiveEasy && hasActiveHard) {
                    return;
                }
            } else {
                dailyProblemRepository.deleteDailySlotsByDate(date);
            }

            ensureProblemPoolAvailable();
            LocalDate noRepeatAfterDate = date.minusDays(Math.max(1, noRepeatDays));

            // easy: 800 .. 1800 by default (independently configurable)
            CfProblem easy = hasActiveEasy ? null : dailyProblemRepository.findRandomProblem(easyMinRating, easyMaxRating, noRepeatAfterDate)
                    .or(() -> dailyProblemRepository.findRandomProblem(easyMinRating, easyMaxRating))
                    .orElse(null);

            // hard: 1900 .. 3000 by default; 1801..1899 is intentionally left out.
            CfProblem hard = hasActiveHard ? null : dailyProblemRepository.findRandomProblem(hardMinRating, hardMaxRating, noRepeatAfterDate)
                    .or(() -> dailyProblemRepository.findRandomProblem(hardMinRating, hardMaxRating))
                    .orElse(null);

            if (easy != null) {
                dailyProblemRepository.insertDailySlot(date, "easy", easy, generatedBy);
            }
            if (hard != null) {
                dailyProblemRepository.insertDailySlot(date, "hard", hard, generatedBy);
            }
        }
    }

    /**
     * 确保题库可用。
     */
    private void ensureProblemPoolAvailable() {
        long count = dailyProblemRepository.countProblems();
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
        return dailyProblemRepository.upsertProblems(problems);
    }

    private boolean sameProblem(CodeforcesApiService.SubmissionStatus submission, Integer contestId,
                                String problemIndex) {
        return contestId != null && contestId.equals(submission.contestId())
                && problemIndex != null && problemIndex.equalsIgnoreCase(submission.problemIndex());
    }

    /**
     * 将每日题记录转换为对外视图。
     *
     * @param type         题目类型。
     * @param dailyProblem 每日题实体。
     * @return 题目视图。
     */
    private ProblemView toProblemView(String type, DailyProblem dailyProblem) {
        return toProblemView(type, dailyProblem, false, 0);
    }

    private ProblemView toProblemView(String type, DailyProblem dailyProblem, boolean checkedIn, Integer score) {
        return new ProblemView(
                type,
                dailyProblem.date().toString(),
                dailyProblem.problemKey(),
                dailyProblem.contestId(),
                dailyProblem.problemIndex(),
                dailyProblem.name(),
                dailyProblem.rating(),
                dailyProblem.tags(),
                dailyProblem.sourceUrl(),
                checkedIn,
                score
        );
    }
}
