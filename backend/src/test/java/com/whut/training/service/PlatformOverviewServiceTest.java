package com.whut.training.service;

import com.whut.training.domain.dto.PlatformOverview.DataStatus;
import com.whut.training.domain.entity.User;
import com.whut.training.domain.enums.UserRole;
import com.whut.training.repository.PlatformBindingRepository;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlatformOverviewServiceTest {
    @Test void oneProviderFailureDoesNotBreakTheOtherPlatforms() {
        var bindings=mock(PlatformBindingRepository.class);
        when(bindings.find(anyLong(),anyString(),eq(false))).thenReturn(Optional.empty());
        var provider=mock(PlatformStatisticsProvider.class);
        when(provider.platform()).thenReturn("codeforces");
        when(provider.read(7L,"cf-user")).thenThrow(new IllegalStateException("snapshot unavailable"));
        var user=new User(7L,"test",null,"password",UserRole.USER); user.setCodeforcesHandle("cf-user");
        var result=new PlatformOverviewService(bindings,List.of(provider)).get(user);
        assertThat(result).hasSize(4);
        assertThat(result.get(0).dataStatus()).isEqualTo(DataStatus.UNAVAILABLE);
        assertThat(result.get(1).dataStatus()).isEqualTo(DataStatus.UNBOUND);
        assertThat(result.get(0).rating()).isNull();
    }
    @Test void aFuturePlatformProviderUsesTheSameFieldsWithoutChangingTheEndpoint() {
        var bindings=mock(PlatformBindingRepository.class);
        when(bindings.find(anyLong(),anyString(),eq(false))).thenReturn(Optional.empty());
        var provider=mock(PlatformStatisticsProvider.class);
        when(provider.platform()).thenReturn("atcoder");
        when(provider.read(7L,"at-user")).thenReturn(new PlatformStatisticsProvider.Statistics(0,100,0,Instant.now(),false));
        var user=new User(7L,"test",null,"password",UserRole.USER); user.setAtcoderHandle("at-user");
        var result=new PlatformOverviewService(bindings,List.of(provider)).get(user).get(1);
        assertThat(result.platform()).isEqualTo("atcoder");
        assertThat(result.dataStatus()).isEqualTo(DataStatus.READY);
        assertThat(result.rating()).isZero(); assertThat(result.solvedCount()).isZero();
    }
}
