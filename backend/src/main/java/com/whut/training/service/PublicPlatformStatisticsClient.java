package com.whut.training.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.whut.training.service.PlatformStatisticsProvider.Statistics;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

/** Public pages only: fixed HTTPS hosts, validated identifiers, no cookies, redirects, or JavaScript execution. */
@Component
public class PublicPlatformStatisticsClient {
    private final ObjectMapper mapper;
    private final AtCoderApiService atcoder;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    public PublicPlatformStatisticsClient(ObjectMapper mapper, AtCoderApiService atcoder) {
        this.mapper = mapper; this.atcoder = atcoder;
    }

    public Statistics fetch(String platform, String account) {
        if ("atcoder".equals(platform)) {
            if (account == null || !account.matches("[A-Za-z0-9_]{1,32}")) throw new IllegalArgumentException("Invalid handle");
            String encoded = URLEncoder.encode(account, StandardCharsets.UTF_8);
            Statistics profile = null;
            Integer solved = null;
            try { profile = parseAtcoder(account, get("https://atcoder.jp/users/" + encoded + "?lang=en")); }
            catch (RuntimeException ignored) { /* Keep independently available AC count. */ }
            try {
                atcoder.acquireSubmissionRequestSlot(); // shared >1s gate with weekly ABC submission requests
                solved = parseAcceptedCount(get("https://kenkoooo.com/atcoder/atcoder-api/v3/user/ac_rank?user=" + encoded));
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt(); throw new IllegalStateException("Statistics sync interrupted", error);
            } catch (RuntimeException ignored) { /* Keep independently available ratings. */ }
            return checked(profile == null ? null : profile.rating(), profile == null ? null : profile.maxRating(), solved);
        }
        if (account == null || !account.matches("[1-9][0-9]{0,17}")) throw new IllegalArgumentException("Invalid UID");
        return switch (platform) {
            case "luogu" -> parseLuogu(account, get("https://www.luogu.com.cn/user/" + account));
            case "nowcoder" -> parseNowcoder(account, get("https://ac.nowcoder.com/acm/contest/profile/" + account + "/practice-coding"));
            default -> throw new IllegalArgumentException("Unknown platform");
        };
    }

    String get(String url) {
        var request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8))
                .header("User-Agent", "WHUT-Training-PlatformStatistics/1.0").GET().build();
        var pending = http.sendAsync(request, ignored -> new PublicProfileVerifier.LimitedBody());
        try {
            var response = pending.get(10, TimeUnit.SECONDS);
            if (response.statusCode() != 200) throw new IllegalStateException("Upstream HTTP " + response.statusCode());
            return response.body();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt(); throw new IllegalStateException("Statistics sync interrupted", error);
        } catch (Exception error) { throw new IllegalStateException("Public statistics unavailable", error); }
        finally { pending.cancel(true); }
    }

    Statistics parseAtcoder(String account, String html) {
        var doc = Jsoup.parse(html);
        if (doc.selectFirst("#main-container") == null || doc.select("a[href='/users/" + account + "']").isEmpty())
            throw new IllegalArgumentException("Not an AtCoder profile");
        // Algorithm is the first contest status table; do not combine Heuristic ratings.
        return checked(tableNumber(doc, "Rating"), tableNumber(doc, "Highest Rating"), null);
    }

    Integer parseAcceptedCount(String json) {
        try {
            var root = mapper.readTree(json);
            var value = integer(root, "count");
            if (value == null) throw new IllegalArgumentException("Missing accepted count");
            return value;
        } catch (java.io.IOException error) { throw new IllegalArgumentException("Invalid accepted count", error); }
    }

    Statistics parseLuogu(String account, String html) {
        try {
            var script = Jsoup.parse(html).getElementById("lentille-context");
            if (script == null) throw new IllegalArgumentException("Missing Luogu profile");
            var root = mapper.readTree(script.data());
            var user = root.path("data").path("user");
            if (root.path("status").asInt() != 200 || !"user.show".equals(root.path("template").asText())
                    || !account.equals(user.path("uid").asText())) throw new IllegalArgumentException("Wrong Luogu identity");
            // gu.rating is 咕值, NOT contest Rating. Historical elo list is not an all-time maximum.
            return checked(integer(user, "eloValue"), null, integer(user, "passedProblemCount"),
                    user.path("name").isTextual() ? user.path("name").asText() : null);
        } catch (java.io.IOException error) { throw new IllegalArgumentException("Invalid Luogu profile", error); }
    }

    Statistics parseNowcoder(String account, String html) {
        var doc = Jsoup.parse(html);
        if (doc.select(".profile-menu a[href='/acm/contest/profile/" + account + "/practice-coding']").isEmpty())
            throw new IllegalArgumentException("Wrong Nowcoder identity");
        // Only use the verified target profile header, never a feed author or a global navigation label.
        var name = doc.selectFirst(".profile-info-wrapper .coder-info-detail > .coder-name");
        String displayName = name == null ? null : name.attr("data-title").isBlank() ? name.text() : name.attr("data-title");
        return checked(labelNumber(doc, "Rating"), null, labelNumber(doc, "题已通过"), displayName);
    }

    private Integer tableNumber(Document doc, String label) {
        for (var row : doc.select("table tr")) {
            var heading = row.selectFirst("th"); var cell = row.selectFirst("td");
            if (heading != null && cell != null && label.equals(heading.text().trim())) return leadingNumber(cell.text());
        }
        return null;
    }

    private Integer labelNumber(Document doc, String label) {
        for (var item : doc.select(".status-item, .my-state-item")) {
            var heading = item.children().stream().filter(child -> child.tagName().equals("span")).findFirst().orElse(null);
            var number = item.selectFirst(".state-num");
            if (heading != null && number != null && label.equals(heading.text().trim())) return leadingNumber(number.text());
        }
        return null;
    }

    private Integer leadingNumber(String text) {
        var match = java.util.regex.Pattern.compile("^([0-9][0-9,]*)($|\\s|―)").matcher(text.trim());
        if (!match.find()) return null;
        try { return Integer.valueOf(match.group(1).replace(",", "")); }
        catch (NumberFormatException error) { return null; }
    }

    private Integer integer(JsonNode node, String field) {
        var value = node.get(field);
        return value != null && value.isIntegralNumber() && value.canConvertToInt() && value.intValue() >= 0 ? value.intValue() : null;
    }

    private Statistics checked(Integer rating, Integer maximum, Integer solved) {
        return checked(rating, maximum, solved, null);
    }

    private Statistics checked(Integer rating, Integer maximum, Integer solved, String displayName) {
        String name = displayName == null ? null : displayName.replaceAll("[\\p{Cc}\\p{Cf}]", "").strip();
        if (name != null && name.isBlank()) name = null;
        if (name != null) name = name.substring(0, name.offsetByCodePoints(0, Math.min(128, name.codePointCount(0, name.length()))));
        if (rating == null && maximum == null && solved == null && name == null)
            throw new IllegalArgumentException("No public statistics");
        return new Statistics(rating, maximum, solved, Instant.now(), rating == null || maximum == null || solved == null,
                false, name);
    }
}
