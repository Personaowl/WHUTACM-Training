package com.whut.training.service;

import com.whut.training.domain.dto.CodeforcesOverview;
import com.whut.training.repository.CodeforcesProfileSnapshotRepository;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CodeforcesStatisticsProviderTest {
    @Test void homepageUsesExistingNonBlockingProfileRefreshAndDoesNotExposeInitialZeroCounts() {
        var repo = mock(CodeforcesProfileSnapshotRepository.class); var profiles = mock(CodeforcesProfileService.class);
        var provider = new CodeforcesStatisticsProvider(repo,profiles,true);
        when(profiles.getOverview(7L)).thenReturn(new CodeforcesOverview("user",100,200,0,0,0,0,null,false,null,true,List.of(),List.of()));
        assertThat(provider.read(7L,"user")).isNull();
        when(profiles.getOverview(7L)).thenReturn(new CodeforcesOverview("user",1961,2040,1748,1800,2000,62,null,true,Instant.now(),true,List.of(),List.of()));
        var stats = provider.read(7L,"user");
        assertThat(stats.rating()).isEqualTo(1961); assertThat(stats.solvedCount()).isEqualTo(1748);
        assertThat(stats.refreshing()).isTrue(); assertThat(stats.partial()).isTrue();
        assertThat(provider.read(7L,"different-account")).isNull();
        verifyNoInteractions(repo);
    }
}
