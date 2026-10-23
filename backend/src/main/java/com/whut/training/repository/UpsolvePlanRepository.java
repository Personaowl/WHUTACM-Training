package com.whut.training.repository;

import com.whut.training.domain.dto.UpsolvePlanView;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@Repository
public class UpsolvePlanRepository {
    private final JdbcTemplate jdbc;
    public UpsolvePlanRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Counts counts(long userId) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(CASE WHEN completed=0 THEN 1 ELSE 0 END),0) AS pending,
                       COALESCE(SUM(CASE WHEN completed=1 THEN 1 ELSE 0 END),0) AS done
                FROM upsolve_plan WHERE user_id=?
                """, (rs, n) -> new Counts(rs.getLong("pending"), rs.getLong("done")), userId);
    }
    public List<UpsolvePlanView> list(long userId, boolean completed, int size, long offset) {
        return jdbc.query("SELECT * FROM upsolve_plan WHERE user_id=? AND completed=? ORDER BY id DESC LIMIT ? OFFSET ?",
                UpsolvePlanRepository::map, userId, completed, size, offset);
    }
    public Optional<UpsolvePlanView> find(long userId, long id) {
        return jdbc.query("SELECT * FROM upsolve_plan WHERE user_id=? AND id=?", UpsolvePlanRepository::map, userId, id).stream().findFirst();
    }
    public long create(long userId, String title, String link, String hash, String note) {
        var keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("INSERT INTO upsolve_plan(user_id,title,link,link_hash,note) VALUES (?,?,?,?,?)", new String[]{"id"});
            statement.setLong(1,userId); statement.setString(2,title); statement.setString(3,link);
            statement.setString(4,hash); statement.setString(5,note); return statement;
        }, keys);
        return java.util.Objects.requireNonNull(keys.getKey()).longValue();
    }
    public int update(long userId, long id, String title, String link, String hash, String note) {
        return jdbc.update("UPDATE upsolve_plan SET title=?,link=?,link_hash=?,note=? WHERE user_id=? AND id=?", title,link,hash,note,userId,id);
    }
    public int complete(long userId, long id, boolean completed) {
        return jdbc.update("""
                UPDATE upsolve_plan SET completed_at=CASE WHEN ? THEN COALESCE(completed_at,CURRENT_TIMESTAMP) ELSE NULL END,
                    completed=? WHERE user_id=? AND id=?
                """, completed,completed,userId,id);
    }
    public int delete(long userId, long id) { return jdbc.update("DELETE FROM upsolve_plan WHERE user_id=? AND id=?", userId,id); }
    private static UpsolvePlanView map(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
        return new UpsolvePlanView(rs.getLong("id"),rs.getString("title"),rs.getString("link"),rs.getString("note"),
                rs.getBoolean("completed"),text(rs.getTimestamp("created_at")),text(rs.getTimestamp("completed_at")));
    }
    private static String text(Timestamp value) { return value == null ? null : value.toInstant().toString(); }
    public record Counts(long pending, long completed) { }
}
