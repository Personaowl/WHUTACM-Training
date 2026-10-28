package com.whut.training.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.whut.training.domain.dto.PlatformDetails;
import com.whut.training.domain.dto.PlatformDetails.*;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Bounded, fixed-origin public reads. Never consumes user cookies or follows profile links. */
@Component
public class PublicPlatformDetailClient {
    private final PublicPlatformStatisticsClient publicClient;
    private final AtCoderApiService atcoder;
    private final ObjectMapper mapper;
    private final Map<String, Long> lastRequest = new HashMap<>();
    private String get(String platform, String url) {
        // Submission API requests already acquired the shared weekly-ABC gate immediately
        // before this call. Do not add a second delay after that shared reservation.
        if ("atcoder".equals(platform)) return publicClient.get(url);
        synchronized (lastRequest) {
            try {
                long pause = 1100 - (System.nanoTime() - lastRequest.getOrDefault(platform, 0L)) / 1000000;
                if (pause > 0) Thread.sleep(pause);
                lastRequest.put(platform, System.nanoTime());
            } catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IllegalStateException(error); }
        }
        return publicClient.get(url);
    }
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final String[] LUOGU_LABELS = {"暂无评定", "入门", "普及−", "普及/提高−", "普及+/提高", "提高+/省选−", "省选/NOI−", "NOI/NOI+/CTSC"};
    private static final String[] LUOGU_COLORS = {"#9ba3b2", "#eb6b73", "#efa45a", "#edc458", "#68ba79", "#5a9bdd", "#9b7bd6", "#49546a"};

    public PublicPlatformDetailClient(PublicPlatformStatisticsClient publicClient, AtCoderApiService atcoder, ObjectMapper mapper) {
        this.publicClient = publicClient; this.atcoder = atcoder; this.mapper = mapper;
    }

    public PlatformDetails fetch(String platform, String account) {
        if ("atcoder".equals(platform)) {
            if (account == null || !account.matches("[A-Za-z0-9_]{1,32}")) throw new IllegalArgumentException("Invalid handle");
            List<ContestStat> contests = null;
            List<ActivityDay> activity = null;
            boolean partial = false;
            try { contests = parseAtcoderHistory(get(platform, "https://atcoder.jp/users/" + account + "/history/json")); }
            catch (RuntimeException ignored) { partial = true; }
            try {
                long from = LocalDate.now(ZONE).minusDays(364).atStartOfDay(ZONE).toEpochSecond();
                Map<Long, JsonNode> submissions = new LinkedHashMap<>();
                boolean complete = false;
                for (int page = 0; page < 4; page++) {
                    atcoder.acquireSubmissionRequestSlot();
                    var root = json(get(platform, "https://kenkoooo.com/atcoder/atcoder-api/v3/user/submissions?user=" + account + "&from_second=" + from));
                    if (!root.isArray() || root.size() > 500) throw new IllegalArgumentException("Invalid AtCoder submissions");
                    long latest = from;
                    for (var row : root) {
                        if (!row.path("user_id").asText().equalsIgnoreCase(account)) throw new IllegalArgumentException("Wrong submission owner");
                        if (!row.path("id").canConvertToLong() || !row.path("epoch_second").canConvertToLong()) throw new IllegalArgumentException("Missing submission identity");
                        submissions.put(row.path("id").asLong(), row);
                        latest = Math.max(latest, row.path("epoch_second").asLong());
                    }
                    if (root.size() < 500) { complete = true; break; }
                    if (latest <= from) break;
                    from = latest; // inclusive overlap, deduplicated by submission id
                }
                activity = parseAtcoderActivity(account, submissions.values());
                partial |= !complete;
            } catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IllegalStateException(error); }
            catch (RuntimeException ignored) { partial = true; }
            return checked(platform, account, contests, null, activity, "AC_SUBMISSIONS", partial,
                    "AtCoder Algorithm Rated 比赛；AC 日期来自 AtCoder Problems，最近一年最多读取 4 页，按北京时间统计。");
        }
        if (account == null || !account.matches("[1-9][0-9]{0,17}")) throw new IllegalArgumentException("Invalid UID");
        if ("luogu".equals(platform)) {
            List<DifficultyStat> difficulties = null; List<ActivityDay> activity = null;
            try { difficulties = parseLuoguPractice(account, get(platform, "https://www.luogu.com.cn/user/" + account + "/practice")); }
            catch (RuntimeException ignored) { /* Independent sections can still be available. */ }
            try { activity = parseLuoguActivity(account, get(platform, "https://www.luogu.com.cn/user/" + account)); }
            catch (RuntimeException ignored) { /* Missing is not zero. */ }
            return checked(platform, account, null, difficulties, activity, "PUBLIC_ACTIVITY", true,
                    "洛谷公开练习页的已通过题目难度；公开日历仅作为活动记录，不并入 AC 次数。公开练习列表不含标签，本次不逐题抓取，也不展示 Rated 比赛。");
        }
        if ("nowcoder".equals(platform)) {
            List<ContestStat> contests = null; List<ActivityDay> activity = null;
            try { contests = parseNowcoderHistory(get(platform, "https://ac.nowcoder.com/acm/contest/rating-history?uid=" + account)); }
            catch (RuntimeException ignored) { /* Keep available activity if ratings fail. */ }
            try {
                Map<String, TimedAc> unique = new LinkedHashMap<>();
                for (int page = 1; page <= 3; page++) {
                    var rows = parseNowcoderActivity(account, get(platform, "https://ac.nowcoder.com/acm/contest/profile/" + account
                            + "/practice-coding?statusTypeFilter=5&pageSize=100&page=" + page));
                    for (var row : rows) unique.putIfAbsent(row.id(), row);
                    if (rows.size() < 100) break;
                }
                activity = days(unique.values().stream().map(TimedAc::second).toList());
            } catch (RuntimeException ignored) { /* Keep ratings if submissions are private/changed. */ }
            return checked(platform, account, contests, null, activity, "AC_SUBMISSIONS", true,
                    "Nowcoder 公开 Rating 比赛；做题日期仅含公开竞赛练习提交，最多 3 页，不代表全部历史，也不包含需登录的每日题打卡。");
        }
        throw new IllegalArgumentException("Unknown platform");
    }

    List<ContestStat> parseAtcoderHistory(String text) {
        var root = json(text); if (!root.isArray() || root.size() > 10000) throw new IllegalArgumentException("Invalid history");
        Map<String, ContestStat> result = new HashMap<>();
        for (var row : root) {
            if (!row.path("IsRated").asBoolean()) continue;
            String id = row.path("ContestScreenName").asText().replaceFirst("\\.contest\\.atcoder\\.jp$", "");
            if (!id.matches("[a-zA-Z0-9_-]{1,80}")) continue;
            Integer old = integer(row, "OldRating"), next = integer(row, "NewRating");
            if (next == null) continue;
            long second = OffsetDateTime.parse(row.path("EndTime").asText()).toEpochSecond();
            result.put(id, new ContestStat(id, text(row, "ContestName", id), integer(row, "Place"), old, next,
                    old == null ? null : next - old, second, "https://atcoder.jp/contests/" + id));
        }
        return recent(result.values());
    }

    List<ContestStat> parseNowcoderHistory(String text) {
        var root = json(text);
        if (root.path("code").asInt(-1) != 0 || !root.path("data").isArray() || root.path("data").size() > 10000)
            throw new IllegalArgumentException("Invalid Nowcoder ratings");
        Map<String, ContestStat> result = new HashMap<>();
        for (var row : root.path("data")) {
            String id = row.path("contestId").asText();
            Integer next = integer(row, "rating"), delta = integer(row, "changeValue");
            if (!id.matches("[1-9][0-9]{0,17}") || next == null) continue;
            long second = row.path("time").asLong(); if (second > 10000000000L) second /= 1000;
            if (second <= 0) continue;
            var item = new ContestStat(id, text(row, "contestName", "Nowcoder Rated 比赛"), integer(row, "rank"),
                    delta == null ? null : next - delta, next, delta, second, "https://ac.nowcoder.com/acm/contest/" + id);
            if (!result.containsKey(id) || result.get(id).ratingUpdateTimeSeconds() <= second) result.put(id, item);
        }
        return recent(result.values());
    }

    List<DifficultyStat> parseLuoguPractice(String account, String html) {
        var data = luogu(account, html);
        if (!data.path("passed").isArray() || data.path("passed").size() > 50000) throw new IllegalArgumentException("No public practice inventory");
        Map<Integer, Integer> counts = new TreeMap<>(); Set<String> seen = new HashSet<>();
        for (var row : data.path("passed")) {
            String pid = row.path("pid").asText();
            if (!pid.matches("[A-Za-z0-9_-]{1,40}") || !seen.add(pid)) continue;
            Integer difficulty = integer(row, "difficulty");
            counts.merge(difficulty != null && difficulty >= 0 && difficulty <= 7 ? difficulty : 0, 1, Integer::sum);
        }
        return counts.entrySet().stream().map(entry -> new DifficultyStat(LUOGU_LABELS[entry.getKey()], entry.getValue(), LUOGU_COLORS[entry.getKey()])).toList();
    }

    List<ActivityDay> parseLuoguActivity(String account, String html) {
        var data = luogu(account, html); var daily = data.path("dailyCounts");
        if (!daily.isObject() || daily.size() > 5000) throw new IllegalArgumentException("No public calendar");
        Map<String, Integer> counts = new TreeMap<>();
        daily.fields().forEachRemaining(entry -> {
            var day = LocalDate.parse(entry.getKey()); var value = entry.getValue();
            var count = value.isArray() && !value.isEmpty() ? value.get(0) : value;
            if (count.isIntegralNumber() && count.canConvertToInt() && count.asInt() >= 0 && inYear(day)) counts.put(day.toString(), count.asInt());
        });
        return counts.entrySet().stream().map(entry -> new ActivityDay(entry.getKey(), entry.getValue())).toList();
    }

    List<ActivityDay> parseAtcoderActivity(String account, Collection<JsonNode> rows) {
        Map<Long, Long> unique = new HashMap<>();
        for (var row : rows) {
            if (!row.path("user_id").asText().equalsIgnoreCase(account)) throw new IllegalArgumentException("Wrong account");
            if ("AC".equals(row.path("result").asText()) && row.path("id").canConvertToLong())
                unique.putIfAbsent(row.path("id").asLong(), row.path("epoch_second").asLong());
        }
        return days(unique.values());
    }

    record TimedAc(String id, long second) { }
    List<TimedAc> parseNowcoderActivity(String account, String html) {
        var doc = Jsoup.parse(html);
        if (doc.select(".profile-menu a[href='/acm/contest/profile/" + account + "/practice-coding']").isEmpty()) throw new IllegalArgumentException("Wrong account");
        if (doc.select("table").stream().noneMatch(table -> table.text().contains("提交时间"))) throw new IllegalArgumentException("No public submission table");
        List<TimedAc> rows = new ArrayList<>();
        for (var row : doc.select("table tr")) {
            var cells = row.select("td"); if (cells.size() < 9) continue;
            if (!List.of("答案正确", "Accepted", "AC").contains(cells.get(2).text().trim())) continue;
            String id = cells.get(0).text().trim(); if (!id.matches("[0-9]{1,25}")) continue;
            long second = LocalDateTime.parse(cells.get(8).text().trim(), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")).atZone(ZONE).toEpochSecond();
            rows.add(new TimedAc(id, second));
        }
        return rows;
    }

    private List<ActivityDay> days(Collection<Long> seconds) {
        Map<String, Integer> counts = new TreeMap<>();
        for (long second : seconds) if (second > 0) {
            var day = Instant.ofEpochSecond(second).atZone(ZONE).toLocalDate();
            if (inYear(day)) counts.merge(day.toString(), 1, Integer::sum);
        }
        return counts.entrySet().stream().map(entry -> new ActivityDay(entry.getKey(), entry.getValue())).toList();
    }
    private boolean inYear(LocalDate date) { var today = LocalDate.now(ZONE); return !date.isAfter(today) && !date.isBefore(today.minusDays(364)); }
    private JsonNode luogu(String account, String html) {
        var script = Jsoup.parse(html).getElementById("lentille-context"); if (script == null) throw new IllegalArgumentException("No Luogu data");
        var root = json(script.data()); var data = root.path("data");
        if (root.path("status").asInt() != 200 || !"user.show".equals(root.path("template").asText()) || !account.equals(data.path("user").path("uid").asText()))
            throw new IllegalArgumentException("Wrong Luogu account");
        return data;
    }
    private JsonNode json(String text) { try { return mapper.readTree(text); } catch (Exception error) { throw new IllegalArgumentException("Invalid public JSON", error); } }
    private Integer integer(JsonNode row, String field) { var n = row.path(field); return n.isNumber() && n.canConvertToInt() && n.asDouble() == n.asInt() ? n.asInt() : null; }
    private String text(JsonNode row, String field, String fallback) { String value = row.path(field).asText(fallback); return value.substring(0, Math.min(value.length(), 200)); }
    private List<ContestStat> recent(Collection<ContestStat> rows) { return rows.stream().sorted(Comparator.comparing(ContestStat::ratingUpdateTimeSeconds).reversed()).limit(8).toList(); }
    private PlatformDetails checked(String platform, String account, List<ContestStat> contests, List<DifficultyStat> difficulty,
            List<ActivityDay> activity, String metric, boolean partial, String note) {
        if (contests == null && difficulty == null && activity == null) throw new IllegalStateException("No public details");
        return new PlatformDetails(platform, account, Instant.now(), "READY", partial, note, null, difficulty, contests, activity, metric);
    }
}
