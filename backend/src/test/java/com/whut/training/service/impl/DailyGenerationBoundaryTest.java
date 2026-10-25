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
    private final DailyProblemServiceImpl service = new DailyProblemServiceImpl(daily, catalog, pool,
            mock(CodeforcesApiService.class), time, mock(DailyCheckInRecorder.class),
            1200, 2000, 90, 800, 1800, 1900, 3000);
    private DailyProblemSlot slot(Long id, String slot, int rating) {
        return new DailyProblemSlot(id, today, slot, "123-" + id, 123, "B", slot, rating,
                "dp", "https://codeforces.com/problemset/problem/123/B", false);
    }

    @Test void existingSlotsDoNotTouchCatalogOrSynchronization() {
        when(time.today()).thenReturn(today);
        when(daily.findCurrentDailySlotsByDate(today)).thenReturn(List.of(easySlot, hardSlot));
        assertTrue(service.ensureTodaySlots());
        verifyNoInteractions(catalog, pool);
        verify(daily, never()).insertDailySlot(any(), any(), any(), any());
    }

    @Test void onlyMissingHardSlotIsGeneratedWithinItsOriginalRange() {
        when(time.today()).thenReturn(today);
        when(daily.findCurrentDailySlotsByDate(today)).thenReturn(List.of(easySlot));
        when(catalog.findRandomProblem(1900, 3000, today.minusDays(90))).thenReturn(Optional.of(hard));
        assertTrue(service.ensureTodaySlots());
        verify(pool).ensureAvailable();
        verify(catalog).findRandomProblem(1900, 3000, today.minusDays(90));
        verifyNoMoreInteractions(catalog);
        verify(daily).insertDailySlot(today, "hard", hard, "api-home");
        verify(daily, never()).insertDailySlot(eq(today), eq("easy"), any(), any());
    }

    @Test void redrawingHardInvalidatesAllOldHardSlotsBeforeInsertingTheNewOne() {
        when(daily.findCurrentDailySlotsByDate(today)).thenReturn(List.of(easySlot, hardSlot));
        when(catalog.findRandomProblem(1900, 3000, today.minusDays(90))).thenReturn(Optional.of(hard));
        when(daily.insertDailySlot(today, "hard", hard, "admin-redraw")).thenReturn(hardSlot);
        User admin = new User(7L, "admin", null, "hash", UserRole.ADMIN);
        assertEquals("HARD", service.adminRedrawSlot(admin, today, "hard", true).type());
        var order = inOrder(daily, pool, catalog);
        order.verify(daily).findCurrentDailySlotsByDate(today);
        order.verify(daily).markActiveSlotsRedrawn(today, "hard");
        order.verify(pool).ensureAvailable();
        order.verify(catalog).findRandomProblem(1900, 3000, today.minusDays(90));
        order.verify(daily).insertDailySlot(today, "hard", hard, "admin-redraw");
        verify(daily, never()).markActiveSlotsRedrawn(today, "easy");
    }
}
