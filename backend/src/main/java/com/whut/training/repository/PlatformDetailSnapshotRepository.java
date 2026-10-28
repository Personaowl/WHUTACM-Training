package com.whut.training.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.whut.training.domain.dto.PlatformDetails;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public class PlatformDetailSnapshotRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    public PlatformDetailSnapshotRepository(JdbcTemplate jdbc, ObjectMapper mapper) { this.jdbc = jdbc; this.mapper = mapper; }
    public Optional<PlatformDetails> find(long userId, String platform, String account) {
        return jdbc.query("SELECT payload_json FROM platform_detail_snapshot WHERE user_id=? AND platform=? AND account_id=?",
                (rs, index) -> { try { return mapper.readValue(rs.getString(1), PlatformDetails.class); }
                    catch (Exception invalid) { return null; } }, userId, platform, account).stream().filter(java.util.Objects::nonNull).findFirst();
    }
    public void save(long userId, PlatformDetails details) {
        try {
            String json = mapper.writeValueAsString(details);
            if (json.length() > 262144) throw new IllegalArgumentException("Detail snapshot too large");
            jdbc.update("""
                    INSERT INTO platform_detail_snapshot(user_id,platform,account_id,payload_json,synced_at) VALUES(?,?,?,?,?)
                    ON DUPLICATE KEY UPDATE payload_json=VALUES(payload_json),synced_at=VALUES(synced_at)
                    """, userId, details.platform(), details.accountId(), json, details.syncedAt().toString());
        } catch (com.fasterxml.jackson.core.JsonProcessingException error) { throw new IllegalStateException(error); }
    }
}
