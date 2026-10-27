package com.whut.training.controller;

import com.whut.training.context.UserContext;
import com.whut.training.domain.entity.User;
import com.whut.training.domain.enums.UserRole;
import com.whut.training.exception.BusinessException;
import com.whut.training.repository.DailyProblemRepository;
import com.whut.training.service.CodeforcesProfileService;
import com.whut.training.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserReadPerformanceTest {
    private final UserService users = mock(UserService.class);
    private final UserController controller = new UserController(users, mock(DailyProblemRepository.class),
            mock(CodeforcesProfileService.class));

    @AfterEach void clear() { UserContext.clear(); }

    @Test void selfReadReusesTheRequestUserWithoutAnotherDatabaseLookup() {
        var user = new User(1L, "self", null, "hash", UserRole.USER);
        UserContext.setCurrentUser(user);
        assertThat(controller.getById(1L).data()).isSameAs(user);
        verifyNoInteractions(users);
    }

    @Test void anonymousAndOtherMemberReadsRemainForbidden() {
        UserContext.clear();
        assertThatThrownBy(() -> controller.getById(1L)).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getCode()).isEqualTo(401));
        UserContext.setCurrentUser(new User(1L, "self", null, "hash", UserRole.USER));
        assertThatThrownBy(() -> controller.getById(2L)).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getCode()).isEqualTo(403));
        verifyNoInteractions(users);
    }

    @Test void administratorStillLoadsTheRequestedMemberRatherThanReturningHimself() {
        UserContext.setCurrentUser(new User(1L, "admin", null, "hash", UserRole.ADMIN));
        var member = new User(2L, "member", null, "hash", UserRole.USER);
        when(users.getById(2L)).thenReturn(member);
        assertThat(controller.getById(2L).data()).isSameAs(member);
        verify(users).getById(2L);
    }
}
