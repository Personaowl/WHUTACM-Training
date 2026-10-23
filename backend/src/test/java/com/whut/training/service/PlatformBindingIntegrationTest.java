package com.whut.training.service;

import com.whut.training.domain.entity.User;
import com.whut.training.domain.enums.UserRole;
import com.whut.training.exception.BusinessException;
import com.whut.training.repository.UserRepository;
import com.whut.training.repository.AuthTokenSessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PlatformBindingIntegrationTest {
    @Autowired PlatformBindingService service;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired AuthTokenSessionRepository sessions;
    @MockBean PublicProfileVerifier verifier;
    private User user(String name) { return users.save(new User(null,name,null,"password",UserRole.USER)); }
    private void code(Runnable action, int expected) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getCode()).isEqualTo(expected));
    }
    @Test void onlyValidProofCreatesBindingAndItIsUniqueAndSingleUse() {
        User a=user("platform-a"), b=user("platform-b");
        var challenge=service.start(a.getId(),"luogu","123");
        assertThat(challenge.accountId()).isNull();
        when(verifier.introduction("luogu","123")).thenReturn(challenge.verificationToken());
        var bound=service.finish(a.getId(),"luogu");
        assertThat(bound.accountId()).isEqualTo("123");
        assertThat(bound.verifiedAtSeconds()).isNotNull();
        assertThat(bound.verificationToken()).isNull();
        code(() -> service.finish(a.getId(),"luogu"),400);
        var duplicate=service.start(b.getId(),"luogu","123");
        when(verifier.introduction("luogu","123")).thenReturn(duplicate.verificationToken());
        code(() -> service.finish(b.getId(),"luogu"),409);
        assertThat(service.get(a.getId(),"luogu").accountId()).isEqualTo("123");
    }
    @Test void mismatchesExpiryAndSourceErrorsNeverBindAndAttemptsAreThrottled() {
        User a=user("platform-errors");
        service.start(a.getId(),"nowcoder","456");
        when(verifier.introduction("nowcoder","456")).thenReturn("other introduction");
        code(() -> service.finish(a.getId(),"nowcoder"),400);
        code(() -> service.finish(a.getId(),"nowcoder"),429);
        assertThat(service.get(a.getId(),"nowcoder").accountId()).isNull();
        jdbc.update("UPDATE user_platform_binding SET last_attempt_at=0 WHERE user_id=?",a.getId());
        when(verifier.introduction("nowcoder","456")).thenThrow(new BusinessException(503,"unavailable"));
        code(() -> service.finish(a.getId(),"nowcoder"),503);
        jdbc.update("UPDATE user_platform_binding SET expires_at=0 WHERE user_id=?",a.getId());
        code(() -> service.finish(a.getId(),"nowcoder"),400);
        code(() -> service.start(a.getId(),"nowcoder","https://evil.example"),400);
        code(() -> service.start(a.getId(),"unknown","123"),400);
    }
    @Test void renewedChallengeCannotBeOverwrittenByLateVerification() {
        User a=user("platform-race");
        var first=service.start(a.getId(),"luogu","123");
        when(verifier.introduction("luogu","123")).thenAnswer(call -> {
            jdbc.update("UPDATE user_platform_binding SET verification_token='new-challenge' WHERE user_id=?", a.getId());
            return first.verificationToken();
        });
        code(() -> service.finish(a.getId(),"luogu"),409);
        assertThat(service.get(a.getId(),"luogu").accountId()).isNull();
    }
    @Test void apiRestrictsEvenAdminsToTheirOwnBindings() throws Exception {
        User a=user("platform-api-admin"), b=user("platform-api-target");
        a.setRole(UserRole.ADMIN); users.save(a);
        long now=System.currentTimeMillis()/1000;
        sessions.save(new AuthTokenSessionRepository.AuthTokenSession(a.getId(),"platform-access","platform-refresh",now+3600,now+7200,now));
        mvc.perform(get("/api/users/"+b.getId()+"/platform-bindings/luogu")
                .header("Authorization","Bearer platform-access")).andExpect(status().isForbidden());
        mvc.perform(post("/api/users/"+a.getId()+"/platform-bindings/nowcoder/start")
                .header("Authorization","Bearer platform-access").contentType("application/json")
                .content("{\"uid\":\"123\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountId").doesNotExist())
                .andExpect(jsonPath("$.data.verificationToken").isString());
    }
    @Test void unsuccessfulRebindingPreservesTheVerifiedAccount() {
        User a=user("platform-rebinding");
        var first=service.start(a.getId(),"luogu","123");
        when(verifier.introduction("luogu","123")).thenReturn(first.verificationToken());
        service.finish(a.getId(),"luogu");
        jdbc.update("UPDATE user_platform_binding SET started_at=0 WHERE user_id=?",a.getId());
        service.start(a.getId(),"luogu","456");
        when(verifier.introduction("luogu","456")).thenReturn("");
        code(() -> service.finish(a.getId(),"luogu"),400);
        assertThat(service.get(a.getId(),"luogu").accountId()).isEqualTo("123");
    }
}
