package com.whut.training.service;

import com.whut.training.domain.dto.UpsolvePlanRequest;
import com.whut.training.domain.entity.User;
import com.whut.training.domain.enums.UserRole;
import com.whut.training.exception.BusinessException;
import com.whut.training.repository.AuthTokenSessionRepository;
import com.whut.training.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UpsolvePlanIntegrationTest {
    @Autowired UpsolvePlanService service;
    @Autowired UserRepository users;
    @Autowired AuthTokenSessionRepository sessions;
    @Autowired MockMvc mvc;

    private User user(String name) { return users.save(new User(null,name,null,"password",UserRole.USER)); }
    private UpsolvePlanRequest request(String link) { return new UpsolvePlanRequest(link,null,"赛后补题"); }
    private void code(Runnable action, int code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,e -> assertThat(e.getCode()).isEqualTo(code));
    }
    private String token(User user) {
        long now=System.currentTimeMillis()/1000;
        String token="upsolve-access-"+user.getId();
        sessions.save(new AuthTokenSessionRepository.AuthTokenSession(user.getId(),token,"upsolve-refresh-"+user.getId(),now+3600,now+7200,now));
        return "Bearer "+token;
    }

    @Test void recordsPersistAcrossReadsAndCompletionCanBeUndoneWithoutDeleting() {
        var user=user("upsolve-flow");
        var saved=service.save(user.getId(),null,request("https://codeforces.com/contest/123/problem/a?locale=en#top"));
        assertThat(saved.link()).isEqualTo("https://codeforces.com/problemset/problem/123/A");
        assertThat(saved.title()).isNotBlank();
        assertThat(saved.note()).isEqualTo("赛后补题");
        assertThat(service.list(user.getId(),false,1,20).items()).hasSize(1);
        var done=service.complete(user.getId(),saved.id(),true);
        assertThat(done.completedAt()).isNotNull();
        assertThat(service.complete(user.getId(),saved.id(),true).completedAt()).isEqualTo(done.completedAt());
        assertThat(service.list(user.getId(),false,1,20).pendingCount()).isZero();
        assertThat(service.list(user.getId(),true,1,20).items()).hasSize(1);
        var undone=service.complete(user.getId(),saved.id(),false);
        assertThat(undone.completedAt()).isNull();
        var edited=service.save(user.getId(),saved.id(),new UpsolvePlanRequest(saved.link(),"新标题","新备注"));
        assertThat(edited.title()).isEqualTo("新标题");
        assertThat(edited.completed()).isFalse();
        service.delete(user.getId(),saved.id());
        assertThat(service.list(user.getId(),false,1,20).total()).isZero();
    }

    @Test void duplicatesAreRejectedAcrossStatusesButOtherUsersCanSaveSameProblem() {
        var a=user("upsolve-duplicate-a"); var b=user("upsolve-duplicate-b");
        var first=service.save(a.getId(),null,request("https://codeforces.com/problemset/problem/123/A"));
        service.complete(a.getId(),first.id(),true);
        code(() -> service.save(a.getId(),null,request("http://www.codeforces.com/contest/123/problem/a")),409);
        service.save(b.getId(),null,request(first.link()));
        var second=service.save(a.getId(),null,request("https://atcoder.jp/contests/abc380/tasks/abc380_f"));
        code(() -> service.save(a.getId(),second.id(),request(first.link())),409);
        assertThat(service.list(a.getId(),false,1,20).items().get(0).link()).isEqualTo(second.link());
    }

    @Test void paginationAndTotalsArePerUserAndPerStatus() {
        var user=user("upsolve-pages");
        for(int n=0;n<23;n++) service.save(user.getId(),null,request("https://example.com/problem/"+n));
        var first=service.list(user.getId(),false,1,20);
        var second=service.list(user.getId(),false,2,20);
        assertThat(first.total()).isEqualTo(23);
        assertThat(first.items()).hasSize(20);
        assertThat(second.items()).hasSize(3);
        assertThat(first.items().get(19).id()).isGreaterThan(second.items().get(0).id());
        code(() -> service.list(user.getId(),false,0,20),400);
        code(() -> service.list(user.getId(),false,1,100),400);
    }

    @Test void apiRejectsUnauthenticatedAndEvenAdminsCannotModifyOthersRecords() throws Exception {
        var a=user("upsolve-owner"); var admin=user("upsolve-admin");
        admin.setRole(UserRole.SUPER_ADMIN); users.save(admin);
        var saved=service.save(a.getId(),null,request("https://www.luogu.com.cn/problem/P1001"));
        String auth=token(admin);
        mvc.perform(get("/api/upsolve-plans")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/upsolve-plans").header("Authorization",auth)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pendingCount").value(0));
        mvc.perform(patch("/api/upsolve-plans/"+saved.id()).header("Authorization",auth).contentType("application/json")
                .content("{\"link\":\"https://example.com\",\"title\":\"overwrite\"}")).andExpect(status().isNotFound());
        mvc.perform(patch("/api/upsolve-plans/"+saved.id()+"/completion").header("Authorization",auth).contentType("application/json")
                .content("{\"completed\":true}")).andExpect(status().isNotFound());
        mvc.perform(delete("/api/upsolve-plans/"+saved.id()).header("Authorization",auth)).andExpect(status().isNotFound());
        assertThat(service.list(a.getId(),false,1,20).items()).hasSize(1);
    }

    @Test void apiValidatesPayloadAndPersistsOwnerFromSession() throws Exception {
        var user=user("upsolve-api"); String auth=token(user);
        mvc.perform(post("/api/upsolve-plans").header("Authorization",auth).contentType("application/json")
                .content("{\"link\":\"javascript:alert(1)\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/upsolve-plans").header("Authorization",auth).contentType("application/json")
                .content("{\"link\":\"\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/upsolve-plans").header("Authorization",auth).contentType("application/json")
                .content("{\"link\":\"https://example.com/problem/1\",\"title\":\"Example\",\"note\":\"思路\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.completed").value(false))
                .andExpect(jsonPath("$.data.title").value("Example"));
        var id=service.list(user.getId(),false,1,20).items().get(0).id();
        mvc.perform(patch("/api/upsolve-plans/"+id+"/completion").header("Authorization",auth).contentType("application/json")
                .content("{}")).andExpect(status().isBadRequest());
        mvc.perform(patch("/api/upsolve-plans/"+id+"/completion").header("Authorization",auth).contentType("application/json")
                .content("{\"completed\":true}")).andExpect(status().isOk()).andExpect(jsonPath("$.data.completed").value(true));
        mvc.perform(post("/api/upsolve-plans").header("Authorization",auth).contentType("application/json")
                .content("{\"link\":\"https://example.com/problem/1\"}")).andExpect(status().isConflict());
    }
}
