package com.whut.training.repository;

import com.whut.training.domain.entity.CfProblem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class DailyRatingDrawTest {
    @Autowired ProblemCatalogRepository catalog;
    @Autowired DailyProblemRepository daily;
    @Autowired JdbcTemplate jdbc;
    private final LocalDate date = LocalDate.of(2032, 3, 4);
    private final LocalDate cutoff = date.minusDays(90);

    private CfProblem problem(int index, int rating, boolean interactive, Integer source) {
        return new CfProblem("9000-" + index, 9000, String.valueOf(index), "Draw fixture", rating,
                "math", interactive, source, 1, "https://codeforces.com/problemset/problem/9000/" + index);
    }

    @BeforeEach void fixtures() {
        jdbc.update("DELETE FROM cf_problem");
        var problems = new ArrayList<CfProblem>();
        for (int i = 0; i < 20; i++) problems.add(problem(i, 800, false, null));
        problems.add(problem(20, 900, false, null));
        problems.add(problem(21, 1000, false, null));
        problems.add(problem(22, 1100, true, null));
        problems.add(problem(23, 1200, false, 42));
        catalog.upsertProblems(problems);
        daily.insertDailySlot(date.minusDays(1), "easy", problems.get(21), "test");
    }

    @Test void twentyLowRatedProblemsAndOneHigherProblemStillHaveOnlyTwoRatingChoices() {
        for (int index = 0; index < 2; index++) {
            final int selected = index;
            var chosen = catalog.findDailySlotCandidate(800, 1400, date, cutoff, size -> {
                assertThat(size).isEqualTo(2);
                return selected;
            }).orElseThrow();
            assertThat(chosen.rating()).isEqualTo(index == 0 ? 800 : 900);
        }
    }

    @Test void exhaustedRatingIsSkippedButRelaxedHistoryMakesItEligibleAgain() {
        daily.insertDailySlot(date.minusDays(1), "easy", problem(20, 900, false, null), "test");
        var strict = catalog.findDailySlotCandidate(800, 1400, date, cutoff, size -> {
            assertThat(size).isOne();
            return 0;
        });
        assertThat(strict.orElseThrow().rating()).isEqualTo(800);
        var relaxed = catalog.findDailySlotCandidate(800, 1400, date, null, size -> {
            assertThat(size).isEqualTo(3);
            return 2;
        });
        assertThat(relaxed.orElseThrow().rating()).isEqualTo(1000);
    }

    @Test void sameDayActiveProblemsStayExcludedEvenWithoutHistoryCutoff() {
        daily.insertDailySlot(date, "hard", problem(20, 900, false, null), "old-version");
        assertThat(catalog.findDailySlotCandidateByRating(900, 900, date, null)).isEmpty();
        assertThat(catalog.findDailySlotCandidate(900, 900, date, null)).isEmpty();
    }

    @Test void emptyRangeNeverCallsTheRandomPicker() {
        var called = new AtomicBoolean();
        assertThat(catalog.findDailySlotCandidate(1500, 1900, date, cutoff, size -> {
            called.set(true);
            return 0;
        })).isEmpty();
        assertThat(called).isFalse();
    }

    @Test void midEndpointsAreBothEligibleAndRatingsOutsideTheRangeAreNot() {
        catalog.upsertProblems(List.of(problem(24, 1500, false, null), problem(25, 1900, false, null),
                problem(26, 2000, false, null)));
        for (int index = 0; index < 2; index++) {
            final int selected = index;
            assertThat(catalog.findDailySlotCandidate(1500, 1900, date, cutoff, size -> {
                assertThat(size).isEqualTo(2);
                return selected;
            }).orElseThrow().rating()).isEqualTo(index == 0 ? 1500 : 1900);
        }
    }
}
