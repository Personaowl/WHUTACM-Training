package com.whut.training.service.impl;

import com.whut.training.common.TimeProvider;
import com.whut.training.repository.DailyProblemRepository;
import com.whut.training.service.DailyProblemService;
import com.whut.training.service.LeaderboardService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HomeServiceImplTest {

    @Test
    void reusesShortLivedOverviewAndCapsLeaderboardSize() {
        LeaderboardService leaderboardService = mock(LeaderboardService.class);
        DailyProblemRepository dailyProblemRepository = mock(DailyProblemRepository.class);
        DailyProblemService dailyProblemService = mock(DailyProblemService.class);
        TimeProvider timeProvider = mock(TimeProvider.class);
        LocalDate today = LocalDate.of(2026, 8, 9);

        when(timeProvider.today()).thenReturn(today);
        when(dailyProblemRepository.countActiveUsers(7)).thenReturn(12);
        when(leaderboardService.getTop(50, 0)).thenReturn(List.of());
        when(dailyProblemService.ensureTodaySlots()).thenReturn(true);
        when(dailyProblemRepository.findCurrentDailySlotsByDate(today)).thenReturn(List.of());

        HomeServiceImpl service = new HomeServiceImpl(
                leaderboardService,
                dailyProblemRepository,
                dailyProblemService,
                timeProvider,
                5_000
        );

        var first = service.getOverview(999);
        var second = service.getOverview(999);

        assertThat(second).isSameAs(first);
        assertThat(first.getTotalUsers()).isEqualTo(12);
        verify(leaderboardService, times(1)).getTop(50, 0);
        verify(dailyProblemRepository, times(1)).countActiveUsers(7);
    }
}
