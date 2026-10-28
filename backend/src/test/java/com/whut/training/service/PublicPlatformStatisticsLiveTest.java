package com.whut.training.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.assertj.core.api.Assertions.*;

/** Explicit opt-in public-source smoke test; never runs in ordinary CI or verify. */
@EnabledIfSystemProperty(named = "platform.live", matches = "true")
class PublicPlatformStatisticsLiveTest {
    private final PublicPlatformStatisticsClient client = new PublicPlatformStatisticsClient(new ObjectMapper(),
            new AtCoderApiService("https://atcoder.jp", "https://kenkoooo.com/atcoder/atcoder-api/v3/user/submissions"));
    @Test void publicProfileFactsFromSamePages() {
        var atcoder=client.parseAtcoder("Persona_owl",client.get("https://atcoder.jp/users/Persona_owl?lang=en"));
        var luogu=client.parseLuogu("373100",client.get("https://www.luogu.com.cn/user/373100"));
        var nowcoder=client.parseNowcoder("317580157",client.get("https://ac.nowcoder.com/acm/contest/profile/317580157/practice-coding"));
        assertThat(atcoder.profileInfo().fields()).containsKeys("globalRank","ratedContestCount");
        assertThat(luogu.profileInfo().fields()).containsKeys("followingCount","followerCount","registrationTimeSeconds");
        assertThat(nowcoder.profileInfo().fields()).containsKeys("followingCount","followerCount","attemptedCount","submissionCount");
        System.out.println("Public profile fact keys: AtCoder="+atcoder.profileInfo().fields().keySet()+", Luogu="+luogu.profileInfo().fields().keySet()+", Nowcoder="+nowcoder.profileInfo().fields().keySet());
    }
    @Test void atcoder() {
        var data = client.fetch("atcoder", "tourist");
        assertThat(data.rating()).isNotNull(); assertThat(data.maxRating()).isGreaterThanOrEqualTo(data.rating());
        assertThat(data.solvedCount()).isNotNull();
        System.out.println("AtCoder live public statistics: " + data);
    }
    @Test void luogu() {
        var data = client.fetch("luogu", "2");
        assertThat(data.solvedCount()).isNotNull();
        System.out.println("Luogu live public statistics: " + data);
    }
    @Test void nowcoder() {
        var data = client.fetch("nowcoder", "733965206");
        assertThat(data.rating()).isNotNull(); assertThat(data.solvedCount()).isNotNull();
        System.out.println("Nowcoder live public statistics: " + data);
    }
}
