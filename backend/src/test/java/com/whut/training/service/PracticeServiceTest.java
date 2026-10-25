package com.whut.training.service;

import com.whut.training.common.TimeProvider;
import com.whut.training.domain.entity.CfProblem;
import com.whut.training.domain.entity.User;
import com.whut.training.domain.entity.UserPracticeDraw;
import com.whut.training.domain.enums.UserRole;
import com.whut.training.exception.BusinessException;
import com.whut.training.repository.PracticeDrawRepository;
import com.whut.training.repository.ProblemCatalogRepository;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PracticeServiceTest {
    private final ProblemCatalogRepository catalog = mock(ProblemCatalogRepository.class);
    private final PracticeDrawRepository draws = mock(PracticeDrawRepository.class);
    private final ProblemPoolService pool = mock(ProblemPoolService.class);
    private final TimeProvider time = mock(TimeProvider.class);
    private final PracticeService service = new PracticeService(catalog, draws, pool, time, 1200, 2000);
    private final User user = new User(7L, "member", null, "hash", UserRole.USER);
    private final LocalDate today = LocalDate.of(2026, 10, 25);
    private final CfProblem problem = new CfProblem("123-B", 123, "B", "Example", 1800,
            "dp,math", false, null, 10, "https://codeforces.com/problemset/problem/123/B");
    private UserPracticeDraw draw() {
        return new UserPracticeDraw(42L, 7L, today, problem.problemKey(), problem.contestId(),
                problem.problemIndex(), problem.name(), problem.rating(), problem.tags(), problem.sourceUrl(), null, null);
    }

    @Test void normalizesTagsAndPersistsTheSelectedProblemForTheCurrentUser() {
        when(time.today()).thenReturn(today);
        when(catalog.findRandomProblem(800, 3500, List.of("dp", "math"))).thenReturn(Optional.of(problem));
        when(draws.insertPracticeDraw(7L, today, problem)).thenReturn(draw());
        var response = service.drawPracticeProblem(user, 800, 3500, Arrays.asList(null, " DP ", "", "dp", " math "));
        assertEquals(42L, response.drawId());
        assertEquals("PRACTICE", response.problem().type());
        assertEquals("2026-10-25", response.problem().date());
        assertEquals(problem.problemKey(), response.problem().problemKey());
        assertFalse(response.problem().checkedIn());
        assertEquals(0, response.problem().score());
        var order = inOrder(pool, catalog, draws);
        order.verify(pool).ensureAvailable();
        order.verify(catalog).findRandomProblem(800, 3500, List.of("dp", "math"));
        order.verify(draws).insertPracticeDraw(7L, today, problem);
    }

    @Test void omittedFiltersRetainConfiguredDefaults() {
        when(time.today()).thenReturn(today);
        when(catalog.findRandomProblem(1200, 2000, List.of())).thenReturn(Optional.of(problem));
        when(draws.insertPracticeDraw(7L, today, problem)).thenReturn(draw());
        assertEquals(42L, service.drawPracticeProblem(user, null, null, null).drawId());
        verify(catalog).findRandomProblem(1200, 2000, List.of());
    }

    @Test void invalidRangeDoesNotSelectOrSaveAnything() {
        assertEquals(400, assertThrows(BusinessException.class,
                () -> service.drawPracticeProblem(user, 1900, 800, List.of())).getCode());
        verifyNoInteractions(catalog, draws);
    }

    @Test void tooManyOrOversizedTagsAreRejectedBeforeSelection() {
        for (List<String> tags : List.of(List.of("a", "b", "c", "d", "e", "f"), List.of("x".repeat(65)))) {
            assertEquals(400, assertThrows(BusinessException.class,
                    () -> service.drawPracticeProblem(user, 800, 3500, tags)).getCode());
        }
        verifyNoInteractions(catalog, draws);
    }

    @Test void noMatchingProblemRemains404AndDoesNotCreateAHistoryRecord() {
        when(catalog.findRandomProblem(800, 3500, List.of("dp"))).thenReturn(Optional.empty());
        assertEquals(404, assertThrows(BusinessException.class,
                () -> service.drawPracticeProblem(user, 800, 3500, List.of("dp"))).getCode());
        verifyNoInteractions(draws);
    }

    @Test void initializingPoolRemains503WithoutTouchingPracticeRecords() {
        doThrow(new BusinessException(503, "problem pool is initializing")).when(pool).ensureAvailable();
        assertEquals(503, assertThrows(BusinessException.class,
                () -> service.drawPracticeProblem(user, 800, 3500, List.of())).getCode());
        verifyNoInteractions(catalog, draws, time);
    }

    @Test void historyKeepsOwnerLimitAndLegacyResponseFields() {
        when(draws.findPracticeDrawsByUserId(7L, 1)).thenReturn(List.of(draw()));
        var history = service.getPracticeHistory(user, -100);
        assertEquals(42L, history.get(0).drawId());
        assertEquals("2026-10-25", history.get(0).drawDate());
        assertNull(history.get(0).drawnAt());
        assertNull(history.get(0).submissionId());
        assertNull(history.get(0).verdict());
        service.getPracticeHistory(user, 1000);
        verify(draws).findPracticeDrawsByUserId(7L, 100);
        verifyNoInteractions(pool, catalog);
    }

    @Test void deleteCannotDropTheOwnerPredicate() {
        when(draws.deletePracticeDraw(42L, 7L)).thenReturn(true);
        assertTrue(service.deletePracticeDraw(user, 42L));
        assertFalse(service.deletePracticeDraw(user, 999L));
        verify(draws).deletePracticeDraw(999L, 7L);
        verifyNoInteractions(pool, catalog);
    }

    @Test void readingTagsDoesNotTriggerExternalSynchronization() {
        when(catalog.findAvailableProblemTags()).thenReturn(List.of("dp", "math"));
        assertEquals(List.of("dp", "math"), service.getAvailablePracticeTags());
        verifyNoInteractions(pool, draws);
    }
}
