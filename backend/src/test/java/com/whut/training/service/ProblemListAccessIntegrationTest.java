package com.whut.training.service;

import com.whut.training.domain.dto.*;
import com.whut.training.domain.entity.User;
import com.whut.training.domain.enums.UserRole;
import com.whut.training.exception.BusinessException;
import com.whut.training.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Transactional
class ProblemListAccessIntegrationTest {
    @Autowired ProblemListService service;
    @Autowired UserRepository users;
    private User user(String name, UserRole role) { return users.save(new User(null,name,null,"password",role)); }
    private ProblemListItemRequest item(String name) { return new ProblemListItemRequest(name,"https://example.com/"+name,null,null,null,null); }
    private void forbidden(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,e -> assertThat(e.getCode()).isEqualTo(403));
    }
    @Test void publishedListsAreReadOnlyForEveryoneExceptTheirCreator() {
        var admin=user("list-publisher",UserRole.ADMIN);
        var reader=user("list-reader",UserRole.USER);
        var otherAdmin=user("list-other-admin",UserRole.SUPER_ADMIN);
        var list=service.create(admin,new ProblemListSaveRequest("专题",null,true)).list();
        var problem=service.addItem(admin,list.id(),item("original"));
        assertThat(service.get(reader,list.id()).list().collaborative()).isFalse();
        for(var viewer:java.util.List.of(reader,otherAdmin)) {
            forbidden(() -> service.addItem(viewer,list.id(),item("added")));
            forbidden(() -> service.updateItem(viewer,list.id(),problem.id(),item("changed")));
            forbidden(() -> service.deleteItem(viewer,list.id(),problem.id()));
            forbidden(() -> service.delete(viewer,list.id()));
        }
        forbidden(() -> service.create(reader,new ProblemListSaveRequest("不能发布",null,true)));
        service.updateItem(admin,list.id(),problem.id(),item("owner-change"));
        assertThat(service.get(reader,list.id()).problems().get(0).title()).isEqualTo("owner-change");
    }
    @Test void oneCommunityListAcceptsContributionsAndOnlyAuthorsOrAdminsModerate() {
        var a=user("community-a",UserRole.USER); var b=user("community-b",UserRole.USER);
        var admin=user("community-admin",UserRole.ADMIN);
        var community=service.listVisible(a).stream().filter(ProblemListSummary::collaborative).findFirst().orElseThrow();
        assertThat(service.listVisible(b).stream().filter(ProblemListSummary::collaborative).toList()).hasSize(1);
        assertThat(service.listVisible(b).stream().filter(ProblemListSummary::collaborative).findFirst().orElseThrow().id()).isEqualTo(community.id());
        var problem=service.addItem(b,community.id(),item("contribution"));
        forbidden(() -> service.deleteItem(a,community.id(),problem.id()));
        service.updateItem(b,community.id(),problem.id(),item("author-update"));
        forbidden(() -> service.delete(admin,community.id()));
        forbidden(() -> service.update(a,community.id(),new ProblemListSaveRequest("renamed",null,false)));
        service.deleteItem(admin,community.id(),problem.id());
    }
    @Test void privateListsRemainPrivate() {
        var owner=user("private-owner",UserRole.USER); var viewer=user("private-admin",UserRole.ADMIN);
        var list=service.create(owner,new ProblemListSaveRequest("私人",null,false)).list();
        forbidden(() -> service.get(viewer,list.id()));
        forbidden(() -> service.addItem(viewer,list.id(),item("not-allowed")));
    }
}
