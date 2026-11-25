package com.whut.training.service.impl;

import com.whut.training.common.TimeProvider;
import com.whut.training.domain.entity.CfProblem;
import com.whut.training.domain.entity.DailyProblemSlot;
import com.whut.training.domain.entity.User;
import com.whut.training.domain.enums.UserRole;
import com.whut.training.repository.DailyProblemRepository;
import com.whut.training.repository.ProblemCatalogRepository;
import com.whut.training.service.CodeforcesApiService;
import com.whut.training.service.DailyCheckInRecorder;
import com.whut.training.service.ProblemPoolService;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DailyGenerationBoundaryTest {
    private final DailyProblemRepository daily = mock(DailyProblemRepository.class);
    private final ProblemCatalogRepository catalog = mock(ProblemCatalogRepository.class);
    private final ProblemPoolService pool = mock(ProblemPoolService.class);
    private final TimeProvider time = mock(TimeProvider.class);
    private final LocalDate today = LocalDate.of(2026, 10, 25);
    private final CfProblem hard = new CfProblem("123-B", 123, "B", "Hard", 1900, "dp", false, null,
            10, "https://codeforces.com/problemset/problem/123/B");
    private final DailyProblemSlot easySlot = slot(1L, "easy", 1200);
    private final DailyProblemSlot hardSlot = slot(2L, "hard", 1900);
    private final DailyProblemSlot midSlot = slot(3L, "mid", 1900);
    private final DailyProblemServiceImpl service = new DailyProblemServiceImpl(daily, catalog, pool,
            mock(CodeforcesApiService.class), time, mock(DailyCheckInRecorder.class),
            1200, 2000, 90, 800, 1400, 1500, 1900, 2000, 3500);
    private DailyProblemSlot slot(Long id, String slot, int rating) {
        return new DailyProblemSlot(id, today, slot, "123-" + id, 123, "B", slot, rating,
                "dp", "https://codeforces.com/problemset/problem/123/B", false);
    }

    @Test void existingSlotsDoNotTouchCatalogOrSynchronization() {
        when(time.today()).thenReturn(today);
        when(daily.findCurrentDailySlotsByDate(today)).thenReturn(List.of(easySlot, midSlot, hardSlot));
        assertTrue(service.ensureTodaySlots());
        verifyNoInteractions(catalog, pool);
        verify(daily, never()).insertDailySlot(any(), any(), any(), any());
    }

    @Test void upgradeFillsOnlyMissingMidAndKeepsPreviouslyPublishedSlots() {
        when(time.today()).thenReturn(today);
        when(daily.findCurrentDailySlotsByDate(today)).thenReturn(List.of(easySlot, hardSlot));
        when(catalog.findDailySlotCandidateByRating(1500, 1900, today, today.minusDays(90))).thenReturn(Optional.of(hard));
        assertTrue(service.ensureTodaySlots());
        verify(pool).ensureAvailable();
        verify(catalog).findDailySlotCandidateByRating(1500, 1900, today, today.minusDays(90));
        verifyNoMoreInteractions(catalog);
        verify(daily).insertDailySlot(today, "mid", hard, "api-home");
        verify(daily, never()).insertDailySlot(eq(today), argThat(slot -> !"mid".equals(slot)), any(), any());
    }

    @Test void redrawingMidOnlyReplacesThatSlotAfterSelectingAProblem() {
        when(catalog.findDailySlotCandidateByRating(1500, 1900, today, today.minusDays(90))).thenReturn(Optional.of(hard));
        when(daily.replaceDailySlot(today, "mid", hard, "admin-redraw")).thenReturn(midSlot);
        User admin = new User(7L, "admin", null, "hash", UserRole.ADMIN);
        assertEquals("MID", service.adminRedrawSlot(admin, today, "MID", true).type());
        var order = inOrder(daily, pool, catalog);
        order.verify(pool).ensureAvailable();
        order.verify(catalog).findDailySlotCandidateByRating(1500, 1900, today, today.minusDays(90));
        order.verify(daily).replaceDailySlot(today, "mid", hard, "admin-redraw");
        verifyNoMoreInteractions(daily);
    }

    @Test void newDayUsesThreeConfiguredRangesInOrder() {
        when(time.today()).thenReturn(today);
        assertTrue(service.ensureTodaySlots());
        var order = inOrder(catalog);
        for (int[] range : new int[][]{{800, 1400}, {1500, 1900}}) {
            order.verify(catalog).findDailySlotCandidateByRating(range[0], range[1], today, today.minusDays(90));
            order.verify(catalog).findDailySlotCandidateByRating(range[0], range[1], today, null);
        }
        for (int[] range : new int[][]{{2000, 3500}}) {
            order.verify(catalog).findDailySlotCandidate(range[0], range[1], today, today.minusDays(90));
            order.verify(catalog).findDailySlotCandidate(range[0], range[1], today, null);
        }
        verifyNoMoreInteractions(catalog);
    }

    @Test void unavailableRedrawDoesNotChangeAnySlot() {
        User admin = new User(7L, "admin", null, "hash", UserRole.ADMIN);
        var error = assertThrows(com.whut.training.exception.BusinessException.class,
                () -> service.adminRedrawSlot(admin, today, "mid", true));
        assertEquals(503, error.getCode());
        verifyNoInteractions(daily);
    }

    @Test void exhaustedEasyHistoryRetriesBalancedDrawWithoutCutoff() {
        when(time.today()).thenReturn(today);
        when(daily.findCurrentDailySlotsByDate(today)).thenReturn(List.of(midSlot, hardSlot));
        when(catalog.findDailySlotCandidateByRating(800, 1400, today, null)).thenReturn(Optional.of(hard));
        service.ensureTodaySlots();
        var order = inOrder(catalog);
        order.verify(catalog).findDailySlotCandidateByRating(800, 1400, today, today.minusDays(90));
        order.verify(catalog).findDailySlotCandidateByRating(800, 1400, today, null);
        verifyNoMoreInteractions(catalog);
        verify(daily).insertDailySlot(today, "easy", hard, "api-home");
    }

    @Test void hardRedrawKeepsProblemUniformSelection() {
        when(catalog.findDailySlotCandidate(2000, 3500, today, today.minusDays(90))).thenReturn(Optional.of(hard));
        when(daily.replaceDailySlot(today, "hard", hard, "admin-redraw")).thenReturn(hardSlot);
        service.adminRedrawSlot(new User(7L, "admin", null, "hash", UserRole.ADMIN), today, "hard", true);
        verify(catalog).findDailySlotCandidate(2000, 3500, today, today.minusDays(90));
        verifyNoMoreInteractions(catalog);
    }
}
