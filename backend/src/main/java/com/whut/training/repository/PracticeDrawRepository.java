package com.whut.training.repository;

import com.whut.training.domain.entity.CfProblem;
import com.whut.training.domain.entity.UserPracticeDraw;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** Stores practice snapshots; every user-facing query/delete is scoped to its owner. */
@Repository
public class PracticeDrawRepository {
    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<UserPracticeDraw> userPracticeDrawRowMapper = (rs, rowNum) -> new UserPracticeDraw(
            rs.getLong("id"),
            rs.getLong("user_id"),
            LocalDate.parse(rs.getString("draw_date")),
            rs.getString("problem_key"),
            rs.getInt("contest_id"),
            rs.getString("problem_index"),
            rs.getString("name"),
            (Integer) rs.getObject("rating"),
            rs.getString("tags"),
            rs.getString("source_url"),
            (Long) rs.getObject("submission_id"),
            rs.getString("verdict")
    );


    public PracticeDrawRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 插入练习题抽题记录。
     *
     * @param userId   用户 ID。
     * @param drawDate 抽题日期。
     * @param problem  题目实体。
     * @return 抽题记录。
     */
    public UserPracticeDraw insertPracticeDraw(Long userId, LocalDate drawDate, CfProblem problem) {
        String sql = """
                INSERT INTO user_practice_draw (
                    user_id, draw_date, problem_key, contest_id, problem_index, name, rating,
                    tags, source_url, drawn_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        String nowIso = OffsetDateTime.now().toString();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, userId);
            statement.setString(2, drawDate.toString());
            statement.setString(3, problem.problemKey());
            statement.setInt(4, problem.contestId());
            statement.setString(5, problem.problemIndex());
            statement.setString(6, problem.name());
            statement.setObject(7, problem.rating());
            statement.setString(8, problem.tags());
            statement.setString(9, problem.sourceUrl());
            statement.setString(10, nowIso);
            return statement;
        }, keyHolder);
        Long id = keyHolder.getKey() == null ? null : keyHolder.getKey().longValue();

        return new UserPracticeDraw(
                id,
                userId,
                drawDate,
                problem.problemKey(),
                problem.contestId(),
                problem.problemIndex(),
                problem.name(),
                problem.rating(),
                problem.tags(),
                problem.sourceUrl(),
                null,
                null
        );
    }

    public List<UserPracticeDraw> findPracticeDrawsByUserId(Long userId, int limit) {
        return jdbcTemplate.query(
                "SELECT id, user_id, draw_date, problem_key, contest_id, problem_index, name, rating, tags, source_url, drawn_at, submission_id, verdict, checked_at FROM user_practice_draw WHERE user_id = ? ORDER BY drawn_at DESC LIMIT ?",
                userPracticeDrawRowMapper,
                userId,
                limit
        );
    }

    public boolean deletePracticeDraw(Long drawId, Long userId) {
        int rows = jdbcTemplate.update(
                "DELETE FROM user_practice_draw WHERE id = ? AND user_id = ?",
                drawId, userId
        );
        return rows > 0;
    }
}
