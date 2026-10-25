package com.whut.training.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** Public AtCoder profile/history client plus the optional AtCoder Problems submission source. */
@Service
public class AtCoderApiService {
    private static final String USER_AGENT = "WHUT-ACM Training Platform/1.0";
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String origin;
    private final String submissionsUrl;
    private final Object submissionRateLock = new Object();
    private long lastSubmissionRequestAtMillis;

    public AtCoderApiService(
            @Value("${atcoder.origin:https://atcoder.jp}") String origin,
            @Value("${atcoder-problems.submissions-url:https://kenkoooo.com/atcoder/atcoder-api/v3/user/submissions}") String submissionsUrl
    ) {
        this.origin = origin.replaceAll("/+$", "");
        this.submissionsUrl = submissionsUrl;
    }

    public Optional<AtCoderPublicProfile> getPublicProfile(String handle) {
        try {
            Document document = Jsoup.connect(profileUrl(handle)).userAgent(USER_AGENT).timeout(8_000).get();
            if (document.title().startsWith("Sign In") || document.selectFirst("#main-container") == null) {
                return Optional.empty();
            }
            return Optional.of(new AtCoderPublicProfile(handle, readAffiliation(document), profileUrl(handle)));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    public List<AtCoderHistoryEntry> getHistory(String handle) {
        try {
            String json = Jsoup.connect(profileUrl(handle) + "/history/json")
                    .userAgent(USER_AGENT).ignoreContentType(true).timeout(10_000).execute().body();
            return parseHistory(json);
        } catch (Exception ex) {
            throw new IllegalStateException("AtCoder history is unavailable", ex);
        }
    }

    public AcceptedProblems getAcceptedProblems(String handle, String contestId, long startSeconds, long endSeconds) {
        try {
            acquireSubmissionRequestSlot();
            String json = Jsoup.connect(submissionsUrl)
                    .data("user", handle)
                    .data("from_second", String.valueOf(Math.max(0, startSeconds - 30)))
                    .userAgent(USER_AGENT).ignoreContentType(true).timeout(12_000).execute().body();
            return parseAcceptedProblems(json, contestId, startSeconds, endSeconds);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("AtCoder submission sync was interrupted", ex);
        } catch (Exception ex) {
            throw new IllegalStateException("AtCoder Problems submissions are unavailable", ex);
        }
    }

    String readAffiliation(Document document) {
        for (Element row : document.select("table.dl-table tr")) {
            Element label = row.selectFirst("th");
            Element value = row.selectFirst("td");
            if (label != null && value != null && "affiliation".equalsIgnoreCase(label.text().trim())) {
                return value.text().trim();
            }
        }
        return "";
    }

    List<AtCoderHistoryEntry> parseHistory(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            List<AtCoderHistoryEntry> entries = new ArrayList<>();
            for (JsonNode item : root) {
                String screenName = item.path("ContestScreenName").asText("");
                String contestId = screenName.replaceFirst("\\.contest\\.atcoder\\.jp$", "");
                if (contestId.isBlank()) continue;
                entries.add(new AtCoderHistoryEntry(
                        contestId.toLowerCase(Locale.ROOT),
                        item.path("ContestName").asText(contestId),
                        item.path("IsRated").asBoolean(false),
                        nullableInt(item, "Place"), nullableInt(item, "Performance"),
                        nullableInt(item, "OldRating"), nullableInt(item, "NewRating"),
                        OffsetDateTime.parse(item.path("EndTime").asText()).toEpochSecond()
                ));
            }
            return List.copyOf(entries);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid AtCoder history response", ex);
        }
    }

    AcceptedProblems parseAcceptedProblems(String json, String contestId, long startSeconds, long endSeconds) {
        try {
            JsonNode root = objectMapper.readTree(json);
            Set<String> problemIds = new LinkedHashSet<>();
            for (JsonNode item : root) {
                long submittedAt = item.path("epoch_second").asLong(0);
                if (submittedAt < startSeconds || submittedAt > endSeconds) continue;
                if (!contestId.equalsIgnoreCase(item.path("contest_id").asText())) continue;
                if (!"AC".equalsIgnoreCase(item.path("result").asText())) continue;
                String problemId = item.path("problem_id").asText();
                if (!problemId.isBlank()) problemIds.add(problemId);
            }
            return new AcceptedProblems(problemIds.size(), List.copyOf(problemIds));
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid AtCoder submissions response", ex);
        }
    }

    public String profileUrl(String handle) {
        return origin + "/users/" + handle;
    }

    void acquireSubmissionRequestSlot() throws InterruptedException {
        synchronized (submissionRateLock) {
            long waitMillis = 1_100 - (System.currentTimeMillis() - lastSubmissionRequestAtMillis);
            if (waitMillis > 0) Thread.sleep(waitMillis);
            lastSubmissionRequestAtMillis = System.currentTimeMillis();
        }
    }

    private Integer nullableInt(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asInt();
    }

    public record AtCoderPublicProfile(String handle, String affiliation, String profileUrl) {}
    public record AtCoderHistoryEntry(String contestId, String contestName, boolean rated, Integer place,
                                      Integer performance, Integer oldRating, Integer newRating, long endTimeSeconds) {}
    public record AcceptedProblems(int count, List<String> problemIds) {}
}
