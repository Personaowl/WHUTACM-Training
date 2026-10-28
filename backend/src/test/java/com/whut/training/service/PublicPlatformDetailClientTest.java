package com.whut.training.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

class PublicPlatformDetailClientTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final PublicPlatformDetailClient client = new PublicPlatformDetailClient(mock(PublicPlatformStatisticsClient.class), mock(AtCoderApiService.class), mapper);
    private String luogu(String uid, String fields) { return "<script id='lentille-context' type='application/json'>{\"status\":200,\"template\":\"user.show\",\"data\":{\"user\":{\"uid\":"+uid+"},"+fields+"}}</script>"; }

    @Test void luoguDifficultyIsDeduplicatedAndNeverTurnedIntoCfRatings() {
        var rows = client.parseLuoguPractice("123", luogu("123", "\"passed\":[{\"pid\":\"P1001\",\"difficulty\":1},{\"pid\":\"P1001\",\"difficulty\":1},{\"pid\":\"P1002\",\"difficulty\":4}]"));
        assertThat(rows).hasSize(2);assertThat(rows.get(0).label()).isEqualTo("入门");assertThat(rows.get(0).count()).isEqualTo(1);
        assertThatThrownBy(() -> client.parseLuoguPractice("999", luogu("123", "\"passed\":[]"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.parseLuoguPractice("123", luogu("123", "\"submitted\":[]"))).isInstanceOf(IllegalArgumentException.class);
        assertThat(client.parseLuoguPractice("123", luogu("123", "\"passed\":[]"))).isEmpty();
    }
    @Test void publicLuoguCalendarIsNotClaimedAsAcceptedSubmissions() {
        String day = LocalDate.now(ZoneId.of("Asia/Shanghai")).toString();
        var rows = client.parseLuoguActivity("123", luogu("123", "\"dailyCounts\":{\""+day+"\":[6,5],\"2000-01-01\":[9,9]}"));
        assertThat(rows).hasSize(1);assertThat(rows.get(0).count()).isEqualTo(6);
    }
    @Test void atcoderHistoryKeepsOnlyRatedAndUsesFixedContestLinks() {
        var rows = client.parseAtcoderHistory("""
          [{"IsRated":true,"ContestScreenName":"abc123.contest.atcoder.jp","ContestName":"ABC 123","OldRating":1000,"NewRating":1100,"Place":42,"EndTime":"2026-10-20T21:00:00+09:00"},
           {"IsRated":false,"ContestScreenName":"abc124.contest.atcoder.jp","EndTime":"2026-10-21T21:00:00+09:00"}]
          """);
        assertThat(rows).hasSize(1);assertThat(rows.get(0).url()).isEqualTo("https://atcoder.jp/contests/abc123");
        assertThat(rows.get(0).ratingChange()).isEqualTo(100);
        assertThatThrownBy(() -> client.parseAtcoderHistory("{\"error\":true}")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void nowcoderRatingHistoryAcceptsIntegralDoublesAndDeduplicatesLatest() {
        var rows=client.parseNowcoderHistory("""
          {"code":0,"data":[{"contestId":7,"rating":1200.0,"changeValue":-50.0,"rank":2,"time":1750000000000},
          {"contestId":7,"rating":1250.0,"changeValue":50.0,"time":1760000000000}]}
          """);
        assertThat(rows).hasSize(1);assertThat(rows.get(0).newRating()).isEqualTo(1250);
        assertThat(rows.get(0).oldRating()).isEqualTo(1200);assertThat(rows.get(0).ratingUpdateTimeSeconds()).isEqualTo(1760000000L);
        assertThatThrownBy(() -> client.parseNowcoderHistory("{\"code\":999,\"data\":[]}")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void atcoderActivityChecksOwnerDeduplicatesAndUsesChinaDay() throws Exception {
        long second = LocalDate.now(ZoneId.of("Asia/Shanghai")).atStartOfDay(ZoneId.of("Asia/Shanghai")).toEpochSecond();
        var ac=mapper.readTree("{\"id\":1,\"user_id\":\"name\",\"epoch_second\":"+second+",\"result\":\"AC\"}");
        var wa=mapper.readTree("{\"id\":2,\"user_id\":\"name\",\"epoch_second\":"+second+",\"result\":\"WA\"}");
        var rows=client.parseAtcoderActivity("name",List.of(ac,ac,wa));
        assertThat(rows).hasSize(1);assertThat(rows.get(0).count()).isEqualTo(1);
        assertThatThrownBy(() -> client.parseAtcoderActivity("other",List.of(ac))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void nowcoderOnlyAcceptsVerifiedTargetAndRealAcceptedRows() {
        String html="<div class='profile-menu'><a href='/acm/contest/profile/123/practice-coding'>个人</a></div><table><tr><th>提交时间</th></tr>"
                +"<tr><td>998</td><td>题目</td><td>答案正确</td><td>100</td><td>1</td><td>2</td><td>3</td><td>C++</td><td>2026-10-20 23:59:59</td></tr></table>";
        var rows=client.parseNowcoderActivity("123",html);
        assertThat(rows).hasSize(1);assertThat(rows.get(0).id()).isEqualTo("998");
        assertThatThrownBy(() -> client.parseNowcoderActivity("999",html)).isInstanceOf(IllegalArgumentException.class);
        assertThat(client.parseNowcoderActivity("123",html.replace("答案正确","答案错误"))).isEmpty();
    }
    @Test void untrustedIdentifiersAreRejectedBeforeNetwork() {
        for(String platform:List.of("atcoder","luogu","nowcoder"))assertThatThrownBy(()->client.fetch(platform,"../../host")).isInstanceOf(IllegalArgumentException.class);
    }
}
