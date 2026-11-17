package com.whut.training.repository;

import com.whut.training.domain.entity.CfProblem;
import com.whut.training.domain.entity.User;
import com.whut.training.domain.enums.MemberType;
import com.whut.training.domain.enums.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.assertThat;

/** Runs against the default H2 database or the guarded, disposable mysql-test instance. */
@SpringBootTest
@Transactional
class DailyProjectionIntegrationTest {
    @Autowired DailyProblemRepository daily;
    @Autowired TrainingDashboardRepository dashboard;
    @Autowired UserRepository users;
    @Autowired ProblemCatalogRepository catalog;
    private final CfProblem problem = new CfProblem("123-A", 123, "A", "Projection example", 1200,
            "math", false, null, 1, "https://codeforces.com/problemset/problem/123/A");
    private User member(String name) {
        User user = new User(null, name, null, "test-hash", UserRole.USER);
        user.setMemberType(MemberType.ACTIVE_TEAM);
        return users.save(user);
    }

    @Test void slotHistoryAndExportPreserveScoresLongSubmissionIdsAndUncompletedNulls() {
        User user = member("projection-current");
        LocalDate date = LocalDate.of(2031, 2, 1);
        daily.insertDailySlot(date, "easy", problem, "test");
        var hard = new CfProblem("123-B", 123, "B", "Hard", 1900, "dp", false, null, 1,
                "https://codeforces.com/problemset/problem/123/B");
        daily.insertDailySlot(date, "hard", hard, "test");
        daily.saveUserDailySlotStatus(user.getId(), date, "easy", problem.problemKey(), 9007199254740993L, "OK", 1200);
        var history = daily.findDailyHistoryForUser(user.getId(), date, date);
        assertThat(history).filteredOn(row -> row.slot().equals("easy")).singleElement().satisfies(row -> {
            assertThat(row.checkedIn()).isTrue();
            assertThat(row.score()).isEqualTo(1200);
            assertThat(row.submissionId()).isEqualTo(9007199254740993L);
        });
        assertThat(history).filteredOn(row -> row.slot().equals("hard")).singleElement().satisfies(row -> {
            assertThat(row.checkedIn()).isFalse();
            assertThat(row.score()).isNull();
            assertThat(row.submissionId()).isNull();
        });
        var export = dashboard.findActiveTeamDailyExport(date).stream().filter(row -> row.userId().equals(user.getId())).toList();
        assertThat(export).filteredOn(row -> row.slot().equals("easy")).singleElement().satisfies(row -> {
            assertThat(row.score()).isEqualTo(1200);
            assertThat(row.submissionId()).isEqualTo(9007199254740993L);
        });
        assertThat(export).filteredOn(row -> row.slot().equals("hard")).singleElement().satisfies(row -> assertThat(row.score()).isNull());
    }

    @Test void legacyDailyHistoryAndExportKeepTheirOriginalNumericTypes() {
        User user = member("projection-legacy");
        LocalDate date = LocalDate.of(2031, 2, 2);
        daily.insertDailyProblem(date, problem, "test");
        daily.saveUserDailyStatus(user.getId(), date, 9007199254740993L, "OK", 1200);
        assertThat(daily.findDailyHistoryForUser(user.getId(), date, date)).singleElement().satisfies(row -> {
            assertThat(row.slot()).isEqualTo("daily");
            assertThat(row.score()).isEqualTo(1200);
            assertThat(row.submissionId()).isEqualTo(9007199254740993L);
        });
        assertThat(dashboard.findActiveTeamDailyExport(date)).filteredOn(row -> row.userId().equals(user.getId()))
                .singleElement().satisfies(row -> {
                    assertThat(row.slot()).isEqualTo("daily");
                    assertThat(row.score()).isEqualTo(1200);
                    assertThat(row.submissionId()).isEqualTo(9007199254740993L);
                });
    }

    @Test void midDoesNotReuseActiveLegacyHardOrInheritItsCompletion() {
        User user = member("projection-mid");
        LocalDate date = LocalDate.of(2031, 2, 3);
        var legacyHard = new CfProblem("991-A", 991, "A", "Old hard", 1900, "math", false, null, 1,
                "https://codeforces.com/problemset/problem/991/A");
        var mid = new CfProblem("991-B", 991, "B", "New mid", 1900, "dp", false, null, 1,
                "https://codeforces.com/problemset/problem/991/B");
        catalog.upsertProblems(java.util.List.of(legacyHard, mid));
        daily.insertDailySlot(date, "easy", problem, "test");
        daily.insertDailySlot(date, "hard", legacyHard, "test");
        daily.saveUserDailySlotStatus(user.getId(), date, "hard", legacyHard.problemKey(), 50L, "OK", 1900);
        daily.saveUserDailyStatus(user.getId(), date, 50L, "OK", 1900);

        var candidate = catalog.findDailySlotCandidate(1900, 1900, date, date.minusDays(90)).orElseThrow();
        assertThat(candidate.problemKey()).isEqualTo(mid.problemKey());
        daily.insertDailySlot(date, "mid", candidate, "test");
        assertThat(daily.findCurrentDailySlotsByDate(date)).extracting(slot -> slot.slot())
                .containsExactly("easy", "mid", "hard");
        var history = daily.findDailyHistoryForUser(user.getId(), date, date);
        assertThat(history).extracting(row -> row.slot()).containsExactly("easy", "mid", "hard");
        assertThat(history.get(1).checkedIn()).isFalse();
        assertThat(history.get(2).checkedIn()).isTrue();
        assertThat(dashboard.findActiveTeamDailyExport(date)).filteredOn(row -> row.userId().equals(user.getId()))
                .extracting(row -> row.slot()).containsExactly("easy", "mid", "hard");
        assertThat(catalog.findDailySlotCandidate(1900, 1900, date, null)).isEmpty();

        daily.replaceDailySlot(date, "mid", new CfProblem("991-C", 991, "C", "Replacement", 1700, "dp",
                false, null, 1, "https://codeforces.com/problemset/problem/991/C"), "admin-redraw");
        assertThat(daily.findCurrentDailySlotsByDate(date)).extracting(slot -> slot.problemKey())
                .containsExactly(problem.problemKey(), "991-C", legacyHard.problemKey());
        assertThat(daily.findDailySlotsByDate(date)).filteredOn(slot -> slot.isRedrawn())
                .extracting(slot -> slot.problemKey()).containsExactly(mid.problemKey());
    }
}
