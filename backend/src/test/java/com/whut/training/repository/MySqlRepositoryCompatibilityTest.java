package com.whut.training.repository;

import com.whut.training.domain.dto.CodeforcesOverview;
import com.whut.training.domain.entity.CfProblem;
import com.whut.training.domain.entity.PushPoolItem;
import com.whut.training.domain.entity.User;
import com.whut.training.domain.enums.MemberType;
import com.whut.training.domain.enums.UserRole;
import com.whut.training.service.CodeforcesApiService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 使用 H2 MySQL 模式执行项目中的 MySQL 建表和 upsert 方言。
 */
@SpringBootTest
@Transactional
class MySqlRepositoryCompatibilityTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DailyProblemRepository dailyProblemRepository;

    @Autowired
    private PushPoolRepository pushPoolRepository;

    @Autowired
    private CodeforcesProfileSnapshotRepository snapshotRepository;

    @Autowired
    private TrainingDashboardRepository trainingDashboardRepository;

    @Autowired
    private CodeforcesRatingHistoryRepository ratingHistoryRepository;

    @Autowired
    private ProblemListRepository problemListRepository;

    @Autowired
    private AtCoderTrackingRepository atCoderTrackingRepository;

    @Autowired
    private AuthTokenSessionRepository authTokenSessionRepository;

    @Test
    void supportsRegistrationAndAllMySqlUpserts() {
        User superAdmin = new User(null, "ranking-superadmin", null, "password", UserRole.SUPER_ADMIN);
        superAdmin.setDisplayName("Ranking Superadmin");
        superAdmin.setTotalPoints(99_999);
        userRepository.save(superAdmin);

        User user = new User(null, "mysql-user", null, "password", UserRole.USER);
        user.setDisplayName("MySQL User");
        user.setTotalPoints(100);
        user.setAtcoderHandle("mysql_atcoder");
        user.setAtcoderVerifiedAtSeconds(1_754_006_400L);
        userRepository.save(user);
        assertThat(user.getId()).isNotNull();
        authTokenSessionRepository.save(new AuthTokenSessionRepository.AuthTokenSession(
                user.getId(), "raw-access-token", "raw-refresh-token", 2_000L, 3_000L, 1_000L
        ));
        assertThat(authTokenSessionRepository.findByAccessToken("raw-access-token"))
                .get()
                .satisfies(session -> {
                    assertThat(session.accessToken()).isNotEqualTo("raw-access-token");
                    assertThat(session.refreshToken()).isNotEqualTo("raw-refresh-token");
                });
        assertThat(authTokenSessionRepository.findByRefreshToken("raw-refresh-token")).isPresent();
        authTokenSessionRepository.save(new AuthTokenSessionRepository.AuthTokenSession(
                user.getId(), "expired-access-token", "live-refresh-token", 900L, 1_100L, 1_000L
        ));
        authTokenSessionRepository.deleteExpiredBefore(1_000L);
        assertThat(authTokenSessionRepository.findByRefreshToken("live-refresh-token")).isPresent();
        assertThat(userRepository.countRankedUsers()).isLessThan(userRepository.countAll());
        assertThat(userRepository.findTopByTotalPoints(10, 0))
                .extracting(item -> item.getUserId())
                .contains(user.getId())
                .doesNotContain(superAdmin.getId());
        assertThat(userRepository.findByAtcoderHandle("MYSQL_ATCODER"))
                .get()
                .satisfies(saved -> {
                    assertThat(saved.getAtcoderHandle()).isEqualTo("mysql_atcoder");
                    assertThat(saved.getAtcoderVerifiedAtSeconds()).isEqualTo(1_754_006_400L);
                });
        userRepository.updateMemberType(user.getId(), MemberType.ACTIVE_TEAM);

        PushPoolItem pushed = pushPoolRepository.insert(
                "Two pointers", "https://codeforces.com/problemset/problem/100/A", "practice", user.getId()
        );
        assertThat(pushed.id()).isNotNull();
        assertThat(pushPoolRepository.findById(pushed.id()))
                .get()
                .satisfies(item -> {
                    assertThat(item.title()).isEqualTo("Two pointers");
                    assertThat(item.submitterUsername()).isEqualTo("mysql-user");
                    assertThat(item.status()).isEqualTo("PENDING");
                });

        atCoderTrackingRepository.upsertContest(
                "abc460", "AtCoder Beginner Contest 460", 1_780_148_400L, 1_780_155_600L,
                "https://atcoder.jp/contests/abc460"
        );
        atCoderTrackingRepository.prepareActiveMembers("abc460");
        assertThat(atCoderTrackingRepository.findRequirements("abc460"))
                .singleElement()
                .extracting(AtCoderTrackingRepository.RequirementRow::atcoderHandle)
                .isEqualTo("mysql_atcoder");
        atCoderTrackingRepository.freezeActiveMembers("abc460");
        atCoderTrackingRepository.upsertParticipation(
                "abc460", user.getId(), true, 321, 1800, true,
                1700, 1750, 2, "abc460_a,abc460_b", "COMPLETED", null
        );
        assertThat(atCoderTrackingRepository.findRequirements("abc460"))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.atcoderHandle()).isEqualTo("mysql_atcoder");
                    assertThat(row.status()).isEqualTo("COMPLETED");
                    assertThat(row.acCount()).isEqualTo(2);
                });
        assertThat(atCoderTrackingRepository.findExportRows(1_700_000_000L))
                .singleElement()
                .extracting(AtCoderTrackingRepository.AtCoderExportRow::contestId)
                .isEqualTo("abc460");

        CfProblem first = new CfProblem(
                "100-A", 100, "A", "First", 1200, "math", false,
                null, 10, "https://codeforces.com/problemset/problem/100/A"
        );
        CfProblem updated = new CfProblem(
                "100-A", 100, "A", "Updated", 1300, "math,greedy", false,
                null, 20, "https://codeforces.com/problemset/problem/100/A"
        );
        dailyProblemRepository.upsertProblems(List.of(first));
        dailyProblemRepository.upsertProblems(List.of(updated));
        assertThat(dailyProblemRepository.countProblems()).isGreaterThanOrEqualTo(1);
        assertThat(dailyProblemRepository.findRandomProblem(1200, 1400)).isPresent();
        assertThat(dailyProblemRepository.findRandomProblem(1200, 1400, List.of("math", "greedy")))
                .get()
                .extracting(CfProblem::problemKey)
                .isEqualTo("100-A");
        assertThat(dailyProblemRepository.findRandomProblem(1200, 1400, List.of("graphs"))).isEmpty();
        assertThat(dailyProblemRepository.findAvailableProblemTags()).contains("greedy", "math");

        LocalDate date = LocalDate.of(2026, 8, 1);
        dailyProblemRepository.insertDailySlot(date, "easy", updated, "test");
        // 重抽必须让该题位的所有旧有效记录失效，最后插入的新题才是唯一有效题。
        LocalDate redrawDate = date.plusDays(1);
        dailyProblemRepository.insertDailySlot(redrawDate, "hard", first, "test");
        dailyProblemRepository.insertDailySlot(redrawDate, "hard", updated, "test");
        assertThat(dailyProblemRepository.markActiveSlotsRedrawn(redrawDate, "hard")).isEqualTo(2);
        dailyProblemRepository.insertDailySlot(redrawDate, "hard", first, "redraw");
        assertThat(dailyProblemRepository.findDailySlotsByDate(redrawDate))
                .filteredOn(slot -> "hard".equals(slot.slot()) && !slot.isRedrawn())
                .singleElement()
                .extracting(slot -> slot.problemKey())
                .isEqualTo("100-A");
        dailyProblemRepository.deleteDailySlotsByDate(redrawDate);
        dailyProblemRepository.saveUserDailySlotStatus(
                user.getId(), date, "easy", "100-A", 1L, "OK", 1200
        );
        dailyProblemRepository.saveUserDailySlotStatus(
                user.getId(), date, "easy", "100-A", 2L, "OK", 1300
        );
        assertThat(dailyProblemRepository.findUserDailySlotStatus(user.getId(), date, "100-A"))
                .get().extracting(status -> status.score()).isEqualTo(1300);

        dailyProblemRepository.upsertUserDailyStatus(user.getId(), date, 1L, "OK", 1200);
        dailyProblemRepository.upsertUserDailyStatus(user.getId(), date, 2L, "OK", 1300);
        assertThat(dailyProblemRepository.findUserDailyStatus(user.getId(), date))
                .get().extracting(status -> status.score()).isEqualTo(1300);

        dailyProblemRepository.insertPracticeDraw(user.getId(), date, updated);
        assertThat(trainingDashboardRepository.findActiveTeamDailyActivity(date.minusDays(1), date))
                .extracting(TrainingDashboardRepository.DailyActivity::userId)
                .contains(user.getId());
        assertThat(trainingDashboardRepository.findActiveTeamPracticeSummary(date.minusDays(1), date))
                .singleElement()
                .satisfies(summary -> {
                    assertThat(summary.userId()).isEqualTo(user.getId());
                    assertThat(summary.drawCount()).isEqualTo(1);
                    assertThat(summary.solvedCount()).isZero();
                });
        assertThat(trainingDashboardRepository.findActiveTeamDailyExport(date.minusDays(1)))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.userId()).isEqualTo(user.getId());
                    assertThat(row.problemKey()).isEqualTo("100-A");
                    assertThat(row.completed()).isTrue();
                });
        ratingHistoryRepository.replaceForUser(user.getId(), List.of(
                new CodeforcesApiService.CodeforcesRatingChange(
                        2048L, "Codeforces Round", 321, 1450, 1500, 1_754_006_400L
                )
        ));
        assertThat(ratingHistoryRepository.findActiveTeamHistory(1_700_000_000L))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.userId()).isEqualTo(user.getId());
                    assertThat(row.ratingChange()).isEqualTo(50);
                    assertThat(row.contestId()).isEqualTo(2048L);
                });
        assertThat(ratingHistoryRepository.existsByUserId(user.getId())).isTrue();

        Long listId = problemListRepository.createList(user.getId(), "区间 DP", "专题训练", false);
        assertThat(listId).isNotNull();
        assertThat(problemListRepository.findVisible(user.getId()))
                .extracting(com.whut.training.domain.dto.ProblemListSummary::name)
                .contains("区间 DP");
        Long itemId = problemListRepository.addItem(
                listId, "Updated", "https://codeforces.com/problemset/problem/100/A",
                "重做", "100-A", 1300, "math,greedy"
        );
        assertThat(problemListRepository.findItems(listId))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.id()).isEqualTo(itemId);
                    assertThat(item.problemKey()).isEqualTo("100-A");
                    assertThat(item.rating()).isEqualTo(1300);
                });
        assertThat(problemListRepository.findProblemMetadata("100-A"))
                .get()
                .extracting(ProblemListRepository.ProblemMetadata::name)
                .isEqualTo("Updated");

        pushPoolRepository.setDailyPush(date, 10L);
        pushPoolRepository.setDailyPush(date, 11L);
        assertThat(pushPoolRepository.findDailyPushId(date)).contains(11L);

        CodeforcesOverview overview = new CodeforcesOverview(
                "mysql-handle", 1500, 1600, 3, 5, 4, 2,
                100L, false, Instant.parse("2026-08-01T00:00:00Z"), false,
                List.of(), List.of()
        );
        snapshotRepository.save(user.getId(), "mysql-handle", overview);
        snapshotRepository.save(user.getId(), "mysql-handle", overview);
        assertThat(snapshotRepository.find(user.getId(), "mysql-handle")).isPresent();
        assertThat(snapshotRepository.findAllForActiveTeam()).containsKey(user.getId());
    }
}
