package com.whut.training.service;

import com.whut.training.common.TimeProvider;
import com.whut.training.domain.dto.WeeklyTrainingReport;
import com.whut.training.domain.entity.User;
import com.whut.training.domain.enums.MemberType;
import com.whut.training.domain.enums.UserRole;
import com.whut.training.exception.BusinessException;
import com.whut.training.repository.AtCoderTrackingRepository;
import com.whut.training.repository.AuthTokenSessionRepository;
import com.whut.training.repository.UserRepository;
import com.whut.training.repository.WeeklyTrainingRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"app.weekly-training.roster-initial-delay-ms=600000", "app.atcoder-tracking.initial-delay-ms=600000"})
@AutoConfigureMockMvc
class WeeklyTrainingServiceTest {
    @Autowired WeeklyTrainingService service;
    @SpyBean WeeklyTrainingRepository repository;
    @Autowired UserRepository users;
    @Autowired AtCoderTrackingRepository abc;
    @Autowired AuthTokenSessionRepository sessions;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @MockBean TimeProvider time;
    private final LocalDate week = LocalDate.of(2026, 10, 5);
    private User member;
    private User admin;

    @BeforeEach
    void prepare() {
        when(time.today()).thenReturn(LocalDate.of(2026, 10, 15));
        when(time.now()).thenReturn(LocalDateTime.of(2026, 10, 15, 12, 0));
        when(time.nowEpochSecond()).thenReturn(epoch(LocalDate.of(2026, 10, 15)) + 12 * 3600);
        member = createUser("member", UserRole.USER, MemberType.ACTIVE_TEAM);
        admin = createUser("admin", UserRole.ADMIN, MemberType.REGULAR);
    }

    @AfterEach
    void cleanup() {
        for (String table : List.of("weekly_training_audit", "weekly_training_record", "user_daily_slot_status",
                "user_daily_status", "auth_token_session", "atcoder_participation", "atcoder_requirement_member")) {
            jdbc.update("DELETE FROM " + table + " WHERE user_id IN (SELECT id FROM users WHERE username LIKE 'weekly-test-%')");
        }
        jdbc.update("DELETE FROM atcoder_contest WHERE contest_id LIKE 'weekly-test-%'");
        jdbc.update("DELETE FROM users WHERE username LIKE 'weekly-test-%'");
    }

    private User createUser(String suffix, UserRole role, MemberType type) {
        User user = new User(null, "weekly-test-" + suffix + "-" + UUID.randomUUID(), null, "hash", role);
        user.setMemberType(type);
        user.setAtcoderHandle("handle-" + UUID.randomUUID());
        return users.save(user);
    }

    private long epoch(LocalDate date) { return date.atStartOfDay(ZoneId.of("Asia/Shanghai")).toEpochSecond(); }

    private WeeklyTrainingReport.Member personal() { return service.personal(member, week).members().get(0); }

    private String token(User user) {
        String token = "weekly-token-" + UUID.randomUUID();
        sessions.save(new AuthTokenSessionRepository.AuthTokenSession(user.getId(), token, token + "-refresh",
                time.nowEpochSecond() + 3600, time.nowEpochSecond() + 7200, time.nowEpochSecond()));
        return "Bearer " + token;
    }

    private void daily(LocalDate date, String slot, long submissionId) {
        jdbc.update("""
                INSERT INTO user_daily_slot_status (user_id, date, slot, problem_key, submission_id, verdict, checked_at, score)
                VALUES (?, ?, ?, ?, ?, 'OK', ?, ?)
                """, member.getId(), date.toString(), slot, "test-" + slot, submissionId, time.now().toString(),
                "hard".equals(slot) ? 1900 : 1200);
    }

    private String contest(String suffix, String status) {
        String id = "weekly-test-" + suffix;
        abc.upsertContest(id, id, epoch(week.plusDays(5)) + 21 * 3600,
                epoch(week.plusDays(5)) + 23 * 3600, "https://atcoder.jp/contests/abc001");
        abc.freezeActiveMembers(id);
        abc.upsertParticipation(id, member.getId(), "COMPLETED".equals(status), 1, 1000, true,
                800, 900, "COMPLETED".equals(status) ? 2 : 0, "", status, null);
        return id;
    }

