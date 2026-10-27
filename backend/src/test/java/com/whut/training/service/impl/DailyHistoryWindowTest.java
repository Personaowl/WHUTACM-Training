package com.whut.training.service.impl;

import com.whut.training.common.TimeProvider;
import com.whut.training.domain.entity.DailyProblemSlot;
import com.whut.training.domain.entity.User;
import com.whut.training.domain.enums.UserRole;
import com.whut.training.repository.DailyProblemRepository;
import com.whut.training.repository.ProblemCatalogRepository;
import com.whut.training.service.CodeforcesApiService;
import com.whut.training.service.DailyCheckInRecorder;
import com.whut.training.service.ProblemPoolService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.LocalDate;
import java.util.List;
import static org.mockito.Mockito.*;

class DailyHistoryWindowTest {
    private final DailyProblemRepository daily = mock(DailyProblemRepository.class);
    private final TimeProvider time = mock(TimeProvider.class);
    private final LocalDate today = LocalDate.of(2026, 3, 1);
    private final User user = new User(7L, "history-user", null, "hash", UserRole.USER);
    private final DailyProblemServiceImpl service = new DailyProblemServiceImpl(daily,
            mock(ProblemCatalogRepository.class), mock(ProblemPoolService.class), mock(CodeforcesApiService.class),
            time, mock(DailyCheckInRecorder.class), 1200, 2000, 90, 800, 1800, 1900, 3000);

    @BeforeEach void setup() {
        when(time.today()).thenReturn(today);
        when(daily.findCurrentDailySlotsByDate(today)).thenReturn(List.of(slot("easy"), slot("hard")));
    }

    private DailyProblemSlot slot(String type) {
        return new DailyProblemSlot(1L, today, type, "123-" + type, 123, "A", "History", 1200,
                "math", "https://codeforces.com/problemset/problem/123/A", false);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, Integer.MIN_VALUE, 30, 31, 365, Integer.MAX_VALUE})
    void defaultLegacyAndOversizedRequestsAreLimitedToThirtyInclusiveDays(int days) {
        service.getHistory(user, days);
        verify(daily).findDailyHistoryForUser(user.getId(), LocalDate.of(2026, 1, 31), today);
        verify(daily, never()).deleteDailyByDate(any());
        verify(daily, never()).deleteDailySlotsByDate(any());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 7, 29})
    void shorterWindowsArePreserved(int days) {
        service.getHistory(user, days);
        verify(daily).findDailyHistoryForUser(user.getId(), today.minusDays(days - 1L), today);
    }
}
