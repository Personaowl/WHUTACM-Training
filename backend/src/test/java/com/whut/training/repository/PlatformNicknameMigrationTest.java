package com.whut.training.repository;

import com.whut.training.config.MySqlInitializer;
import com.whut.training.service.PlatformStatisticsProvider.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import java.sql.DriverManager;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;

/** An isolated in-memory old schema: never connects to a real MySQL service. */
class PlatformNicknameMigrationTest {
    @Test void existingStatisticsSurviveTheNullableNicknameColumnUpgrade() throws Exception {
        try (var connection=DriverManager.getConnection("jdbc:h2:mem:nickname-upgrade;MODE=MySQL;DATABASE_TO_LOWER=TRUE", "sa", "")) {
            var jdbc=new JdbcTemplate(new SingleConnectionDataSource(connection,true));
            jdbc.execute("""
                    CREATE TABLE platform_statistics_snapshot (
                      user_id BIGINT NOT NULL, platform VARCHAR(16) NOT NULL, account_id VARCHAR(64) NOT NULL,
                      rating INT, max_rating INT, solved_count INT, synced_at VARCHAR(64) NOT NULL,
                      partial TINYINT NOT NULL DEFAULT 0, PRIMARY KEY(user_id,platform,account_id))
                    """);
            var time=Instant.now();
            jdbc.update("INSERT INTO platform_statistics_snapshot VALUES (7,'luogu','123',NULL,NULL,1349,?,1)",time.toString());
            new MySqlInitializer(jdbc).init();
            var repository=new PlatformStatisticsSnapshotRepository(jdbc);
            assertThat(repository.find(7L,"luogu","123")).get().satisfies(data -> {
                assertThat(data.displayName()).isNull();assertThat(data.solvedCount()).isEqualTo(1349);
                assertThat(data.syncedAt()).isEqualTo(time);
            });
            repository.save(7L,"luogu","123",new Statistics(null,null,1350,time,true,false,"洛谷昵称"));
            assertThat(repository.find(7L,"luogu","123")).get().satisfies(data -> {
                assertThat(data.displayName()).isEqualTo("洛谷昵称");assertThat(data.solvedCount()).isEqualTo(1350);
            });
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM platform_statistics_snapshot",Integer.class)).isEqualTo(1);
            var info=com.whut.training.domain.dto.PlatformProfileInfo.empty().with("followerCount",0).with("registrationTimeSeconds",1597576705L);
            repository.save(7L,"luogu","123",new Statistics(null,null,1350,time,true,false,"洛谷昵称",info));
            assertThat(repository.find(7L,"luogu","123")).get().satisfies(data->assertThat(data.profileInfo()).isEqualTo(info));
            assertThat(repository.find(7L,"luogu","456")).isEmpty();
            jdbc.update("UPDATE platform_statistics_snapshot SET profile_info_json = ?","{broken");
            assertThat(repository.find(7L,"luogu","123")).get().satisfies(data->{assertThat(data.profileInfo()).isNull();assertThat(data.solvedCount()).isEqualTo(1350);});
        }
    }
}
