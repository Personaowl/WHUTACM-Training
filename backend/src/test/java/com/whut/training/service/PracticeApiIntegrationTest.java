package com.whut.training.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.whut.training.domain.entity.CfProblem;
import com.whut.training.domain.entity.User;
import com.whut.training.domain.enums.UserRole;
import com.whut.training.repository.AuthTokenSessionRepository;
import com.whut.training.repository.PracticeDrawRepository;
import com.whut.training.repository.ProblemCatalogRepository;
import com.whut.training.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PracticeApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired AuthTokenSessionRepository sessions;
    @Autowired ProblemCatalogRepository catalog;
    @Autowired PracticeDrawRepository draws;
    private final CfProblem problem = new CfProblem("999123-A", 999123, "A", "Architecture example", 1200,
            "architecture phase two", false, null, 10, "https://codeforces.com/problemset/problem/999123/A");

    private User user(String name, UserRole role) {
        return users.save(new User(null, name, null, "hash", role));
    }
    private String token(User user) {
        long now = System.currentTimeMillis() / 1000;
        String access = "practice-access-" + user.getId();
        sessions.save(new AuthTokenSessionRepository.AuthTokenSession(user.getId(), access,
                "practice-refresh-" + user.getId(), now + 3600, now + 7200, now));
        return "Bearer " + access;
    }

    @Test void everyPracticeRouteStillRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/practice/tags")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/practice/history")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/practice/draw")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/practice/1")).andExpect(status().isUnauthorized());
    }

    @Test void drawTagsHistoryAndDeleteKeepExistingHttpContracts() throws Exception {
        String auth = token(user("practice-api-flow", UserRole.USER));
        catalog.upsertProblems(List.of(problem));
        mvc.perform(get("/api/practice/tags").header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
        var response = mvc.perform(post("/api/practice/draw").header("Authorization", auth)
                        .contentType("application/json")
                        .content("{\"minRating\":1200,\"maxRating\":1200,\"tags\":[\"architecture phase two\"]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.problem.type").value("PRACTICE"))
                .andExpect(jsonPath("$.data.problem.problemKey").value(problem.problemKey()))
                .andExpect(jsonPath("$.data.problem.rating").value(1200)).andReturn();
        long id = json.readTree(response.getResponse().getContentAsString()).path("data").path("drawId").asLong();
        assertThat(id).isPositive();
        mvc.perform(get("/api/practice/history").header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].drawId").value(id))
                .andExpect(jsonPath("$.data[0].name").value(problem.name()));
        mvc.perform(delete("/api/practice/" + id).header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        mvc.perform(get("/api/practice/history").header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
        mvc.perform(delete("/api/practice/" + id).header("Authorization", auth))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("practice draw not found"));
    }

    @Test void evenSuperAdminCannotReadOrDeleteAnotherUsersPracticeRecords() throws Exception {
        User owner = user("practice-api-owner", UserRole.USER);
        String otherAuth = token(user("practice-api-other", UserRole.SUPER_ADMIN));
        var saved = draws.insertPracticeDraw(owner.getId(), LocalDate.of(2026, 10, 25), problem);
        mvc.perform(get("/api/practice/history").header("Authorization", otherAuth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
        mvc.perform(delete("/api/practice/" + saved.id()).header("Authorization", otherAuth))
                .andExpect(status().isNotFound());
        assertThat(draws.findPracticeDrawsByUserId(owner.getId(), 20)).hasSize(1);
    }
}
