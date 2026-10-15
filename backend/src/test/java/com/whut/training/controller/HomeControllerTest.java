package com.whut.training.controller;

import com.whut.training.context.UserContext;
import com.whut.training.domain.dto.HomeOverview;
import com.whut.training.domain.entity.User;
import com.whut.training.exception.BusinessException;
import com.whut.training.service.HomeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class HomeControllerTest {
    private HomeService service;
    private HomeController controller;
    private HomeOverview shared;

    @BeforeEach
    void setUp() {
        service = mock(HomeService.class);
        controller = new HomeController(service);
        shared = new HomeOverview();
        shared.setTotalUsers(12);
        shared.setTopUsers(List.of());
        when(service.getOverview(10)).thenReturn(shared);
    }

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    @Test
    void returnsCurrentDatabasePointsWithoutChangingTheExistingResponseShape() throws Exception {
        UserContext.setCurrentUser(user(7L, 12400));
        MockMvcBuilders.standaloneSetup(controller).build().perform(get("/api/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentUserId").value(7))
                .andExpect(jsonPath("$.currentUserPoints").value(12400))
                .andExpect(jsonPath("$.totalUsers").value(12))
                .andExpect(jsonPath("$.topUsers").isArray());
    }

    @Test
    void sharedOverviewCannotLeakAnotherUsersPoints() {
        UserContext.setCurrentUser(user(7L, 12400));
        var first = controller.overview(10);
        UserContext.setCurrentUser(user(8L, 700));
        var second = controller.overview(10);
        assertThat(first.currentUserId()).isEqualTo(7L);
        assertThat(first.currentUserPoints()).isEqualTo(12400);
        assertThat(second.currentUserId()).isEqualTo(8L);
        assertThat(second.currentUserPoints()).isEqualTo(700);
        assertThat(first).isNotSameAs(second);
        assertThat(first.topUsers()).isSameAs(second.topUsers());
    }

    @Test
    void pointsStayFreshEvenWhenThePublicOverviewIsStillCached() {
        UserContext.setCurrentUser(user(7L, 1200));
        assertThat(controller.overview(10).currentUserPoints()).isEqualTo(1200);
        UserContext.setCurrentUser(user(7L, 1900));
        assertThat(controller.overview(10).currentUserPoints()).isEqualTo(1900);
    }

    @Test
    void accountsWithoutPointsReturnZero() {
        UserContext.setCurrentUser(user(7L, null));
        assertThat(controller.overview(10).currentUserPoints()).isZero();
    }

    @Test
    void rejectsMissingAuthenticationBeforeLoadingTheOverview() {
        UserContext.clear();
        assertThatThrownBy(() -> controller.overview(10))
                .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.getCode()).isEqualTo(401));
        verifyNoInteractions(service);
    }

    private User user(Long id, Integer points) {
        User user = new User();
        user.setId(id);
        user.setTotalPoints(points);
        return user;
    }
}
