package com.whut.training.controller;

import com.whut.training.context.UserContext;
import com.whut.training.domain.entity.User;
import com.whut.training.domain.enums.UserRole;
import com.whut.training.exception.GlobalExceptionHandler;
import com.whut.training.service.DailyCheckInJobService;
import com.whut.training.service.DailyProblemService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DailyHistoryControllerTest {
    private final DailyProblemService service = mock(DailyProblemService.class);
    private final DailyProblemController controller = new DailyProblemController(service, mock(DailyCheckInJobService.class));

    @AfterEach void clear() { UserContext.clear(); }

    @Test void missingDaysDefaultsToThirtyAndAuthenticationIsStillRequired() throws Exception {
        UserContext.clear();
        var mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(get("/api/daily-problem/history")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
        var user = new User(7L, "history-user", null, "hash", UserRole.USER);
        UserContext.setCurrentUser(user);
        mvc.perform(get("/api/daily-problem/history")).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        verify(service).getHistory(user, 30);
    }
}
