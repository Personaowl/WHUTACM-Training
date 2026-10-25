package com.whut.training.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

class PublicPlatformStatisticsClientTest {
    private final PublicPlatformStatisticsClient client = new PublicPlatformStatisticsClient(new ObjectMapper(), mock(AtCoderApiService.class));

    @Test void atcoderUsesAlgorithmRatingAndTheDedicatedAcceptedCount() {
        var data = client.parseAtcoder("tourist", """
                <div id='main-container'><a href='/users/tourist'>tourist</a>
                <table><tr><th>Rating</th><td><span>3,730</span></td></tr>
                <tr><th>Highest Rating</th><td>4229 ― King (+171 to promote)</td></tr></table>
                <table><tr><th>Rating</th><td>9999</td></tr></table></div>
                """);
        assertThat(data.rating()).isEqualTo(3730); assertThat(data.maxRating()).isEqualTo(4229);
        assertThat(data.solvedCount()).isNull();
        assertThat(client.parseAcceptedCount("{\"count\":0,\"rank\":123}")).isZero();
        assertThatThrownBy(() -> client.parseAcceptedCount("{\"rank\":123}")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.parseAtcoder("other", "<div id='main-container'>Sign in</div>"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private String luogu(String fields) {
        return "<script id='lentille-context' type='application/json'>{\"status\":200,\"template\":\"user.show\",\"data\":{\"gu\":{\"rating\":999},\"user\":{\"uid\":123," + fields + "}}}</script>";
    }
    @Test void luoguNeverConfusesGuValueWithContestRatingOrInventsAnAllTimeMaximum() {
        var data = client.parseLuogu("123", luogu("\"eloValue\":null,\"passedProblemCount\":850"));
        assertThat(data.rating()).isNull(); assertThat(data.maxRating()).isNull();
        assertThat(data.solvedCount()).isEqualTo(850); assertThat(data.partial()).isTrue();
        data = client.parseLuogu("123", luogu("\"eloValue\":1600,\"passedProblemCount\":0"));
        assertThat(data.rating()).isEqualTo(1600); assertThat(data.solvedCount()).isZero();
        assertThatThrownBy(() -> client.parseLuogu("456", luogu("\"passedProblemCount\":123")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.parseLuogu("123", "<html>Verify you are human</html>"))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void nowcoderDistinguishesRatingFromRankAndSolvedProblemsFromSubmissions() {
        var data = client.parseNowcoder("123", """
                <ul class='profile-menu'><li><a href='/acm/contest/profile/123/practice-coding'>TA的练习</a></li></ul>
                <div class='status-item'><div><a class='state-num'>1,644</a></div><span>Rating</span></div>
                <div class='status-item'><div class='state-num'>3916</div><span>Rating排名</span></div>
                <div class='my-state-item'><div class='state-num'>410</div><span>题已通过</span></div>
                <div class='my-state-item'><div class='state-num'>9999</div><span>次提交</span></div>
                """);
        assertThat(data.rating()).isEqualTo(1644); assertThat(data.solvedCount()).isEqualTo(410);
        assertThat(data.maxRating()).isNull(); assertThat(data.partial()).isTrue();
        assertThatThrownBy(() -> client.parseNowcoder("456", "<html>login</html>"))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void invalidIdsAreRejectedBeforeAnyNetworkRequest() {
        for (var platform : java.util.List.of("luogu", "nowcoder", "atcoder"))
            assertThatThrownBy(() -> client.fetch(platform, "../admin?x=http://localhost"))
                    .isInstanceOf(IllegalArgumentException.class);
    }
}
