package com.whut.training.repository;

import com.whut.training.domain.dto.LeaderboardItem;
import com.whut.training.domain.entity.User;
import com.whut.training.domain.enums.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Supplier;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Transactional
class LeaderboardPerformanceIntegrationTest {
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;

    private User member(String name, int points, UserRole role) {
        var user = new User(null, name, null, "test-only", role);
        user.setTotalPoints(points);
        user.setAvatarUrl("data:image/webp;base64," + "A".repeat(16384));
        return users.save(user);
    }

    private void status(User user, String date, String checkedAt) {
        jdbc.update("""
                INSERT INTO user_daily_status(user_id,date,submission_id,verdict,checked_at,score)
                VALUES (?,?,1,'OK',?,100)
                """, user.getId(), date, checkedAt);
    }

    @Test void rankingAndPaginationKeepScoresLatestCheckinAvatarsAndSuperAdminExclusion() {
        var a = member("rank-a", 90001, UserRole.USER);
        var b = member("rank-b", 90001, UserRole.ADMIN);
        var c = member("rank-c", 90002, UserRole.USER);
        var d = member("rank-d", 90001, UserRole.USER);
        member("rank-super", 999999, UserRole.SUPER_ADMIN);
        status(a, "2031-01-01", "2031-01-01T00:00:00Z");
        status(a, "2031-01-02", "2031-01-02T00:00:00.123456Z");
        status(b, "2031-01-03", "2031-01-03T00:00:00Z");
        assertThat(users.findTopByTotalPoints(4)).extracting(LeaderboardItem::getUserId)
                .containsExactly(c.getId(), b.getId(), a.getId(), d.getId());
        assertThat(users.findTopByTotalPoints(2, 1)).extracting(LeaderboardItem::getUserId)
                .containsExactly(b.getId(), a.getId());
        assertThat(users.findTopByTotalPoints(2, 1).get(1)).satisfies(item -> {
            assertThat(item.getAvatarUrl()).isEqualTo(a.getAvatarUrl());
            assertThat(item.getTotalPoints()).isEqualTo(90001);
            assertThat(item.getLastCheckinAt()).isEqualTo(java.time.LocalDateTime.parse("2031-01-02T00:00:00.123"));
        });
        jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) connection -> {
            try (var indexes = connection.getMetaData().getIndexInfo(connection.getCatalog(), null,
                    "user_daily_status", false, false)) {
                var columns = new TreeMap<Short, String>();
                while (indexes.next()) {
                    if ("idx_user_daily_status_user_checked".equalsIgnoreCase(indexes.getString("INDEX_NAME")))
                        columns.put(indexes.getShort("ORDINAL_POSITION"), indexes.getString("COLUMN_NAME").toLowerCase());
                }
                assertThat(columns.values()).containsExactly("user_id", "checked_at");
            }
            return null;
        });
    }

    // Synthetic local SQL comparison, not an end-to-end or production concurrency claim.
    @Test
    @EnabledIfEnvironmentVariable(named = "MYSQL_TEST_URL", matches = ".+")
    void compareOriginalAndNarrowRankingOnRealMySql() {
        var statuses = new ArrayList<Object[]>();
        String prefix = "bench-" + UUID.randomUUID().toString().substring(0, 8);
        for (int n = 0; n < 120; n++) {
            var user = member(prefix + n, 10000 + n, UserRole.USER);
            for (int day = 0; day < 180; day++) {
                String date = LocalDate.of(2030, 1, 1).plusDays(day).toString();
                statuses.add(new Object[]{user.getId(), date, date + "T12:00:00Z"});
            }
        }
        jdbc.batchUpdate("""
                INSERT INTO user_daily_status(user_id,date,submission_id,verdict,checked_at,score)
                VALUES (?,?,1,'OK',?,100)
                """, statuses);
        String original = """
                SELECT u.id,u.username,u.display_name,u.avatar_url,u.total_points,
                       (SELECT MAX(checked_at) FROM user_daily_status uds WHERE uds.user_id=u.id) AS last_checkin_at
                FROM users u WHERE u.role <> 'SUPER_ADMIN'
                ORDER BY u.total_points DESC,last_checkin_at DESC LIMIT 50 OFFSET 0
                """;
        var beforeIds = jdbc.queryForList(original).stream().map(row -> ((Number) row.get("id")).longValue()).toList();
        assertThat(users.findTopByTotalPoints(50)).extracting(LeaderboardItem::getUserId).isEqualTo(beforeIds);
        for (int n = 0; n < 3; n++) { jdbc.queryForList(original); users.findTopByTotalPoints(50); }
        double oldMs = medianMillis(() -> jdbc.queryForList(original));
        double newMs = medianMillis(() -> users.findTopByTotalPoints(50));
        System.out.printf(Locale.ROOT,
                "LEADERBOARD_SQL_BENCH users=120 statuses=21600 avatarChars=16384 originalMedianMs=%.3f optimizedMedianMs=%.3f%n",
                oldMs, newMs);
    }

    private double medianMillis(Supplier<?> query) {
        long[] times = new long[9];
        for (int n = 0; n < times.length; n++) {
            long start = System.nanoTime(); query.get(); times[n] = System.nanoTime() - start;
        }
        Arrays.sort(times);
        return times[times.length / 2] / 1_000_000.0;
    }
}
