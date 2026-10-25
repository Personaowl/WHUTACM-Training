package com.whut.training.service;

import com.whut.training.repository.PlatformStatisticsSnapshotRepository;
import com.whut.training.service.PlatformStatisticsProvider.Statistics;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PublicPlatformStatisticsServiceTest {
    @Test void coldAndExpiredReadsReturnImmediatelyAndDeduplicateBackgroundWork() {
        var repo = mock(PlatformStatisticsSnapshotRepository.class); var client = mock(PublicPlatformStatisticsClient.class);
        when(repo.find(1L,"luogu","123")).thenReturn(Optional.empty());
        var jobs = new ArrayList<Runnable>();
        var service = new PublicPlatformStatisticsService(repo,client,jobs::add,true,30);
        for (int i=0;i<40;i++) assertThat(service.read(1L,"luogu","123")).isNull();
        assertThat(jobs).hasSize(1); verifyNoInteractions(client);
        var data = new Statistics(100,null,200,Instant.now(),true);
        when(client.fetch("luogu","123")).thenReturn(data);
        jobs.remove(0).run(); verify(repo).save(1L,"luogu","123",data);
        var old = new Statistics(50,null,100,Instant.now().minusSeconds(3600),true);
        when(repo.find(1L,"luogu","123")).thenReturn(Optional.of(old));
        var result = service.read(1L,"luogu","123");
        assertThat(result.rating()).isEqualTo(50); assertThat(result.refreshing()).isTrue();
        assertThat(jobs).hasSize(1);
    }
    @Test void upstreamFailureBacksOffAndRetainsLastGoodSnapshot() {
        var repo = mock(PlatformStatisticsSnapshotRepository.class); var client = mock(PublicPlatformStatisticsClient.class);
        var jobs = new ArrayList<Runnable>();
        when(repo.find(anyLong(),anyString(),anyString())).thenReturn(Optional.empty());
        when(client.fetch("nowcoder","123")).thenThrow(new IllegalStateException());
        var service = new PublicPlatformStatisticsService(repo,client,jobs::add,true,30);
        service.read(1L,"nowcoder","123"); jobs.remove(0).run();
        for(int i=0;i<5;i++) assertThatThrownBy(() -> service.read(1L,"nowcoder","123")).isInstanceOf(IllegalStateException.class);
        assertThat(jobs).isEmpty(); verify(repo,never()).save(anyLong(),anyString(),anyString(),any());
        when(repo.find(1L,"nowcoder","123")).thenReturn(Optional.of(new Statistics(100,null,10,Instant.now().minusSeconds(3600),true)));
        assertThat(service.read(1L,"nowcoder","123").rating()).isEqualTo(100);
    }
    @Test void fullQueueDoesNotExecuteExternalWorkOnRequestThreadAndFreshSnapshotsDoNotRefresh() {
        var repo = mock(PlatformStatisticsSnapshotRepository.class); var client = mock(PublicPlatformStatisticsClient.class);
        when(repo.find(anyLong(),anyString(),anyString())).thenReturn(Optional.empty());
        var service = new PublicPlatformStatisticsService(repo,client, job -> { throw new java.util.concurrent.RejectedExecutionException(); },true,30);
        assertThat(service.read(1L,"luogu","123")).isNull();
        assertThatThrownBy(() -> service.read(1L,"luogu","123")).isInstanceOf(IllegalStateException.class);
        when(repo.find(1L,"atcoder","test")).thenReturn(Optional.of(new Statistics(0,0,0,Instant.now(),false)));
        assertThat(service.read(1L,"atcoder","test").rating()).isZero();
        verifyNoInteractions(client);
    }
}