    @Test
    void mondaySundayBoundariesAndDailyDeduplicationAreCorrect() {
        daily(week.minusDays(1), "easy", 1);
        daily(week, "easy", 2);
        daily(week, "hard", 3);
        daily(week.plusDays(6), "easy", 4);
        daily(week.plusDays(7), "easy", 5);
        abc.upsertContest("weekly-test-last-second", "Sunday contest", epoch(week.plusDays(7)) - 1,
                epoch(week.plusDays(7)) + 6000, "https://atcoder.jp/contests/abc001");
        abc.upsertContest("weekly-test-next-week", "Monday contest", epoch(week.plusDays(7)),
                epoch(week.plusDays(7)) + 6000, "https://atcoder.jp/contests/abc002");
        jdbc.update("""
                INSERT INTO user_daily_status (user_id, date, submission_id, verdict, checked_at, score)
                VALUES (?, ?, 6, 'OK', ?, 1900)
                """, member.getId(), week.toString(), time.now().toString());
        var report = service.personal(member, week.plusDays(6));
        assertThat(report.weekStart()).isEqualTo("2026-10-05");
        assertThat(report.weekEnd()).isEqualTo("2026-10-11");
        assertThat(report.members().get(0).dailyCompletedDays()).isEqualTo(2);
        assertThat(report.members().get(0).days()).hasSize(7);
        assertThat(report.members().get(0).contests()).extracting(WeeklyTrainingReport.Contest::contestId)
                .containsExactly("weekly-test-last-second");
        var monday = report.members().get(0).days().get(0);
        assertThat(monday.easyCompleted()).isTrue();
        assertThat(monday.hardCompleted()).isTrue();
        assertThat(monday.score()).isEqualTo(1900);
        assertThat(WeeklyTrainingService.weekStart(LocalDate.of(2027, 1, 3))).isEqualTo(LocalDate.of(2026, 12, 28));
    }

    @Test
    void oneOfMultipleContestsIsEnoughAndMissingOrErroredDataRemainsPending() {
        for (int i = 0; i < 7; i++) daily(week.plusDays(i), "easy", 100 + i);
        assertThat(personal().effectiveStatus()).isEqualTo("PENDING");
        contest("error", "DATA_ERROR");
        assertThat(personal().effectiveStatus()).isEqualTo("PENDING");
        contest("done", "COMPLETED");
        assertThat(personal().effectiveStatus()).isEqualTo("COMPLETED");
        assertThat(personal().contests()).hasSize(2);
    }

    @Test
    void currentWeekIsInProgressAndFutureDaysNeverCountAsMissedOrCompleted() {
        LocalDate currentWeek = LocalDate.of(2026, 10, 12);
        daily(currentWeek.plusDays(5), "easy", 200);
        var report = service.personal(member, currentWeek).members().get(0);
        assertThat(report.automaticStatus()).isEqualTo("IN_PROGRESS");
        assertThat(report.dailyCompletedDays()).isZero();
        assertThat(report.days().get(5).status()).isEqualTo("UPCOMING");
        assertThatThrownBy(() -> service.personal(member, currentWeek.plusWeeks(1)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void explanationApprovalAndAutomaticRestorePreserveRawTrainingAndAuditHistory() {
        contest("absent", "ABSENT");
        daily(week, "easy", 300);
        var submitted = service.explain(member, week, new WeeklyTrainingReport.ExplanationRequest("  请假  ", 0L)).members().get(0);
        assertThat(submitted.explanation().content()).isEqualTo("请假");
        assertThat(submitted.explanation().reviewStatus()).isEqualTo("PENDING");
        var approved = service.review(admin, week, member.getId(), new WeeklyTrainingReport.ReviewRequest(
                "APPROVE", "EXEMPT", "核实请假，本周豁免", submitted.version())).members().get(0);
        assertThat(approved.effectiveStatus()).isEqualTo("EXEMPT");
        assertThat(approved.automaticStatus()).isEqualTo("INCOMPLETE");
        assertThat(approved.explanation().reviewer()).isEqualTo(admin.getUsername());
        assertThat(approved.history()).hasSize(2);
        assertThat(approved.dailyCompletedDays()).isEqualTo(1);
        assertThat(users.findById(member.getId()).orElseThrow().getTotalPoints()).isZero();
        var restored = service.review(admin, week, member.getId(), new WeeklyTrainingReport.ReviewRequest(
                "ADJUST", "AUTO", "撤销误操作，恢复自动判定", approved.version())).members().get(0);
        assertThat(restored.effectiveStatus()).isEqualTo("INCOMPLETE");
        assertThat(restored.manualStatus()).isEqualTo("AUTO");
        assertThat(restored.history()).hasSize(3);
        assertThat(service.personal(member, week.plusWeeks(1)).members().get(0).manualStatus()).isEqualTo("AUTO");
        assertThat(service.personal(member, week.plusWeeks(1)).members().get(0).history()).isEmpty();
    }

    @Test
    void updatingAnExplanationInvalidatesStaleReviewsWithoutRemovingAnExistingOverride() {
        var original = service.explain(member, week, new WeeklyTrainingReport.ExplanationRequest("请假", 0L)).members().get(0);
        var approved = service.review(admin, week, member.getId(), new WeeklyTrainingReport.ReviewRequest(
                "APPROVE", "EXEMPT", "同意", original.version())).members().get(0);
        var updated = service.explain(member, week, new WeeklyTrainingReport.ExplanationRequest("补充情况", approved.version())).members().get(0);
        assertThat(updated.explanation().reviewStatus()).isEqualTo("PENDING");
        assertThat(updated.effectiveStatus()).isEqualTo("EXEMPT");
        assertThatThrownBy(() -> service.review(admin, week, member.getId(), new WeeklyTrainingReport.ReviewRequest(
                "REJECT", null, "旧页面审核", approved.version())))
                .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getCode()).isEqualTo(409));
        assertThat(personal().explanation().content()).isEqualTo("补充情况");
        assertThat(personal().history()).hasSize(3);
        var rejected = service.review(admin, week, member.getId(), new WeeklyTrainingReport.ReviewRequest(
                "REJECT", null, "需补充证明", updated.version())).members().get(0);
        assertThat(rejected.explanation().reviewStatus()).isEqualTo("REJECTED");
        assertThat(rejected.effectiveStatus()).isEqualTo("EXEMPT");
    }

    @Test
    void concurrentReviewersCannotBothOverwriteTheSameRevision() throws Exception {
        var record = service.explain(member, week, new WeeklyTrainingReport.ExplanationRequest("冲突说明", 0L)).members().get(0);
        var executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> approve = () -> {
                try {
                    service.review(admin, week, member.getId(), new WeeklyTrainingReport.ReviewRequest(
                            "APPROVE", "EXEMPT", "已核实", record.version()));
                    return 200;
                } catch (BusinessException failure) { return failure.getCode(); }
            };
            var results = executor.invokeAll(List.of(approve, approve), 10, TimeUnit.SECONDS);
            assertThat(List.of(results.get(0).get(), results.get(1).get())).containsExactlyInAnyOrder(200, 409);
        } finally { executor.shutdownNow(); }
        assertThat(personal().history()).hasSize(2);
    }

