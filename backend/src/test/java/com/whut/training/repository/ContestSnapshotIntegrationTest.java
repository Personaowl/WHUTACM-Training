package com.whut.training.repository;

import com.whut.training.domain.dto.UpcomingContestItem;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Transactional
class ContestSnapshotIntegrationTest {
    @Autowired ContestSnapshotRepository snapshots;
    @Autowired JdbcTemplate jdbc;

    @Test void upsertPreservesPublicContestFieldsAndAnEmptySuccessfulResultReplacesOldData() {
        var time = Instant.parse("2026-10-27T01:00:00Z");
        var item = new UpcomingContestItem("ATCODER", "abc999", "比赛测试", "Algorithm",
                "2031-01-01T12:00:00+09:00", 100, "All", "https://atcoder.jp/contests/abc999");
        snapshots.save("ATCODER", List.of(item), time);
        assertThat(snapshots.findAll()).filteredOn(row -> row.platform().equals("ATCODER"))
                .containsExactly(new ContestSnapshotRepository.Snapshot("ATCODER", List.of(item), time));
        snapshots.save("ATCODER", List.of(), time.plusSeconds(1));
        assertThat(snapshots.findAll()).filteredOn(row -> row.platform().equals("ATCODER"))
                .containsExactly(new ContestSnapshotRepository.Snapshot("ATCODER", List.of(), time.plusSeconds(1)));
    }

    @Test void corruptRowDoesNotHideOtherPlatforms() {
        jdbc.update("INSERT INTO contest_source_snapshot(platform,payload_json,synced_at) VALUES ('BROKEN',?,?)",
                "not JSON", "invalid");
        snapshots.save("LUOGU", List.of(), Instant.now());
        assertThat(snapshots.findAll()).extracting(ContestSnapshotRepository.Snapshot::platform)
                .contains("LUOGU").doesNotContain("BROKEN");
    }
}
