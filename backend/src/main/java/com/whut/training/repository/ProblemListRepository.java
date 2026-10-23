package com.whut.training.repository;

import com.whut.training.domain.dto.ProblemListItemView;
import com.whut.training.domain.dto.ProblemListSummary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

/** 个人题单和共享题单的数据访问层。 */
@Repository
public class ProblemListRepository {

    private final JdbcTemplate jdbcTemplate;

    public ProblemListRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void lockList(Long listId) {
        jdbcTemplate.query("SELECT id FROM problem_list WHERE id = ? FOR UPDATE",
                (rs, rowNum) -> rs.getLong(1), listId);
    }

    public boolean hasOtherContributors(Long listId, Long ownerId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM problem_list_item WHERE list_id = ? AND added_by <> ?",
                Integer.class, listId, ownerId);
        return count != null && count > 0;
    }

    public List<ProblemListSummary> findVisible(Long viewerUserId) {
        return jdbcTemplate.query(
                """
                SELECT l.id, l.owner_user_id, l.name, l.description, l.is_shared,
                       l.created_at, l.updated_at, u.username, u.display_name,
                       COUNT(i.id) AS problem_count
                FROM problem_list l
                INNER JOIN users u ON u.id = l.owner_user_id
                LEFT JOIN problem_list_item i ON i.list_id = l.id
                WHERE l.owner_user_id = ? OR l.is_shared = 1
                GROUP BY l.id, l.owner_user_id, l.name, l.description, l.is_shared,
                         l.created_at, l.updated_at, u.username, u.display_name
                ORDER BY CASE WHEN l.owner_user_id = ? THEN 0 ELSE 1 END,
                         l.updated_at DESC, l.id DESC
                """,
                (rs, rowNum) -> mapSummary(rs, viewerUserId),
                viewerUserId,
                viewerUserId
        );
    }

    public Optional<ProblemListSummary> findById(Long listId, Long viewerUserId) {
        List<ProblemListSummary> rows = jdbcTemplate.query(
                """
                SELECT l.id, l.owner_user_id, l.name, l.description, l.is_shared,
                       l.created_at, l.updated_at, u.username, u.display_name,
                       COUNT(i.id) AS problem_count
                FROM problem_list l
                INNER JOIN users u ON u.id = l.owner_user_id
                LEFT JOIN problem_list_item i ON i.list_id = l.id
                WHERE l.id = ?
                GROUP BY l.id, l.owner_user_id, l.name, l.description, l.is_shared,
                         l.created_at, l.updated_at, u.username, u.display_name
                """,
                (rs, rowNum) -> mapSummary(rs, viewerUserId),
                listId
        );
        return rows.stream().findFirst();
    }

    public List<ProblemListItemView> findItems(Long listId) {
        return jdbcTemplate.query(
                """
                SELECT id, list_id, title, link, note, problem_key, rating, tags, sort_order, added_by, created_at
                FROM problem_list_item
                WHERE list_id = ?
                ORDER BY sort_order ASC, id ASC
                """,
                ProblemListRepository::mapItem,
                listId
        );
    }

    public Optional<ProblemListItemView> findItem(Long listId, Long itemId) {
        List<ProblemListItemView> rows = jdbcTemplate.query(
                """
                SELECT id, list_id, title, link, note, problem_key, rating, tags, sort_order, added_by, created_at
                FROM problem_list_item
                WHERE list_id = ? AND id = ?
                LIMIT 1
                """,
                ProblemListRepository::mapItem,
                listId,
                itemId
        );
        return rows.stream().findFirst();
    }

    public Long createList(Long ownerUserId, String name, String description, boolean shared) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO problem_list(owner_user_id, name, description, is_shared) VALUES (?, ?, ?, ?)",
                    new String[]{"id"}
            );
            statement.setLong(1, ownerUserId);
            statement.setString(2, name);
            statement.setString(3, description);
            statement.setBoolean(4, shared);
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? null : key.longValue();
    }

    public int updateList(Long listId, Long ownerUserId, String name, String description, boolean shared) {
        return jdbcTemplate.update(
                """
                UPDATE problem_list
                SET name = ?, description = ?, is_shared = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND owner_user_id = ?
                """,
                name,
                description,
                shared,
                listId,
                ownerUserId
        );
    }

    public int deleteList(Long listId, Long ownerUserId) {
        jdbcTemplate.update(
                "DELETE FROM problem_list_item WHERE list_id IN (SELECT id FROM problem_list WHERE id = ? AND owner_user_id = ?)",
                listId,
                ownerUserId
        );
        return jdbcTemplate.update(
                "DELETE FROM problem_list WHERE id = ? AND owner_user_id = ?",
                listId,
                ownerUserId
        );
    }

    public Long addItem(
            Long listId,
            String title,
            String link,
            String note,
            String problemKey,
            Integer rating,
            String tags,
            Long addedBy
    ) {
        Integer nextOrder = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(sort_order), -1) + 1 FROM problem_list_item WHERE list_id = ?",
                Integer.class,
                listId
        );
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO problem_list_item(
                        list_id, title, link, note, problem_key, rating, tags, sort_order, added_by
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    new String[]{"id"}
            );
            statement.setLong(1, listId);
            statement.setString(2, title);
            statement.setString(3, link);
            statement.setString(4, note);
            statement.setString(5, problemKey);
            statement.setObject(6, rating);
            statement.setString(7, tags);
            statement.setInt(8, nextOrder == null ? 0 : nextOrder);
            statement.setObject(9, addedBy);
            return statement;
        }, keyHolder);
        touch(listId);
        Number key = keyHolder.getKey();
        return key == null ? null : key.longValue();
    }

    public int updateItem(
            Long itemId,
            Long listId,
            String title,
            String link,
            String note,
            String problemKey,
            Integer rating,
            String tags
    ) {
        int updated = jdbcTemplate.update(
                """
                UPDATE problem_list_item
                SET title = ?, link = ?, note = ?, problem_key = ?, rating = ?, tags = ?
                WHERE id = ? AND list_id = ?
                """,
                title,
                link,
                note,
                problemKey,
                rating,
                tags,
                itemId,
                listId
        );
        if (updated > 0) touch(listId);
        return updated;
    }

    public int deleteItem(Long itemId, Long listId) {
        int deleted = jdbcTemplate.update(
                "DELETE FROM problem_list_item WHERE id = ? AND list_id = ?",
                itemId,
                listId
        );
        if (deleted > 0) touch(listId);
        return deleted;
    }

    public boolean itemLinkExists(Long listId, String link, Long excludedItemId) {
        Integer count = excludedItemId == null
                ? jdbcTemplate.queryForObject(
                        "SELECT COUNT(1) FROM problem_list_item WHERE list_id = ? AND link = ?",
                        Integer.class,
                        listId,
                        link
                )
                : jdbcTemplate.queryForObject(
                        "SELECT COUNT(1) FROM problem_list_item WHERE list_id = ? AND link = ? AND id <> ?",
                        Integer.class,
                        listId,
                        link,
                        excludedItemId
                );
        return count != null && count > 0;
    }

    public Optional<ProblemMetadata> findProblemMetadata(String problemKey) {
        if (problemKey == null || problemKey.isBlank()) return Optional.empty();
        List<ProblemMetadata> rows = jdbcTemplate.query(
                "SELECT problem_key, name, rating, tags, source_url FROM cf_problem WHERE problem_key = ? LIMIT 1",
                (rs, rowNum) -> new ProblemMetadata(
                        rs.getString("problem_key"),
                        rs.getString("name"),
                        (Integer) rs.getObject("rating"),
                        rs.getString("tags"),
                        rs.getString("source_url")
                ),
                problemKey
        );
        return rows.stream().findFirst();
    }

    private void touch(Long listId) {
        jdbcTemplate.update(
                "UPDATE problem_list SET updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                listId
        );
    }

    private static ProblemListSummary mapSummary(java.sql.ResultSet rs, Long viewerUserId) throws java.sql.SQLException {
        Long ownerUserId = rs.getLong("owner_user_id");
        return new ProblemListSummary(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("description"),
                ownerUserId,
                rs.getString("username"),
                rs.getString("display_name"),
                rs.getBoolean("is_shared"),
                rs.getInt("problem_count"),
                ownerUserId.equals(viewerUserId),
                timestampText(rs.getTimestamp("created_at")),
                timestampText(rs.getTimestamp("updated_at"))
        );
    }

    private static ProblemListItemView mapItem(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new ProblemListItemView(
                rs.getLong("id"),
                rs.getLong("list_id"),
                rs.getString("title"),
                rs.getString("link"),
                rs.getString("note"),
                rs.getString("problem_key"),
                (Integer) rs.getObject("rating"),
                rs.getString("tags"),
                rs.getInt("sort_order"),
                rs.getObject("added_by", Long.class),
                timestampText(rs.getTimestamp("created_at"))
        );
    }

    private static String timestampText(Timestamp value) {
        return value == null ? null : value.toInstant().toString();
    }

    public record ProblemMetadata(
            String problemKey,
            String name,
            Integer rating,
            String tags,
            String sourceUrl
    ) {
    }
}
