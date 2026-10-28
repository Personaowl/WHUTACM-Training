package com.whut.training.service;

import com.whut.training.domain.dto.PlatformDetails;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Transactional
class PlatformDetailIntegrationTest {
    @Autowired UserRepository users;
    @Autowired PlatformDetailSnapshotRepository snapshots;
    @Autowired PlatformBindingRepository bindings;
    @Autowired PlatformDetailService service;
    @Autowired AuthTokenSessionRepository sessions;
    @Autowired MockMvc mvc;
    @Test void detailJsonRoundTripsAndUidRebindingIsolatesSnapshots() {
        var user=users.save(new User(null,"detail-storage",null,"pw",UserRole.USER));
        bindings.ensure(user.getId(),"luogu");bindings.finish(user.getId(),"luogu","123",1);
        var data=new PlatformDetails("luogu","123",Instant.now(),"READY",true,"公开数据",null,
                List.of(new PlatformDetails.DifficultyStat("入门",2,"#ed6c76")),null,
                List.of(new PlatformDetails.ActivityDay("2026-10-27",7)),"PUBLIC_ACTIVITY");
        snapshots.save(user.getId(),data);assertThat(snapshots.find(user.getId(),"luogu","123")).contains(data);
        assertThat(service.read(user,"luogu",false)).isEqualTo(data);
        bindings.finish(user.getId(),"luogu","456",2);
        assertThat(service.read(user,"luogu",false).accountId()).isEqualTo("456");
        assertThat(service.read(user,"luogu",false).activity()).isNull();
    }
    @Test void detailedRecordsRequireOwnershipAndNeverExposeOtherUsers() throws Exception {
        var a=users.save(new User(null,"detail-owner",null,"pw",UserRole.USER));
        var b=users.save(new User(null,"detail-other",null,"pw",UserRole.USER));
        long now=System.currentTimeMillis()/1000;
        sessions.save(new AuthTokenSessionRepository.AuthTokenSession(a.getId(),"detail-token","detail-refresh",now+3600,now+7200,now));
        mvc.perform(get("/api/users/"+a.getId()+"/platform-details")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users/"+b.getId()+"/platform-details").header("Authorization","Bearer detail-token")).andExpect(status().isForbidden());
        mvc.perform(post("/api/users/"+b.getId()+"/platform-details/refresh").header("Authorization","Bearer detail-token")).andExpect(status().isForbidden());
        mvc.perform(get("/api/users/"+a.getId()+"/platform-details").header("Authorization","Bearer detail-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(4)).andExpect(jsonPath("$.data[0].status").value("UNBOUND"));
        mvc.perform(get("/api/users/"+a.getId()+"/platform-details/unknown").header("Authorization","Bearer detail-token")).andExpect(status().isBadRequest());
    }
}
