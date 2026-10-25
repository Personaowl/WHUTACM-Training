package com.whut.training.repository;

import com.whut.training.domain.entity.CfProblem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.assertThat;

class PracticeDrawRepositoryTest {
    private JdbcTemplate jdbc;
    private PracticeDrawRepository repository;
    private final LocalDate date = LocalDate.of(2026, 10, 25);
    private final CfProblem problem = new CfProblem("123-A", 123, "A", "Saved example", 1200,
            "math", false, null, 10, "https://codeforces.com/problemset/problem/123/A");

    @BeforeEach void setup() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:" + java.util.UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("""
                CREATE TABLE user_practice_draw (
                  id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT, draw_date VARCHAR(32),
                  problem_key VARCHAR(64), contest_id INT, problem_index VARCHAR(16), name VARCHAR(255),
                  rating INT, tags VARCHAR(1000), source_url VARCHAR(1000), drawn_at VARCHAR(64),
                  submission_id BIGINT, verdict VARCHAR(64), checked_at VARCHAR(64))
                """);
        repository = new PracticeDrawRepository(jdbc);
    }

    @Test void insertAndReadKeepTheSavedSnapshotWithoutJoiningTheLiveCatalog() {
        var inserted = repository.insertPracticeDraw(7L, date, problem);
        assertThat(inserted.id()).isNotNull();
        assertThat(repository.findPracticeDrawsByUserId(7L, 20)).containsExactly(inserted);
        assertThat(repository.findPracticeDrawsByUserId(8L, 20)).isEmpty();
    }

    @Test void historyKeepsLatestFirstLimitAndSubmissionFields() {
        var first = repository.insertPracticeDraw(7L, date, problem);
        var second = repository.insertPracticeDraw(7L, date, problem);
        repository.insertPracticeDraw(8L, date, problem);
        jdbc.update("UPDATE user_practice_draw SET drawn_at = ? WHERE id = ?", "2026-10-25T10:00:00Z", first.id());
        jdbc.update("UPDATE user_practice_draw SET drawn_at = ?, submission_id = ?, verdict = ? WHERE id = ?",
                "2026-10-25T11:00:00Z", 9007199254740993L, "OK", second.id());
        assertThat(repository.findPracticeDrawsByUserId(7L, 1)).singleElement().satisfies(row -> {
            assertThat(row.id()).isEqualTo(second.id());
            assertThat(row.submissionId()).isEqualTo(9007199254740993L);
            assertThat(row.verdict()).isEqualTo("OK");
        });
    }

    @Test void deleteRequiresBothDrawIdAndOwnerAndMissingRecordsReturnFalse() {
        var saved = repository.insertPracticeDraw(7L, date, problem);
        assertThat(repository.deletePracticeDraw(saved.id(), 8L)).isFalse();
        assertThat(repository.findPracticeDrawsByUserId(7L, 20)).hasSize(1);
        assertThat(repository.deletePracticeDraw(saved.id(), 7L)).isTrue();
        assertThat(repository.deletePracticeDraw(saved.id(), 7L)).isFalse();
        assertThat(repository.findPracticeDrawsByUserId(7L, 20)).isEmpty();
    }
}
