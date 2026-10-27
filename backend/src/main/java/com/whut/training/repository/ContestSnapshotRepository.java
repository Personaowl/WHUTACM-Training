package com.whut.training.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.whut.training.domain.dto.UpcomingContestItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Only public contest schedules are persisted; no user or credential data. */
@Repository
public class ContestSnapshotRepository {
    private static final Logger log = LoggerFactory.getLogger(ContestSnapshotRepository.class);
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public ContestSnapshotRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public List<Snapshot> findAll() {
        return jdbc.query("SELECT platform, payload_json, synced_at FROM contest_source_snapshot", (rs, row) -> {
            try {
                List<UpcomingContestItem> items = mapper.readValue(rs.getString("payload_json"), new TypeReference<>() { });
                return new Snapshot(rs.getString("platform"), List.copyOf(items), Instant.parse(rs.getString("synced_at")));
            } catch (Exception error) {
                log.warn("Ignoring invalid contest snapshot platform={}", rs.getString("platform"));
                return null;
            }
        }).stream().filter(Objects::nonNull).toList();
    }

    public void save(String platform, List<UpcomingContestItem> items, Instant syncedAt) {
        try {
            String payload = mapper.writeValueAsString(items);
            jdbc.update("""
                    INSERT INTO contest_source_snapshot(platform, payload_json, synced_at) VALUES (?, ?, ?)
                    ON DUPLICATE KEY UPDATE payload_json = VALUES(payload_json), synced_at = VALUES(synced_at)
                    """, platform, payload, syncedAt.toString());
        } catch (com.fasterxml.jackson.core.JsonProcessingException error) {
            throw new IllegalStateException("Cannot serialize contest snapshot", error);
        }
    }

    public record Snapshot(String platform, List<UpcomingContestItem> items, Instant syncedAt) { }
}
