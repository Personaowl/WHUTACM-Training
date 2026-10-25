package com.whut.training.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "MYSQL_TEST_URL", matches = ".+")
class RealMySqlSmokeTest {
    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;
    @Test void connectionIsReallyMySqlAndUsesTheDisposableSchema() throws Exception {
        String schema;
        try (var connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("MySQL");
            assertThat(connection.getCatalog()).matches("whut_acm_test_[a-f0-9]{12}");
            schema = connection.getCatalog();
        }
        assertThat(jdbc.queryForObject("SELECT @@character_set_database", String.class)).isEqualTo("utf8mb4");
        String exactScope = " ON `" + schema.replace("_", "\\_") + "`.* TO ";
        var grants = jdbc.queryForList("SHOW GRANTS", String.class);
        assertThat(grants).anyMatch(grant -> grant.contains(exactScope));
        assertThat(grants).allMatch(grant -> grant.startsWith("GRANT USAGE ON *.* TO ") || grant.contains(exactScope));
    }
}
