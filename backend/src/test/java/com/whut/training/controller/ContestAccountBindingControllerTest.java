package com.whut.training.controller;

import com.whut.training.context.UserContext;
import com.whut.training.domain.dto.AtCoderBindingResponse;
import com.whut.training.domain.dto.CodeforcesBindingResponse;
import com.whut.training.domain.entity.User;
import com.whut.training.domain.enums.UserRole;
import com.whut.training.exception.BusinessException;
import com.whut.training.exception.GlobalExceptionHandler;
import com.whut.training.service.binding.ContestAccountBindingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ContestAccountBindingControllerTest {
    private final ContestAccountBindingService service = mock(ContestAccountBindingService.class);
    private final ContestAccountBindingController controller = new ContestAccountBindingController(service);
    private MockMvc mvc;

    @BeforeEach void setup() {
        UserContext.clear();
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
    }
    @AfterEach void cleanup() { UserContext.clear(); }

    @Test void anonymousBindingIsStillRejected() {
        assertEquals(401, assertThrows(BusinessException.class, () -> controller.finishCodeforcesBinding(1L)).getCode());
        verifyNoInteractions(service);
    }

    @Test void evenAdministratorsCannotBindAccountsForOtherMembers() {
        for (UserRole role : UserRole.values()) {
            UserContext.setCurrentUser(new User(2L, "other", null, "hash", role));
            assertEquals(403, assertThrows(BusinessException.class, () -> controller.finishCodeforcesBinding(1L)).getCode());
            assertEquals(403, assertThrows(BusinessException.class, () -> controller.finishAtCoderBinding(1L)).getCode());
        }
        verifyNoInteractions(service);
    }

    @Test void codeforcesPathsAndResponseEnvelopeStayCompatible() throws Exception {
        User user = new User(1L, "member", null, "hash", UserRole.USER);
        UserContext.setCurrentUser(user);
        when(service.startCodeforcesBinding(eq(1L), any())).thenReturn(new CodeforcesBindingResponse("tourist", 1120L, "https://codeforces.com/contest/1/problem/A", "proof"));
        when(service.finishCodeforcesBinding(1L)).thenReturn(user);
        mvc.perform(post("/api/users/1/codeforces-binding/start").contentType("application/json").content("{\"handle\":\"tourist\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data.expiresAtSeconds").value(1120));
        mvc.perform(post("/api/users/1/codeforces-binding/finish"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(1)).andExpect(jsonPath("$.data.password").doesNotExist());
    }

    @Test void atcoderPathsAndResponseEnvelopeStayCompatible() throws Exception {
        User user = new User(1L, "member", null, "hash", UserRole.USER);
        UserContext.setCurrentUser(user);
        when(service.startAtCoderBinding(eq(1L), any())).thenReturn(new AtCoderBindingResponse("member", "TOKEN", 1600L, "https://atcoder.jp/users/member", "proof"));
        when(service.finishAtCoderBinding(1L)).thenReturn(user);
        mvc.perform(post("/api/users/1/atcoder-binding/start").contentType("application/json").content("{\"handle\":\"member\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.verificationToken").value("TOKEN"));
        mvc.perform(post("/api/users/1/atcoder-binding/finish"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(1));
    }

    @Test void validationStillRunsBeforeTheBindingService() throws Exception {
        UserContext.setCurrentUser(new User(1L, "member", null, "hash", UserRole.USER));
        mvc.perform(post("/api/users/1/codeforces-binding/start").contentType("application/json").content("{\"handle\":\"\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
