package com.whut.training.repository;

import com.whut.training.common.TimeProvider;
import com.whut.training.domain.entity.CfProblem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PracticePerformanceTest {
    private JdbcTemplate jdbc;
    private DailyProblemRepository repository;
    private CfProblem problem(String index, int rating, String tags) {
        return new CfProblem("123-"+index,123,index,"Problem "+index,rating,tags,false,null,10,"https://codeforces.com/problemset/problem/123/"+index);
    }
    @BeforeEach void setup() {
        jdbc=spy(new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:"+java.util.UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","")));
        jdbc.execute("""
                CREATE TABLE cf_problem(id BIGINT AUTO_INCREMENT PRIMARY KEY,problem_key VARCHAR(64) UNIQUE,
                  contest_id INT,problem_index VARCHAR(16),name VARCHAR(255),rating INT,tags VARCHAR(1000),
                  is_interactive INT,source_contest_id INT,solved_count INT,source_url VARCHAR(1000),last_synced_at VARCHAR(64))
                """);
        repository=new DailyProblemRepository(jdbc,mock(TimeProvider.class));
        repository.upsertProblems(List.of(problem("A",800,"math"),problem("B",1800,"dp,math"),problem("C",1900,"dp"),problem("D",3500,"digit dp")));
        clearInvocations(jdbc);
    }
    @Test void practicePreservesRangeAndAllTagsWithoutRandomSorting() {
        for(int n=0;n<10;n++) assertThat(repository.findRandomProblem(800,3500,List.of("DP","math")).orElseThrow().problemKey()).isEqualTo("123-B");
        assertThat(repository.findRandomProblem(800,800).orElseThrow().problemKey()).isEqualTo("123-A");
        assertThat(repository.findRandomProblem(2000,3000)).isEmpty();
        assertThat(repository.findRandomProblem(3500,3500,List.of("dp"))).isEmpty();
        assertThat(repository.findRandomProblem(3500,3500,List.of("digit dp")).orElseThrow().problemKey()).isEqualTo("123-D");
        var executedSql=mockingDetails(jdbc).getInvocations().stream()
                .filter(call -> call.getArguments().length > 0 && call.getArgument(0) instanceof String)
                .map(call -> (String)call.getArgument(0)).toList();
        assertThat(executedSql).noneMatch(sql -> sql.contains("RAND()"));
        assertThat(executedSql).anyMatch(sql -> sql.contains("ORDER BY p.rating, p.id LIMIT 1 OFFSET ?"));
    }
    @Test void tagCatalogIsLoadedOnceForFortyConcurrentReadersAndInvalidatedBySync() throws Exception {
        var pool=Executors.newFixedThreadPool(8);
        try {
            var calls=new java.util.ArrayList<java.util.concurrent.Future<List<String>>>();
            for(int i=0;i<40;i++) calls.add(pool.submit(repository::findAvailableProblemTags));
            for(var call:calls) assertThat(call.get(5,TimeUnit.SECONDS)).containsExactly("digit dp","dp","math");
            verify(jdbc,times(1)).queryForList(startsWith("SELECT tags"),eq(String.class));
            repository.upsertProblems(List.of(problem("E",1200,"graphs")));
            assertThat(repository.findAvailableProblemTags()).contains("graphs");
            verify(jdbc,times(2)).queryForList(startsWith("SELECT tags"),eq(String.class));
            assertThatThrownBy(() -> repository.findAvailableProblemTags().add("tamper")).isInstanceOf(UnsupportedOperationException.class);
        } finally { pool.shutdownNow(); }
    }
}
