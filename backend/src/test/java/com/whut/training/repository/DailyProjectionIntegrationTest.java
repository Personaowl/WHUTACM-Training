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
}
