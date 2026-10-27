package com.whut.training.service;

import com.whut.training.domain.dto.UpcomingContestItem;
import com.whut.training.exception.BusinessException;
import com.whut.training.repository.ContestSnapshotRepository;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ContestCachePerformanceTest {
    static class TestClock extends Clock {
        Instant now=Instant.parse("2026-10-23T00:00:00Z");
        public ZoneId getZone(){return ZoneOffset.UTC;}
        public Clock withZone(ZoneId zone){return this;}
        public Instant instant(){return now;}
    }
    static class ControlledExecutor implements Executor {
        boolean hold;
        final Queue<Runnable> tasks=new ConcurrentLinkedQueue<>();
        final CountDownLatch submitted=new CountDownLatch(4);
        public void execute(Runnable task){if(hold){tasks.add(task);submitted.countDown();}else task.run();}
        void finish(){Runnable next;while((next=tasks.poll())!=null)next.run();}
    }
    static class Service extends AtCoderContestService {
        final AtomicInteger reads=new AtomicInteger();
        volatile boolean fail;
        volatile int version=1;
        Service(ControlledExecutor executor,Clock clock){super("unused",1,"unused","unused","unused",executor,clock);}
        Service(ControlledExecutor executor,Clock clock,ContestSnapshotRepository snapshots){
            super("unused",1,"unused","unused","unused",executor,clock,snapshots,true);
        }
        List<UpcomingContestItem> items(String platform){
            reads.incrementAndGet();if(fail)throw new IllegalStateException("source offline");
            return List.of(new UpcomingContestItem(platform,"v"+version,"Contest","Algorithm","2027-01-01T12:00:00Z",100,"All","https://example.com"));
        }
        @Override List<UpcomingContestItem> fetchCodeforces(){return items("CODEFORCES");}
        @Override List<UpcomingContestItem> fetchAtCoder(){return items("ATCODER");}
        @Override List<UpcomingContestItem> fetchNowcoder(){return items("NOWCODER");}
        @Override List<UpcomingContestItem> fetchLuogu(){return items("LUOGU");}
    }
    @Test void fortyExpiredCacheReadersReturnOldDataWithoutWaitingAndShareOneRefresh() throws Exception {
        var clock=new TestClock();var executor=new ControlledExecutor();var service=new Service(executor,clock);
        assertThat(service.getUpcomingContests()).hasSize(4);
        assertThat(service.reads.get()).isEqualTo(4);
        clock.now=clock.now.plusSeconds(61);service.version=2;executor.hold=true;
        var readers=Executors.newFixedThreadPool(8);
        try {
            var calls=new ArrayList<Future<List<UpcomingContestItem>>>();
            for(int n=0;n<40;n++)calls.add(readers.submit(service::getUpcomingContests));
            for(var call:calls)assertThat(call.get(5,TimeUnit.SECONDS)).extracting(UpcomingContestItem::contestId).containsOnly("v1");
            assertThat(executor.tasks).hasSize(4);
            executor.finish();
            assertThat(service.getUpcomingContests()).extracting(UpcomingContestItem::contestId).containsOnly("v2");
            assertThat(service.reads.get()).isEqualTo(8);
        } finally {executor.finish();readers.shutdownNow();}
    }
    @Test void fortyColdReadersWaitForOnlyOneGroupOfSourceRequests() throws Exception {
        var executor=new ControlledExecutor();executor.hold=true;var service=new Service(executor,new TestClock());
        var readers=Executors.newFixedThreadPool(8);
        try {
            var calls=new ArrayList<Future<List<UpcomingContestItem>>>();
            for(int n=0;n<40;n++)calls.add(readers.submit(service::getUpcomingContests));
            assertThat(executor.submitted.await(5,TimeUnit.SECONDS)).isTrue();
            assertThat(executor.tasks).hasSize(4);executor.finish();
            for(var call:calls)assertThat(call.get(5,TimeUnit.SECONDS)).hasSize(4);
            assertThat(service.reads.get()).isEqualTo(4);
        } finally {executor.finish();readers.shutdownNow();}
    }
    @Test void coldFailuresHaveBackoffAndWarmFailuresPreserveData() {
        var clock=new TestClock();var service=new Service(new ControlledExecutor(),clock);service.fail=true;
        for(int n=0;n<40;n++)assertThatThrownBy(service::getUpcomingContests).isInstanceOf(BusinessException.class);
        assertThat(service.reads.get()).isEqualTo(4);
        clock.now=clock.now.plusSeconds(31);service.fail=false;
        assertThat(service.getUpcomingContests()).hasSize(4);
        clock.now=clock.now.plusSeconds(61);service.fail=true;
        assertThat(service.getUpcomingContests()).hasSize(4);
        assertThat(service.getUpcomingContests()).hasSize(4);
        assertThat(service.reads.get()).isEqualTo(12);
    }

    @Test void fortyRestartReadersUseDurableDataWhileAllRemoteTasksAreStillBlocked() throws Exception {
        var clock=new TestClock();var executor=new ControlledExecutor();executor.hold=true;
        var snapshots=mock(ContestSnapshotRepository.class);
        var old=new UpcomingContestItem("ATCODER","saved","Saved","Algorithm","2027-01-01T12:00:00Z",100,"All","https://atcoder.jp");
        when(snapshots.findAll()).thenReturn(List.of(new ContestSnapshotRepository.Snapshot("ATCODER",List.of(old),clock.instant())));
        var service=new Service(executor,clock,snapshots);
        var readers=Executors.newFixedThreadPool(8);
        try {
            var calls=new ArrayList<Future<List<UpcomingContestItem>>>();
            for(int n=0;n<40;n++)calls.add(readers.submit(service::getUpcomingContests));
            for(var call:calls)assertThat(call.get(5,TimeUnit.SECONDS)).containsExactly(old);
            assertThat(executor.tasks).hasSize(4);
            assertThat(service.reads.get()).isZero();
            verify(snapshots,times(1)).findAll();
            executor.finish();
            assertThat(service.getUpcomingContests()).hasSize(4);
            verify(snapshots,times(4)).save(anyString(),anyList(),eq(clock.instant()));
        } finally {executor.finish();readers.shutdownNow();}
    }

    @Test void warmupSubmitsWorkWithoutWaitingAndSharesTheColdReadersRefresh() throws Exception {
        var clock=new TestClock();var executor=new ControlledExecutor();executor.hold=true;
        var snapshots=mock(ContestSnapshotRepository.class);
        when(snapshots.findAll()).thenReturn(List.of());
        var service=new Service(executor,clock,snapshots);
        service.warmCache();service.warmCache();
        assertThat(executor.tasks).hasSize(4);
        var readers=Executors.newSingleThreadExecutor();
        try {
            var request=readers.submit(service::getUpcomingContests);
            assertThatThrownBy(() -> request.get(50,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            executor.finish();
            assertThat(request.get(5,TimeUnit.SECONDS)).hasSize(4);
            assertThat(service.reads.get()).isEqualTo(4);
        } finally {executor.finish();readers.shutdownNow();}
    }

    @Test void successfulEmptySnapshotDoesNotBlockAndAlreadyEndedContestsAreHidden() {
        var clock=new TestClock();var executor=new ControlledExecutor();executor.hold=true;
        var snapshots=mock(ContestSnapshotRepository.class);
        var ended=new UpcomingContestItem("ATCODER","ended","Ended","Algorithm","2026-10-22T12:00:00Z",100,"All","https://atcoder.jp");
        when(snapshots.findAll()).thenReturn(List.of(new ContestSnapshotRepository.Snapshot("ATCODER",List.of(ended),clock.instant())));
        var service=new Service(executor,clock,snapshots);
        assertThat(service.getUpcomingContests()).isEmpty();
        assertThat(executor.tasks).hasSize(4);
        executor.finish();
    }

    @Test void dayOldSnapshotsAreNotUsedAsCurrentData() {
        var clock=new TestClock();var executor=new ControlledExecutor();var snapshots=mock(ContestSnapshotRepository.class);
        var old=new UpcomingContestItem("ATCODER","stale","Stale","Algorithm","2027-01-01T12:00:00Z",100,"All","https://atcoder.jp");
        when(snapshots.findAll()).thenReturn(List.of(new ContestSnapshotRepository.Snapshot("ATCODER",List.of(old),clock.instant().minusSeconds(86401))));
        var service=new Service(executor,clock,snapshots);
        assertThat(service.getUpcomingContests()).extracting(UpcomingContestItem::contestId).containsOnly("v1");
    }

    @Test void snapshotDatabaseFailureNeverDiscardsLiveContestResults() {
        var snapshots=mock(ContestSnapshotRepository.class);
        when(snapshots.findAll()).thenThrow(new IllegalStateException("offline"));
        doThrow(new IllegalStateException("offline")).when(snapshots).save(anyString(),anyList(),any());
        var service=new Service(new ControlledExecutor(),new TestClock(),snapshots);
        assertThat(service.getUpcomingContests()).hasSize(4);
        assertThat(service.getUpcomingContests()).hasSize(4);
        verify(snapshots,times(1)).findAll();
        verify(snapshots,times(4)).save(anyString(),anyList(),any());
    }

    @Test void failedSourcesNeverOverwriteDurableSnapshotsAndMalformedPlatformIsIsolated() {
        var clock=new TestClock();var snapshots=mock(ContestSnapshotRepository.class);
        var valid=new UpcomingContestItem("ATCODER","saved","Saved","Algorithm","2027-01-01T12:00:00Z",100,"All","https://atcoder.jp");
        var malformed=new UpcomingContestItem("LUOGU","bad","Bad","Algorithm","not-a-time",100,"All","https://www.luogu.com.cn");
        when(snapshots.findAll()).thenReturn(List.of(
                new ContestSnapshotRepository.Snapshot("LUOGU",List.of(malformed),clock.instant()),
                new ContestSnapshotRepository.Snapshot("ATCODER",List.of(valid),clock.instant())));
        var service=new Service(new ControlledExecutor(),clock,snapshots);service.fail=true;
        assertThat(service.getUpcomingContests()).containsExactly(valid);
        verify(snapshots,never()).save(anyString(),anyList(),any());
    }
}
