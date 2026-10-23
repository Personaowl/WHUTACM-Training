package com.whut.training.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PublicProfileVerifierTest {
    private final PublicProfileVerifier verifier = new PublicProfileVerifier(new ObjectMapper());
    @Test
    @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="RUN_PUBLIC_PROFILE_SMOKE", matches="true")
    void livePublicProfileStructureSmoke() {
        assertThat(verifier.introduction("luogu","1")).isNotNull();
        assertThat(verifier.introduction("nowcoder","317580157")).isNotNull();
    }
    @Test void luoguReadsOnlyMatchingProfileIntroduction() throws Exception {
        String html = "<script id='lentille-context' type='application/json'>{\"status\":200,\"template\":\"user.show\",\"data\":{\"user\":{\"uid\":123,\"introduction\":\"WHUT-proof\"}}}</script>";
        assertThat(verifier.parse("luogu", "123", html)).isEqualTo("WHUT-proof");
        assertThatThrownBy(() -> verifier.parse("luogu", "456", html)).isInstanceOf(Exception.class);
    }
    @Test void nowcoderIgnoresFeedContentAndDoesNotExecuteScripts() throws Exception {
        String html = "<script>window.__INITIAL_STATE__={\"store\":{\"profile\":{\"profile\":{\"userId\":123,\"introduction\":\"本人简介\"}}},\"prefetchData\":{\"comment\":\"WHUT-proof\"}};(function(){throw 1})();</script>";
        assertThat(verifier.parse("nowcoder", "123", html)).isEqualTo("本人简介").doesNotContain("WHUT-proof");
        assertThatThrownBy(() -> verifier.parse("nowcoder", "456", html)).isInstanceOf(Exception.class);
    }
    @Test void loginPagesAndChallengePagesNeverCountAsProof() {
        for (String platform : new String[]{"luogu", "nowcoder"})
            assertThatThrownBy(() -> verifier.parse(platform, "123", "<html>WHUT-proof 请登录</html>")).isInstanceOf(Exception.class);
    }
}
