package com.whut.training.service;

import com.whut.training.domain.dto.*;
import com.whut.training.domain.entity.User;
import com.whut.training.domain.enums.UserRole;
import com.whut.training.exception.BusinessException;
import com.whut.training.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import com.whut.training.repository.AuthTokenSessionRepository;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SharedProblemListIntegrationTest {
    @Autowired ProblemListService service;
    @Autowired UserRepository users;
    @Autowired MockMvc mvc;
    @Autowired AuthTokenSessionRepository sessions;

    private User user(String name, UserRole role) {
        return users.save(new User(null, name, null, "test-password", role));
    }
    private ProblemListItemRequest problem(String suffix) {
        return new ProblemListItemRequest("题目" + suffix, "https://example.com/" + suffix, null, null, 1200, null);
    }
    private void forbidden(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
                ex -> assertThat(ex.getCode()).isEqualTo(403));
    }

    @Test void membersContributeWithoutReviewAndCannotEditOthersContributions() {
        User owner = user("shared-owner", UserRole.USER);
        User author = user("shared-author", UserRole.USER);
        User viewer = user("shared-viewer", UserRole.USER);
        Long id = service.create(owner, new ProblemListSaveRequest("共享", null, true)).list().id();
        var item = service.addItem(author, id, problem("one"));
        assertThat(item.addedBy()).isEqualTo(author.getId());
        assertThat(service.get(viewer, id).problems()).hasSize(1);
        assertThat(service.listVisible(viewer)).extracting(ProblemListSummary::id).contains(id);
        forbidden(() -> service.updateItem(viewer, id, item.id(), problem("stolen")));
        forbidden(() -> service.deleteItem(viewer, id, item.id()));
        forbidden(() -> service.delete(viewer, id));
        assertThat(service.updateItem(author, id, item.id(), problem("edited")).title()).isEqualTo("题目edited");
        assertThatThrownBy(() -> service.addItem(viewer, id, problem("edited")))
                .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getCode()).isEqualTo(409));
        assertThatThrownBy(() -> service.update(owner, id, new ProblemListSaveRequest("私有", null, false)))
                .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getCode()).isEqualTo(409));
        service.deleteItem(author, id, item.id());
        assertThat(service.get(viewer, id).problems()).isEmpty();
    }

    @Test void ownersAndAdminsModerateSharedItemsButPrivateListsRemainPrivate() {
        User owner = user("moderation-owner", UserRole.USER);
        User author = user("moderation-author", UserRole.USER);
        User admin = user("moderation-admin", UserRole.ADMIN);
        Long id = service.create(owner, new ProblemListSaveRequest("公共", null, true)).list().id();
        var item = service.addItem(author, id, problem("two"));
        service.updateItem(owner, id, item.id(), problem("owner-edit"));
        service.updateItem(admin, id, item.id(), problem("admin-edit"));
        service.deleteItem(admin, id, item.id());
        Long privateId = service.create(owner, new ProblemListSaveRequest("私人", null, false)).list().id();
        forbidden(() -> service.addItem(author, privateId, problem("private")));
        forbidden(() -> service.get(admin, privateId));
        forbidden(() -> service.update(author, id, new ProblemListSaveRequest("接管", null, true)));
    }

    @Test void authenticatedHttpApiSupportsCollaborationAndOldPushEndpointsAreGone() throws Exception {
        User owner = user("api-list-owner", UserRole.USER);
        User author = user("api-list-author", UserRole.USER);
        Long id = service.create(owner, new ProblemListSaveRequest("HTTP共享", null, true)).list().id();
        long now = System.currentTimeMillis() / 1000;
        sessions.save(new AuthTokenSessionRepository.AuthTokenSession(author.getId(),
                "shared-api-access", "shared-api-refresh", now + 3600, now + 7200, now));
        mvc.perform(post("/api/problem-lists/" + id + "/items")
                .header("Authorization", "Bearer shared-api-access").contentType("application/json")
                .content("{\"title\":\"API题目\",\"link\":\"https://example.com/http\",\"addedBy\":999999}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.addedBy").value(author.getId()));
        for (String path : new String[]{"/api/push", "/api/push/today", "/api/admin/push/pool"}) {
            mvc.perform(get(path).header("Authorization", "Bearer shared-api-access")).andExpect(status().isNotFound());
        }
    }

    @Test void linksRejectScriptAndEmbeddedCredentials() {
        User owner = user("links-owner", UserRole.USER);
        Long id = service.create(owner, new ProblemListSaveRequest("链接", null, true)).list().id();
        for (String link : new String[]{"javascript:alert(1)", "https://user:pass@example.com/a"}) {
            assertThatThrownBy(() -> service.addItem(owner, id,
                    new ProblemListItemRequest("unsafe", link, null, null, null, null)))
                    .isInstanceOf(BusinessException.class);
        }
    }
}
