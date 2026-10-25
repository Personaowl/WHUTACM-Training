package com.whut.training.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.whut.training.exception.BusinessException;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;

/** Reads only the target account's public introduction; never executes embedded JavaScript. */
@Service
public class PublicProfileVerifier {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PublicProfileVerifier.class);
    private final ObjectMapper mapper;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    public PublicProfileVerifier(ObjectMapper mapper) { this.mapper = mapper; }

    public String profileUrl(String platform, String uid) {
        return switch (platform) {
            case "luogu" -> "https://www.luogu.com.cn/user/" + uid;
            case "nowcoder" -> "https://www.nowcoder.com/users/" + uid;
            default -> throw new BusinessException(400, "不支持的平台");
        };
    }

    public String introduction(String platform, String uid) {
        try {
            var request = HttpRequest.newBuilder(URI.create(profileUrl(platform, uid)))
                    .timeout(Duration.ofSeconds(8)).header("User-Agent", "WHUT-Training-AccountVerification/1.0")
                    .header("Cache-Control", "no-cache").GET().build();
            var pending = client.sendAsync(request, ignored -> new LimitedBody());
            try {
                var response = pending.get(10, java.util.concurrent.TimeUnit.SECONDS);
                if (response.statusCode() != 200) throw new IllegalStateException("upstream HTTP " + response.statusCode());
                if (response.body().length() > 1_048_576) throw new IllegalStateException("profile too large");
                return parse(platform, uid, response.body());
            } finally { pending.cancel(true); }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException(503, "平台访问暂时失败，请稍后重试");
        } catch (Exception ex) {
            // Never log page contents or the user's verification token.
            log.warn("Public profile unavailable platform={} reason={}", platform,
                    ex instanceof IllegalStateException ? ex.getMessage() : ex.getClass().getSimpleName());
            throw new BusinessException(503, "无法读取平台公开简介，可能限流、需要登录或页面结构变化，请稍后重试");
        }
    }

    static final class LimitedBody implements HttpResponse.BodySubscriber<String> {
        private final HttpResponse.BodySubscriber<String> delegate =
                HttpResponse.BodySubscribers.ofString(java.nio.charset.StandardCharsets.UTF_8);
        private java.util.concurrent.Flow.Subscription subscription;
        private long size;
        public java.util.concurrent.CompletionStage<String> getBody() { return delegate.getBody(); }
        public void onSubscribe(java.util.concurrent.Flow.Subscription value) {
            subscription = value; delegate.onSubscribe(value);
        }
        public void onNext(java.util.List<java.nio.ByteBuffer> chunks) {
            for (var chunk : chunks) size += chunk.remaining();
            if (size > 1_048_576) {
                subscription.cancel(); delegate.onError(new java.io.IOException("profile too large")); return;
            }
            delegate.onNext(chunks);
        }
        public void onError(Throwable error) { delegate.onError(error); }
        public void onComplete() { delegate.onComplete(); }
    }

    String parse(String platform, String uid, String html) throws Exception {
        var document = Jsoup.parse(html);
        JsonNode profile;
        if ("luogu".equals(platform)) {
            var script = document.getElementById("lentille-context");
            if (script == null) throw new IllegalStateException("missing public profile");
            JsonNode root = mapper.readTree(script.data());
            if (root.path("status").asInt() != 200 || !"user.show".equals(root.path("template").asText()))
                throw new IllegalStateException("wrong page");
            profile = root.path("data").path("user");
            if (!uid.equals(profile.path("uid").asText())) throw new IllegalStateException("wrong identity");
        } else if ("nowcoder".equals(platform)) {
            JsonNode state = null;
            for (var script : document.select("script")) {
                String data = script.data().trim();
                String prefix = "window.__INITIAL_STATE__=";
                if (data.startsWith(prefix)) {
                    // Jackson reads the JSON value only; the following script is not evaluated.
                    state = mapper.readTree(data.substring(prefix.length()));
                    break;
                }
            }
            if (state == null) throw new IllegalStateException("missing public profile");
            profile = state.path("store").path("profile").path("profile");
            String identity = profile.hasNonNull("userId") ? profile.path("userId").asText()
                    : profile.hasNonNull("uid") ? profile.path("uid").asText() : profile.path("id").asText();
            if (!uid.equals(identity)) throw new IllegalStateException("wrong identity");
        } else throw new IllegalArgumentException("unknown platform");
        if (!profile.path("introduction").isTextual()) throw new IllegalStateException("missing introduction");
        return profile.path("introduction").asText();
    }
}