    @Test
    void failureToWriteAuditRollsBackTheExplanationToo() {
        doThrow(new DataIntegrityViolationException("simulated audit failure")).when(repository)
                .appendAudit(any(), anyLong(), anyLong(), anyString(), anyString(), nullable(String.class), anyString());
        assertThatThrownBy(() -> service.explain(member, week,
                new WeeklyTrainingReport.ExplanationRequest("不能丢失审计", 0L)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(personal().explanation().reviewStatus()).isEqualTo("NONE");
        assertThat(personal().version()).isZero();
        assertThat(personal().history()).isEmpty();
    }

    @Test
    void capturedRosterKeepsHistoricalRecordsAfterMemberRetires() {
        service.captureCurrentRoster();
        member.setMemberType(MemberType.REGULAR);
        users.save(member);
        assertThat(service.personal(member, LocalDate.of(2026, 10, 12)).members()).hasSize(1);
        assertThat(service.personal(member, week).members()).isEmpty();
        assertThatThrownBy(() -> service.explain(member, week,
                new WeeklyTrainingReport.ExplanationRequest("不存在的周", 0L)))
                .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getCode()).isEqualTo(404));
    }

    @Test
    void httpSubmissionAndReviewRoundTripKeepsChineseTextAndReturnsTheUpdatedVersion() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        mvc.perform(post("/api/weekly-training/me/explanation").param("week", week.toString())
                        .header("Authorization", token(member)).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(new WeeklyTrainingReport.ExplanationRequest("考试冲突，请假一天", 0L))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.members[0].version").value(1))
                .andExpect(result -> assertThat(mapper.readTree(result.getResponse().getContentAsByteArray())
                        .at("/data/members/0/explanation/content").asText()).isEqualTo("考试冲突，请假一天"));
        mvc.perform(post("/api/admin/weekly-training/" + member.getId() + "/review").param("week", week.toString())
                        .header("Authorization", token(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(new WeeklyTrainingReport.ReviewRequest("APPROVE", "EXEMPT", "核实无误", 1L))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.members[0].effectiveStatus").value("EXEMPT"))
                .andExpect(jsonPath("$.data.members[0].version").value(2));
        mvc.perform(get("/api/weekly-training/me").param("week", week.toString()).header("Authorization", token(member)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.members[0].explanation.reviewStatus").value("APPROVED"));
    }

    @Test
    void httpPrivacyAdminAuthorizationAndInputValidationAreEnforced() throws Exception {
        User other = createUser("other", UserRole.USER, MemberType.ACTIVE_TEAM);
        service.explain(other, week, new WeeklyTrainingReport.ExplanationRequest("私密情况", 0L));
        String bearer = token(member);
        mvc.perform(get("/api/weekly-training/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/weekly-training/me").param("week", week.toString()).param("userId", other.getId().toString())
                        .header("Authorization", bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.members.length()").value(1))
                .andExpect(jsonPath("$.data.members[0].userId").value(member.getId()))
                .andExpect(result -> assertThat(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                        .doesNotContain("私密情况"));
        mvc.perform(get("/api/admin/weekly-training").header("Authorization", bearer)).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/weekly-training/" + other.getId() + "/review").param("week", week.toString())
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"APPROVE\",\"status\":\"EXEMPT\",\"note\":\"假审核\",\"version\":1}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/weekly-training").param("week", week.toString()).header("Authorization", token(admin)))
                .andExpect(status().isOk()).andExpect(result -> assertThat(result.getResponse()
                        .getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).contains("私密情况"));
        mvc.perform(get("/api/weekly-training/me").param("week", "bad-date").header("Authorization", bearer))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/weekly-training/me/explanation").param("week", week.toString())
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON).content("{\"content\":\" \",\"version\":0}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/weekly-training/me/explanation").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"原因\",\"version\":0}"))
                .andExpect(status().isBadRequest());
    }
}
