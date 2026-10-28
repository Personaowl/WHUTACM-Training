package com.whut.training.service;

import com.whut.training.domain.dto.PlatformDetails;
import com.whut.training.domain.entity.User;
import com.whut.training.domain.enums.UserRole;
import com.whut.training.repository.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlatformDetailServiceTest {
    private final PlatformBindingRepository bindings=mock(PlatformBindingRepository.class);
    private final PlatformDetailSnapshotRepository snapshots=mock(PlatformDetailSnapshotRepository.class);
    private final PublicPlatformDetailClient client=mock(PublicPlatformDetailClient.class);
    private final List<Runnable> jobs=new ArrayList<>();
    private final PlatformDetailService service=new PlatformDetailService(bindings,snapshots,client,mock(CodeforcesProfileService.class),jobs::add,true,360);
    private User user() {var u=new User(7L,"member",null,"pw",UserRole.USER);u.setAtcoderHandle("bound");return u;}
    private PlatformDetails stored(Instant time) {return new PlatformDetails("atcoder","bound",time,"READY",true,"bounded",null,null,List.of(),List.of(),"AC_SUBMISSIONS");}

    @Test void missingDetailsReadLocallyAndCoalesceBackgroundWork() {
        var u=user();when(snapshots.find(7L,"atcoder","bound")).thenReturn(Optional.empty());
        for(int n=0;n<40;n++)assertThat(service.read(u,"atcoder",false).status()).isEqualTo("PENDING");
        assertThat(jobs).hasSize(1);verifyNoInteractions(client);
        var result=stored(Instant.now());when(client.fetch("atcoder","bound")).thenReturn(result);
        jobs.get(0).run();verify(snapshots).save(7L,result);
    }
    @Test void freshCacheDoesNotFetchAndManualSyncRespectsOneMinuteCooldown() {
        var data=stored(Instant.now());when(snapshots.find(7L,"atcoder","bound")).thenReturn(Optional.of(data));
        assertThat(service.read(user(),"atcoder",false)).isEqualTo(data);
        assertThat(service.read(user(),"atcoder",true)).isEqualTo(data);
        assertThat(jobs).isEmpty();verifyNoInteractions(client);
    }
    @Test void failedRefreshKeepsOldSnapshotAndBacksOff() {
        var old=stored(Instant.now().minusSeconds(30000));when(snapshots.find(7L,"atcoder","bound")).thenReturn(Optional.of(old));
        when(client.fetch("atcoder","bound")).thenThrow(new IllegalStateException("unavailable"));
        assertThat(service.read(user(),"atcoder",false).status()).isEqualTo("SYNCING");
        jobs.get(0).run();
        for(int n=0;n<30;n++)assertThat(service.read(user(),"atcoder",true).status()).isEqualTo("STALE");
        assertThat(jobs).hasSize(1);verify(snapshots,never()).save(anyLong(),any());
    }
    @Test void rebindAndPendingChallengesCannotReadOldDetails() {
        var u=user();u.setAtcoderHandle(null);
        assertThat(service.read(u,"atcoder",false).status()).isEqualTo("UNBOUND");
        u.setAtcoderHandle("other");
        assertThat(service.read(u,"atcoder",false).accountId()).isEqualTo("other");
        verify(snapshots,never()).find(7L,"atcoder","bound");
        assertThat(service.read(u,"luogu",false).status()).isEqualTo("UNBOUND");
    }
    @Test void unsupportedPlatformAndUnauthenticatedReadsAreRejected() {
        assertThatThrownBy(()->service.read(user(),"unknown",false)).isInstanceOf(com.whut.training.exception.BusinessException.class);
        assertThatThrownBy(()->service.read(null,"luogu",false)).isInstanceOf(com.whut.training.exception.BusinessException.class);
        assertThat(jobs).isEmpty();
    }
}
