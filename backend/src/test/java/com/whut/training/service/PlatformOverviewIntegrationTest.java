package com.whut.training.service;

import com.whut.training.domain.dto.CodeforcesOverview;
import com.whut.training.domain.dto.PlatformOverview;
import com.whut.training.domain.entity.User;
import com.whut.training.domain.enums.UserRole;
import com.whut.training.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PlatformOverviewIntegrationTest {
    @Autowired PlatformOverviewService service;
    @Autowired UserRepository users;
    @Autowired CodeforcesProfileSnapshotRepository snapshots;
    @Autowired PlatformBindingRepository bindings;
    @Autowired PlatformStatisticsSnapshotRepository platformSnapshots;
    @Autowired AuthTokenSessionRepository sessions;
    @Autowired MockMvc mvc;

    private User user(String name) { return users.save(new User(null,name,null,"password",UserRole.USER)); }
    private PlatformOverview platform(User user,String key) { return service.get(user).stream().filter(p -> p.platform().equals(key)).findFirst().orElseThrow(); }
    private void snapshot(User user,String handle,int solved) {
        snapshots.save(user.getId(),handle,new CodeforcesOverview(handle,1961,2040,solved,10,11,12,null,true,Instant.now(),false,List.of(),List.of()));
    }

    @Test void publicProfileFactsTravelOnlyWithTheVerifiedAccountAndRemainSelfOnly() throws Exception {
        var user=user("overview-profile-facts");
        bindings.ensure(user.getId(),"nowcoder");bindings.finish(user.getId(),"nowcoder","123",1);
        var info=new com.whut.training.domain.dto.PlatformProfileInfo(java.util.Map.of("followerCount","0","followingCount","13","email","hidden@example.test"));
        platformSnapshots.save(user.getId(),"nowcoder","123",new PlatformStatisticsProvider.Statistics(1258,null,352,Instant.now(),true,false,"Nick",info));
        long now=System.currentTimeMillis()/1000;
        sessions.save(new AuthTokenSessionRepository.AuthTokenSession(user.getId(),"facts-access","facts-refresh",now+3600,now+7200,now));
        mvc.perform(get("/api/users/"+user.getId()+"/platform-overviews").header("Authorization","Bearer facts-access"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[3].profileInfo.fields.followerCount").value("0"))
                .andExpect(jsonPath("$.data[3].profileInfo.fields.email").doesNotExist());
        bindings.finish(user.getId(),"nowcoder","456",2);
        assertThat(platform(user,"nowcoder").profileInfo()).isNull();
    }

    @Test void unboundPlatformsHaveNullMetricsAndPendingChallengesDoNotCountAsConnected() {
        var user=user("overview-unbound");
        bindings.ensure(user.getId(),"luogu");
        bindings.start(user.getId(),"luogu","123","private-code",System.currentTimeMillis()/1000);
        assertThat(service.get(user)).hasSize(4).allSatisfy(p -> {
            assertThat(p.connected()).isFalse(); assertThat(p.accountId()).isNull();
            assertThat(p.rating()).isNull(); assertThat(p.solvedCount()).isNull();
            assertThat(p.dataStatus()).isEqualTo(PlatformOverview.DataStatus.UNBOUND);
        });
    }
    @Test void codeforcesReadsExistingSnapshotAndIgnoresOldAccountAfterRebinding() {
        var user=user("overview-cached"); user.setCodeforcesHandle("test-handle"); users.save(user);
        snapshot(user,"test-handle",0);
        var cf=platform(user,"codeforces");
        assertThat(cf.rating()).isEqualTo(1961); assertThat(cf.maxRating()).isEqualTo(2040);
        assertThat(cf.solvedCount()).isZero(); assertThat(cf.partial()).isTrue();
        assertThat(cf.dataStatus()).isEqualTo(PlatformOverview.DataStatus.READY);
        user.setCodeforcesHandle("new-account"); users.save(user);
        cf=platform(user,"codeforces");
        assertThat(cf.rating()).isNull(); assertThat(cf.solvedCount()).isNull();
        assertThat(cf.accountId()).isEqualTo("new-account");
        assertThat(cf.dataStatus()).isEqualTo(PlatformOverview.DataStatus.PENDING);
    }
    @Test void newlySupportedPlatformsWithoutSnapshotsArePending() {
        var user=user("overview-verified"); user.setAtcoderHandle("at-user"); users.save(user);
        bindings.ensure(user.getId(),"nowcoder"); bindings.finish(user.getId(),"nowcoder","123",1);
        for(String key:List.of("atcoder","nowcoder")) {
            var p=platform(user,key);
            assertThat(p.connected()).isTrue(); assertThat(p.rating()).isNull(); assertThat(p.solvedCount()).isNull();
            assertThat(p.dataStatus()).isEqualTo(PlatformOverview.DataStatus.PENDING);
            assertThat(p.profileUrl()).startsWith("https://");
        }
    }
    @Test void otherPlatformsReadPersistedStatisticsAndRebindingHidesTheOldSnapshot() {
        var user = user("overview-multi"); user.setAtcoderHandle("at-user"); users.save(user);
        bindings.ensure(user.getId(), "luogu"); bindings.finish(user.getId(), "luogu", "123", 1);
        bindings.ensure(user.getId(), "nowcoder"); bindings.finish(user.getId(), "nowcoder", "456", 1);
        for (var pair : List.of(List.of("atcoder", "at-user"), List.of("luogu", "123"), List.of("nowcoder", "456"))) {
            platformSnapshots.save(user.getId(), pair.get(0), pair.get(1),
                    new PlatformStatisticsProvider.Statistics(0, null, 100, Instant.now(), true));
            var data = platform(user, pair.get(0));
            assertThat(data.dataStatus()).isEqualTo(PlatformOverview.DataStatus.READY);
            assertThat(data.rating()).isZero(); assertThat(data.solvedCount()).isEqualTo(100);
            assertThat(data.maxRating()).isNull(); assertThat(data.partial()).isTrue();
        }
        // A late result from the previous account cannot overwrite the new account's snapshot.
        platformSnapshots.save(user.getId(), "atcoder", "new-user", new PlatformStatisticsProvider.Statistics(500, 600, 200, Instant.now(), false));
        platformSnapshots.save(user.getId(), "atcoder", "at-user", new PlatformStatisticsProvider.Statistics(100, 100, 150, Instant.now(), false));
        user.setAtcoderHandle("new-user"); users.save(user);
        assertThat(platform(user, "atcoder").rating()).isEqualTo(500);
        user.setAtcoderHandle("no-snapshot"); users.save(user);
        assertThat(platform(user, "atcoder").rating()).isNull();
    }
    @Test void endpointIsSelfOnlyAndNeverContainsChallengeSecrets() throws Exception {
        var user=user("overview-self"); var other=user("overview-other"); user.setRole(UserRole.SUPER_ADMIN); users.save(user);
        long now=System.currentTimeMillis()/1000;
        sessions.save(new AuthTokenSessionRepository.AuthTokenSession(user.getId(),"overview-access","overview-refresh",now+3600,now+7200,now));
        mvc.perform(get("/api/users/"+user.getId()+"/platform-overviews")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users/"+other.getId()+"/platform-overviews").header("Authorization","Bearer overview-access")).andExpect(status().isForbidden());
        mvc.perform(get("/api/users/"+user.getId()+"/platform-overviews").header("Authorization","Bearer overview-access"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(4))
                .andExpect(jsonPath("$.data[0].verificationToken").doesNotExist());
    }

    @Test void nicknamePersistsAndTravelsWithItsUidButNeverWithAnUnverifiedOrNewAccount() throws Exception {
        var user=user("overview-nickname");
        bindings.ensure(user.getId(),"luogu");bindings.finish(user.getId(),"luogu","123",1);
        platformSnapshots.save(user.getId(),"luogu","123",new PlatformStatisticsProvider.Statistics(null,null,1349,Instant.now(),true,false,"猫头鹰"));
        assertThat(platformSnapshots.find(user.getId(),"luogu","123")).get()
                .satisfies(data -> assertThat(data.displayName()).isEqualTo("猫头鹰"));
        assertThat(platform(user,"luogu")).satisfies(data -> {
            assertThat(data.displayName()).isEqualTo("猫头鹰");
            assertThat(data.accountId()).isEqualTo("123");
            assertThat(data.profileUrl()).isEqualTo("https://www.luogu.com.cn/user/123");
        });
        long now=System.currentTimeMillis()/1000;
        sessions.save(new AuthTokenSessionRepository.AuthTokenSession(user.getId(),"nickname-access","nickname-refresh",now+3600,now+7200,now));
        mvc.perform(get("/api/users/"+user.getId()+"/platform-overviews").header("Authorization","Bearer nickname-access"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[2].displayName").value("猫头鹰"))
                .andExpect(jsonPath("$.data[2].accountId").value("123"));
        bindings.finish(user.getId(),"luogu","456",2);
        assertThat(platform(user,"luogu").displayName()).isNull();
        platformSnapshots.save(user.getId(),"nowcoder","789",new PlatformStatisticsProvider.Statistics(100,null,1,Instant.now(),true,false,"未绑定昵称"));
        assertThat(platform(user,"nowcoder").displayName()).isNull();
    }
}
