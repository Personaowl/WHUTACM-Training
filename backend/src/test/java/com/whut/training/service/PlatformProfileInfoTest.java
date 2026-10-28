package com.whut.training.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.whut.training.domain.dto.PlatformProfileInfo;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class PlatformProfileInfoTest {
    @Test void cfPublicFieldsKeepSignedContributionButNeverContactDetailsOrAvatars() throws Exception {
        var node=new ObjectMapper().readTree("""
                {"country":"China","city":"Wuhan","organization":"WHUT","firstName":"Public","lastName":"Name",
                 "friendOfCount":0,"contribution":-8,"registrationTimeSeconds":1696851033,"lastOnlineTimeSeconds":1793160000,
                 "email":"private@example.test","avatar":"https://example.test/avatar"}
                """);
        var info=CodeforcesApiService.profileInfoFromUser(node);
        assertThat(info.fields()).containsEntry("realName","Public Name").containsEntry("contribution","-8")
                .containsEntry("friendOfCount","0").containsEntry("organization","WHUT")
                .containsEntry("registrationTimeSeconds","1696851033");
        assertThat(info.fields()).doesNotContainKeys("email","avatar");
        assertThat(new ObjectMapper().readValue(new ObjectMapper().writeValueAsString(info),PlatformProfileInfo.class)).isEqualTo(info);
        assertThatThrownBy(()->info.fields().put("country","changed")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void factsAreWhitelistedBoundedAndStripOwnershipMarkers() {
        var info=new PlatformProfileInfo(Map.of("organization","WHUT WHUT-ACM-81967F42", "realName","😀".repeat(140),
                "email","private@example.test", "friendOfCount","-1", "globalRank","0", "contribution",Long.toString(Long.MIN_VALUE),
                "lastOnlineTimeSeconds","0", "lastCompetedDate","not-a-date"));
        assertThat(info.fields()).containsEntry("organization","WHUT").containsEntry("realName","😀".repeat(128));
        assertThat(info.fields()).hasSize(2);
    }
}
