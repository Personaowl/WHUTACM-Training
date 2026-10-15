package com.whut.training.service;

import com.whut.training.common.TimeProvider;
import com.whut.training.domain.dto.WeeklyTrainingReport;
import com.whut.training.domain.entity.User;
import com.whut.training.domain.enums.MemberType;
import com.whut.training.domain.enums.UserRole;
import com.whut.training.exception.BusinessException;
import com.whut.training.repository.WeeklyTrainingRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class WeeklyTrainingService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Set<String> MANUAL_STATUSES = Set.of("AUTO", "COMPLETED", "EXEMPT", "INCOMPLETE");
    private final WeeklyTrainingRepository repository;
    private final TimeProvider time;

    public WeeklyTrainingService(WeeklyTrainingRepository repository, TimeProvider time) {
        this.repository = repository;
        this.time = time;
    }

    @Scheduled(fixedDelayString = "${app.weekly-training.roster-delay-ms:1800000}",
            initialDelayString = "${app.weekly-training.roster-initial-delay-ms:45000}")
    public void captureCurrentRoster() {
        repository.captureRoster(weekStart(time.today()), time.now().toString());
    }

    public WeeklyTrainingReport personal(User actor, LocalDate date) {
        requireUser(actor);
        LocalDate week = checkedWeek(date);
        if (week.equals(weekStart(time.today())) && actor.getMemberType() == MemberType.ACTIVE_TEAM) {
            repository.ensureRecord(week, actor.getId(), time.now().toString());
        }
        return report(week, actor.getId());
    }

    public WeeklyTrainingReport dashboard(User actor, LocalDate date) {
        requireAdmin(actor);
        LocalDate week = checkedWeek(date);
        if (week.equals(weekStart(time.today()))) captureCurrentRoster();
        return report(week, null);
    }

    @Transactional
    public WeeklyTrainingReport explain(User actor, LocalDate date, WeeklyTrainingReport.ExplanationRequest request) {
        requireUser(actor);
        LocalDate week = checkedWeek(date);
        requireRosterMember(week, actor.getId());
        String content = checkedText(request.content());
        String now = time.now().toString();
        repository.ensureRecord(week, actor.getId(), now);
        var record = repository.lockRecord(week, actor.getId()).orElseThrow();
        checkVersion(record, request.version());
        repository.submitExplanation(week, actor.getId(), content, now);
        repository.appendAudit(week, actor.getId(), actor.getId(), "SUBMIT", content,
                record.manualStatus() == null ? "AUTO" : record.manualStatus(), now);
        return report(week, actor.getId());
    }

    @Transactional
    public WeeklyTrainingReport review(User actor, LocalDate date, Long userId, WeeklyTrainingReport.ReviewRequest request) {
        requireAdmin(actor);
        LocalDate week = checkedWeek(date);
        requireRosterMember(week, userId);
        String note = checkedText(request.note());
        String now = time.now().toString();
        repository.ensureRecord(week, userId, now);
        var record = repository.lockRecord(week, userId).orElseThrow();
        checkVersion(record, request.version());
        String reviewStatus;
        String manual = record.manualStatus();
        if ("APPROVE".equals(request.action()) || "REJECT".equals(request.action())) {
            if (!"PENDING".equals(record.reviewStatus())) {
                throw new BusinessException(409, "该说明已处理或尚未提交，请刷新记录");
            }
            reviewStatus = "APPROVE".equals(request.action()) ? "APPROVED" : "REJECTED";
            if ("APPROVE".equals(request.action())) manual = checkedStatus(request.status());
        } else if ("ADJUST".equals(request.action())) {
            if ("PENDING".equals(record.reviewStatus())) {
                throw new BusinessException(409, "请先审核待处理说明，再调整周状态");
            }
            reviewStatus = record.reviewStatus();
            manual = checkedStatus(request.status());
        } else {
            throw new BusinessException(400, "无效的审核操作");
        }
        repository.review(week, userId, reviewStatus, manual, note, actor.getId(), now);
        repository.appendAudit(week, userId, actor.getId(), request.action(), note, manual == null ? "AUTO" : manual, now);
        return report(week, userId);
    }

    private WeeklyTrainingReport report(LocalDate week, Long userId) {
        long start = epoch(week);
        long end = epoch(week.plusDays(7));
        var roster = repository.findRoster(week, start, end, userId);
        var daily = repository.findDaily(week, userId).stream().collect(Collectors.groupingBy(
                WeeklyTrainingRepository.DailyRow::userId, Collectors.toMap(WeeklyTrainingRepository.DailyRow::date, Function.identity())));
        var contests = repository.findContests(start, end);
        var participation = repository.findParticipation(start, end, userId).stream().collect(Collectors.groupingBy(
                WeeklyTrainingRepository.ParticipationRow::userId,
                Collectors.toMap(WeeklyTrainingRepository.ParticipationRow::contestId, Function.identity())));
        var records = repository.findRecords(week, userId).stream().collect(Collectors.toMap(
                WeeklyTrainingRepository.WeekRecord::userId, Function.identity()));
        var history = repository.findHistory(week, userId).stream().collect(Collectors.groupingBy(
                WeeklyTrainingRepository.AuditRow::userId, Collectors.mapping(WeeklyTrainingRepository.AuditRow::audit, Collectors.toList())));
        List<WeeklyTrainingReport.Member> members = new ArrayList<>();
        for (var member : roster) {
            var activity = daily.getOrDefault(member.userId(), Map.of());
            List<WeeklyTrainingReport.Day> days = new ArrayList<>();
            for (int offset = 0; offset < 7; offset++) {
                LocalDate date = week.plusDays(offset);
                var row = activity.get(date);
                boolean completed = row != null && !date.isAfter(time.today());
                String status = completed ? "COMPLETED" : date.isAfter(time.today()) ? "UPCOMING"
                        : date.equals(time.today()) ? "IN_PROGRESS" : "INCOMPLETE";
                days.add(new WeeklyTrainingReport.Day(date.toString(), completed,
                        completed && row.easy(), completed && row.hard(), completed ? row.score() : 0, status));
            }
            var results = participation.getOrDefault(member.userId(), Map.of());
            List<WeeklyTrainingReport.Contest> races = new ArrayList<>();
            for (var contest : contests) {
                var result = results.get(contest.id());
                String status;
                if (result != null && result.exempted()) status = "EXEMPT";
                else if (result == null && contest.frozen()) status = "NOT_REQUIRED";
                else if (time.nowEpochSecond() < contest.start()) status = "UPCOMING";
                else if (result != null && result.status() != null) status = result.status();
                else status = member.atcoderHandle() == null ? "UNBOUND" : "PENDING";
                races.add(new WeeklyTrainingReport.Contest(contest.id(), contest.name(), contest.url(), contest.start(),
                        status, result == null ? null : result.acCount(), result == null ? null : result.checkedAt()));
            }
            String raceStatus = contestStatus(races);
            int completedDays = (int) days.stream().filter(WeeklyTrainingReport.Day::completed).count();
            String automatic = automaticStatus(week, completedDays, raceStatus);
            var record = records.get(member.userId());
            String manual = record == null || record.manualStatus() == null ? "AUTO" : record.manualStatus();
            var explanation = record == null ? new WeeklyTrainingReport.Explanation(null, null, "NONE", null, null, null)
                    : new WeeklyTrainingReport.Explanation(record.explanation(), record.submittedAt(), record.reviewStatus(),
                        record.reviewNote(), record.reviewer(), record.reviewedAt());
            members.add(new WeeklyTrainingReport.Member(member.userId(), member.username(), member.displayName(),
                    member.realName(), member.college(), member.className(), member.avatarUrl(), member.memberType(),
                    automatic, "AUTO".equals(manual) ? automatic : manual, manual, completedDays, List.copyOf(days), raceStatus,
                    List.copyOf(races), explanation, record == null ? 0 : record.version(), true,
                    history.getOrDefault(member.userId(), List.of())));
        }
        return new WeeklyTrainingReport(week.toString(), week.plusDays(6).toString(), time.today().toString(), List.copyOf(members));
    }

    private String automaticStatus(LocalDate week, int days, String contestStatus) {
        boolean raceDone = Set.of("COMPLETED", "EXEMPT", "NOT_REQUIRED").contains(contestStatus);
        if (days == 7 && raceDone) return "COMPLETED";
        if (!week.plusDays(6).isBefore(time.today())) return "IN_PROGRESS";
        if (Set.of("PENDING", "DATA_ERROR", "UPCOMING").contains(contestStatus)) return "PENDING";
        return "INCOMPLETE";
    }

    private String contestStatus(List<WeeklyTrainingReport.Contest> contests) {
        if (contests.isEmpty()) return "PENDING";
        var statuses = contests.stream().map(WeeklyTrainingReport.Contest::status).toList();
        if (statuses.contains("COMPLETED")) return "COMPLETED";
        if (statuses.contains("EXEMPT")) return "EXEMPT";
        if (statuses.stream().allMatch("NOT_REQUIRED"::equals)) return "NOT_REQUIRED";
        for (String status : List.of("DATA_ERROR", "PENDING", "UPCOMING", "UNBOUND", "PARTICIPATED", "ABSENT")) {
            if (statuses.contains(status)) return status;
        }
        return "PENDING";
    }

    public static LocalDate weekStart(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private LocalDate checkedWeek(LocalDate date) {
        LocalDate week = weekStart(date == null ? time.today() : date);
        if (week.getYear() < 2000 || week.isAfter(weekStart(time.today()))) {
            throw new BusinessException(400, "只能查询本周及过去的训练记录");
        }
        return week;
    }

    private void requireRosterMember(LocalDate week, Long userId) {
        if (repository.findRoster(week, epoch(week), epoch(week.plusDays(7)), userId).isEmpty()) {
            throw new BusinessException(404, "该成员在所选周没有现役训练记录");
        }
    }

    private long epoch(LocalDate date) { return date.atStartOfDay(ZONE).toEpochSecond(); }

    private void requireUser(User actor) {
        if (actor == null) throw new BusinessException(401, "请先登录");
    }

    private void requireAdmin(User actor) {
        requireUser(actor);
        if (actor.getRole() != UserRole.ADMIN && actor.getRole() != UserRole.SUPER_ADMIN) {
            throw new BusinessException(403, "需要管理员权限");
        }
    }

    private String checkedText(String text) {
        if (text == null || text.isBlank() || text.trim().length() > 2000) {
            throw new BusinessException(400, "请填写 1～2000 字的说明或处理意见");
        }
        return text.trim();
    }

    private String checkedStatus(String status) {
        if (status == null || !MANUAL_STATUSES.contains(status)) throw new BusinessException(400, "请选择有效的周状态");
        return "AUTO".equals(status) ? null : status;
    }

    private void checkVersion(WeeklyTrainingRepository.WeekRecord record, Long version) {
        if (version == null || record.version() != version) {
            throw new BusinessException(409, "记录已更新，请刷新后重新提交，避免覆盖新的说明或审核结果");
        }
    }
}
