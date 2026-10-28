package com.whut.training.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.whut.training.domain.dto.PlatformProfileInfo;
import com.whut.training.service.PlatformStatisticsProvider.Statistics;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public class PlatformStatisticsSnapshotRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper = new ObjectMapper();
    public PlatformStatisticsSnapshotRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<Statistics> find(Long userId, String platform, String account) {
        return jdbc.query("""
                SELECT rating, max_rating, solved_count, synced_at, partial, display_name, profile_info_json
                FROM platform_statistics_snapshot WHERE user_id = ? AND platform = ? AND account_id = ?
                """, (rs, row) -> new Statistics(rs.getObject("rating", Integer.class), rs.getObject("max_rating", Integer.class),
                rs.getObject("solved_count", Integer.class), Instant.parse(rs.getString("synced_at")), rs.getBoolean("partial"),
                false, rs.getString("display_name"), readProfileInfo(rs.getString("profile_info_json"))),
                userId, platform, account).stream().findFirst();
    }

    public void save(Long userId, String platform, String account, Statistics data) {
        jdbc.update("""
                INSERT INTO platform_statistics_snapshot(user_id, platform, account_id, rating, max_rating, solved_count, synced_at, partial, display_name, profile_info_json)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE account_id = VALUES(account_id), rating = VALUES(rating),
                    max_rating = VALUES(max_rating), solved_count = VALUES(solved_count), synced_at = VALUES(synced_at), partial = VALUES(partial),
                    display_name = VALUES(display_name), profile_info_json = VALUES(profile_info_json)
                """, userId, platform, account, data.rating(), data.maxRating(), data.solvedCount(), data.syncedAt().toString(), data.partial(), data.displayName(), writeProfileInfo(data.profileInfo()));
    }

    private PlatformProfileInfo readProfileInfo(String json) {
        if (json == null || json.length() > 8192) return null;
        try { return mapper.readValue(json, PlatformProfileInfo.class); }
        catch (java.io.IOException invalid) { return null; }
    }

    private String writeProfileInfo(PlatformProfileInfo info) {
        if (info == null) return null;
        try { return mapper.writeValueAsString(info); }
        catch (java.io.IOException invalid) { throw new IllegalArgumentException("Invalid public profile info", invalid); }
    }
}
