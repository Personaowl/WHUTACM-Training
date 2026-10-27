package com.whut.training.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.whut.training.aspect.annotation.ServiceLog;
import com.whut.training.domain.dto.UpcomingContestItem;
import com.whut.training.exception.BusinessException;
import com.whut.training.repository.ContestSnapshotRepository;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Supplier;

/**
 * 聚合 Codeforces、AtCoder、牛客和洛谷的近期比赛。
 *
 * <p>四个来源并发读取并分别保留最后一次成功快照。单个平台临时不可用时，
 * 仍会返回其他平台以及该平台的旧快照，避免一个外站故障拖垮整页。
 * 类名暂时保留以兼容已有控制器与测试，职责已扩展为统一比赛聚合服务。
 */
@Service
@ServiceLog
public class AtCoderContestService implements ContestService {

    private static final Logger log = LoggerFactory.getLogger(AtCoderContestService.class);
    private static final String USER_AGENT = "WHUT-ACM Training Platform/1.0";
    private static final String ATCODER_ORIGIN = "https://atcoder.jp";
    private static final String NOWCODER_ORIGIN = "https://ac.nowcoder.com";
    private static final String LUOGU_ORIGIN = "https://www.luogu.com.cn";
    private static final DateTimeFormatter ATCODER_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssxx");
    private static final DateTimeFormatter CHINA_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    private final String atCoderUrl;
    private final String codeforcesUrl;
    private final String nowcoderUrl;
    private final String luoguUrl;
    private final Duration cacheDuration;
    private final Executor contestFetchExecutor;
    private final Clock clock;
    private final ContestSnapshotRepository snapshots;
    private final boolean warmupEnabled;
    private final Object refreshLock = new Object();
    private boolean snapshotsLoaded;
    private CompletableFuture<List<UpcomingContestItem>> refreshing;
    private volatile Instant retryAfter = Instant.EPOCH;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private volatile CacheSnapshot cacheSnapshot = new CacheSnapshot(List.of(), Instant.EPOCH);
    private volatile Map<String, List<UpcomingContestItem>> lastSuccessfulByPlatform = Map.of();

    AtCoderContestService(
            String atCoderUrl,
            long cacheMinutes,
            String codeforcesUrl,
            String nowcoderUrl,
            String luoguUrl
    ) {
        this(atCoderUrl, cacheMinutes, codeforcesUrl, nowcoderUrl, luoguUrl, ForkJoinPool.commonPool(), Clock.systemUTC());
    }

    @Autowired
    public AtCoderContestService(
            @Value("${contests.atcoder-url:https://atcoder.jp/contests/?lang=en}") String atCoderUrl,
            @Value("${contests.cache-minutes:15}") long cacheMinutes,
            @Value("${contests.codeforces-url:https://codeforces.com/api/contest.list?gym=false}") String codeforcesUrl,
            @Value("${contests.nowcoder-url:https://ac.nowcoder.com/acm/contest/vip-index}") String nowcoderUrl,
            @Value("${contests.luogu-url:https://www.luogu.com.cn/contest/list}") String luoguUrl,
            @Qualifier("contestFetchExecutor") Executor contestFetchExecutor,
            ContestSnapshotRepository snapshots,
            @Value("${contests.warmup-enabled:true}") boolean warmupEnabled
    ) {
        this(atCoderUrl, cacheMinutes, codeforcesUrl, nowcoderUrl, luoguUrl, contestFetchExecutor,
                Clock.systemUTC(), snapshots, warmupEnabled);
    }

    AtCoderContestService(String atCoderUrl, long cacheMinutes, String codeforcesUrl, String nowcoderUrl,
                         String luoguUrl, Executor contestFetchExecutor, Clock clock) {
        this(atCoderUrl, cacheMinutes, codeforcesUrl, nowcoderUrl, luoguUrl, contestFetchExecutor, clock, null, false);
    }

    AtCoderContestService(String atCoderUrl, long cacheMinutes, String codeforcesUrl, String nowcoderUrl,
                         String luoguUrl, Executor contestFetchExecutor, Clock clock,
                         ContestSnapshotRepository snapshots, boolean warmupEnabled) {
        this.atCoderUrl = atCoderUrl;
        this.codeforcesUrl = codeforcesUrl;
        this.nowcoderUrl = nowcoderUrl;
        this.luoguUrl = luoguUrl;
        this.cacheDuration = Duration.ofMinutes(Math.max(1, cacheMinutes));
        this.contestFetchExecutor = contestFetchExecutor;
        this.clock = clock;
        this.snapshots = snapshots;
        this.warmupEnabled = warmupEnabled;
    }

    // Only local snapshot loading and task submission happen here; no waiting for external OJs.
    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(fixedDelayString = "${contests.warmup-check-ms:60000}",
            initialDelayString = "${contests.warmup-initial-delay-ms:60000}")
    public void warmCache() {
        if (!warmupEnabled) return;
        restoreSnapshots();
        refreshIfNeeded();
    }

    @Override
    public List<UpcomingContestItem> getUpcomingContests() {
        restoreSnapshots();
        Instant now = clock.instant();
        CacheSnapshot current = cacheSnapshot;
        if (now.isBefore(current.expiresAt())) {
            return current.items();
        }

        CompletableFuture<List<UpcomingContestItem>> refresh = refreshIfNeeded();
        // 有历史快照时立即返回，外站延迟只影响后台刷新，不占用请求线程。
        if (!Instant.EPOCH.equals(current.expiresAt())) {
            return cacheSnapshot.items();
        }
        try {
            return refresh.join();
        } catch (CompletionException error) {
            if (error.getCause() instanceof BusinessException business) {
                throw business;
            }
            throw new BusinessException(503, "近期比赛暂时无法获取，请稍后重试");
        }
    }

    private CompletableFuture<List<UpcomingContestItem>> refreshIfNeeded() {
        synchronized (refreshLock) {
            Instant now = clock.instant();
            CacheSnapshot current = cacheSnapshot;
            if (now.isBefore(current.expiresAt())) {
                return CompletableFuture.completedFuture(current.items());
            }
            if (refreshing != null) {
                return refreshing;
            }
            if (now.isBefore(retryAfter)) {
                return CompletableFuture.failedFuture(new BusinessException(503, "近期比赛暂时无法获取，请稍后重试"));
            }
            CompletableFuture<List<UpcomingContestItem>> result = new CompletableFuture<>();
            refreshing = result;
            List<CompletableFuture<SourceResult>> tasks = List.of(
                    submitSource("CODEFORCES", this::fetchCodeforces),
                    submitSource("ATCODER", this::fetchAtCoder),
                    submitSource("NOWCODER", this::fetchNowcoder),
                    submitSource("LUOGU", this::fetchLuogu)
            );
            CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new)).whenComplete((ignored, failure) -> {
                try {
                    if (failure != null) {
                        throw new BusinessException(503, "近期比赛暂时无法获取，请稍后重试");
                    }
                    List<SourceResult> sources = tasks.stream().map(CompletableFuture::join).toList();
                    result.complete(mergeSources(sources));
                    // Complete waiting requests before writing the durable cache.
                    persistSuccessfulSources(sources);
                } catch (RuntimeException error) {
                    retryAfter = clock.instant().plusSeconds(30);
                    result.completeExceptionally(error);
                } finally {
                    synchronized (refreshLock) {
                        if (refreshing == result) refreshing = null;
                    }
                }
            });
            return result;
        }
    }

    private CompletableFuture<SourceResult> submitSource(String platform, Supplier<List<UpcomingContestItem>> fetcher) {
        try {
            return CompletableFuture.supplyAsync(() -> fetchSource(platform, fetcher), contestFetchExecutor);
        } catch (RuntimeException rejected) {
            return CompletableFuture.completedFuture(new SourceResult(platform, List.of(), rejected));
        }
    }

    private List<UpcomingContestItem> mergeSources(List<SourceResult> results) {
        Map<String, List<UpcomingContestItem>> nextByPlatform = new HashMap<>(lastSuccessfulByPlatform);
        int successfulSources = 0;
        for (SourceResult result : results) {
            if (result.error() == null) {
                nextByPlatform.put(result.platform(), result.items());
                successfulSources++;
            } else {
                log.warn("contest_source_unavailable platform={} reason={}",
                        result.platform(), result.error().getMessage());
            }
        }

        List<UpcomingContestItem> combined = combine(nextByPlatform);

        if (successfulSources == 0 && nextByPlatform.isEmpty()) {
            throw new BusinessException(503, "近期比赛暂时无法获取，请稍后重试");
        }

        lastSuccessfulByPlatform = Map.copyOf(nextByPlatform);
        Duration nextRefresh = successfulSources == 0 ? Duration.ofSeconds(30) : cacheDuration;
        cacheSnapshot = new CacheSnapshot(combined, clock.instant().plus(nextRefresh));
        return combined;
    }

    private List<UpcomingContestItem> combine(Map<String, List<UpcomingContestItem>> sources) {
        return sources.values().stream()
                .flatMap(List::stream)
                .collect(java.util.stream.Collectors.toMap(
                        item -> item.platform() + ":" + item.contestId(),
                        item -> item,
                        (left, right) -> left,
                        LinkedHashMap::new
                ))
                .values().stream()
                .sorted(Comparator.comparing(item -> OffsetDateTime.parse(item.startTime()).toInstant()))
                .limit(120)
                .toList();
    }

    private void restoreSnapshots() {
        synchronized (refreshLock) {
            if (snapshotsLoaded) return;
            snapshotsLoaded = true;
            if (snapshots == null) return;
            try {
                Map<String, List<UpcomingContestItem>> restored = new HashMap<>();
                for (var snapshot : snapshots.findAll()) {
                    if (!List.of("CODEFORCES", "ATCODER", "NOWCODER", "LUOGU").contains(snapshot.platform())
                            || snapshot.syncedAt().isBefore(clock.instant().minus(Duration.ofDays(1)))) continue;
                    try {
                        restored.put(snapshot.platform(), snapshot.items().stream()
                                .filter(item -> snapshot.platform().equals(item.platform()))
                                .filter(item -> OffsetDateTime.parse(item.startTime()).toInstant()
                                        .plus(Duration.ofMinutes(Math.max(0, item.durationMinutes()))).isAfter(clock.instant()))
                                .toList());
                    } catch (RuntimeException invalid) {
                        log.warn("Ignoring invalid contest snapshot items platform={}", snapshot.platform());
                    }
                }
                if (!restored.isEmpty()) {
                    lastSuccessfulByPlatform = Map.copyOf(restored);
                    // A restored snapshot is available immediately but always revalidated in the background.
                    cacheSnapshot = new CacheSnapshot(combine(restored), Instant.EPOCH.plusNanos(1));
                }
            } catch (RuntimeException error) {
                log.warn("Contest snapshot restore unavailable cause={}", error.getClass().getSimpleName());
            }
        }
    }

    private void persistSuccessfulSources(List<SourceResult> sources) {
        if (snapshots == null) return;
        for (var source : sources) {
            if (source.error() != null) continue;
            try {
                snapshots.save(source.platform(), source.items(), clock.instant());
            } catch (RuntimeException error) {
                log.warn("Contest snapshot save unavailable platform={} cause={}",
                        source.platform(), error.getClass().getSimpleName());
            }
        }
    }

    private SourceResult fetchSource(String platform, Supplier<List<UpcomingContestItem>> fetcher) {
        try {
            return new SourceResult(platform, fetcher.get(), null);
        } catch (Exception ex) {
            return new SourceResult(platform, List.of(), ex);
        }
    }

    List<UpcomingContestItem> fetchAtCoder() {
        return parseUpcomingContests(fetchDocument(atCoderUrl));
    }

    /** Returns recent and upcoming AtCoder contests for persistent ABC requirement tracking. */
    @Override
    public List<UpcomingContestItem> getAtCoderContestWindow() {
        return parseAtCoderContests(
                fetchDocument(atCoderUrl),
                "#contest-table-upcoming tbody tr, #contest-table-recent tbody tr"
        );
    }

    List<UpcomingContestItem> fetchNowcoder() {
        return parseNowcoderContests(fetchDocument(nowcoderUrl), Instant.now());
    }

    List<UpcomingContestItem> fetchLuogu() {
        return parseLuoguContests(fetchDocument(luoguUrl), Instant.now());
    }

    List<UpcomingContestItem> fetchCodeforces() {
        try {
            String json = Jsoup.connect(codeforcesUrl)
                    .userAgent(USER_AGENT)
                    .ignoreContentType(true)
                    .timeout(8_000)
                    .execute()
                    .body();
            return parseCodeforcesContests(json);
        } catch (Exception ex) {
            throw new IllegalStateException("Codeforces request failed", ex);
        }
    }

    private Document fetchDocument(String url) {
        try {
            return Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(8_000)
                    .get();
        } catch (Exception ex) {
            throw new IllegalStateException("contest page request failed", ex);
        }
    }

    List<UpcomingContestItem> parseUpcomingContests(Document document) {
        return parseAtCoderContests(document, "#contest-table-upcoming tbody tr");
    }

    private List<UpcomingContestItem> parseAtCoderContests(Document document, String rowSelector) {
        List<UpcomingContestItem> items = new ArrayList<>();
        for (Element row : document.select(rowSelector)) {
            List<Element> cells = row.select("td");
            Element contestLink = row.selectFirst("td:nth-child(2) a[href^='/contests/']");
            Element timeElement = row.selectFirst("time.fixtime-full");
            if (cells.size() < 4 || contestLink == null || timeElement == null) {
                continue;
            }

            String href = contestLink.attr("href");
            String contestId = href.substring(href.lastIndexOf('/') + 1);
            OffsetDateTime startTime = OffsetDateTime.parse(timeElement.text().trim(), ATCODER_TIME_FORMAT);
            items.add(new UpcomingContestItem(
                    "ATCODER",
                    contestId,
                    contestLink.text().trim(),
                    readContestType(cells.get(1)),
                    startTime.toString(),
                    parseClockDuration(cells.get(2).text().trim()),
                    cells.get(3).text().trim(),
                    ATCODER_ORIGIN + href
            ));
        }
        return List.copyOf(items);
    }

    List<UpcomingContestItem> parseCodeforcesContests(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            if (!"OK".equals(root.path("status").asText())) {
                throw new IllegalArgumentException("Codeforces returned non-OK status");
            }
            List<UpcomingContestItem> items = new ArrayList<>();
            for (JsonNode contest : root.path("result")) {
                if (!"BEFORE".equals(contest.path("phase").asText())) {
                    continue;
                }
                long startSeconds = contest.path("startTimeSeconds").asLong(0);
                if (startSeconds <= 0) {
                    continue;
                }
                String id = contest.path("id").asText();
                items.add(new UpcomingContestItem(
                        "CODEFORCES",
                        id,
                        contest.path("name").asText("Codeforces Contest " + id),
                        contest.path("type").asText("Contest"),
                        Instant.ofEpochSecond(startSeconds).toString(),
                        safeMinutes(contest.path("durationSeconds").asLong(0) / 60),
                        "-",
                        "https://codeforces.com/contest/" + id
                ));
            }
            return List.copyOf(items);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid Codeforces contest response", ex);
        }
    }

    List<UpcomingContestItem> parseNowcoderContests(Document document, Instant now) {
        List<UpcomingContestItem> items = new ArrayList<>();
        for (Element card : document.select(".platform-item")) {
            Element link = card.selectFirst("h4 a[href^='/acm/contest/']");
            Element time = card.selectFirst(".match-time-icon");
            if (link == null || time == null) {
                continue;
            }
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
                    "(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2})\\s*至\\s*(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2})")
                    .matcher(time.text());
            if (!matcher.find()) {
                continue;
            }
            try {
                OffsetDateTime start = LocalDateTime.parse(matcher.group(1), CHINA_TIME_FORMAT)
                        .atZone(SHANGHAI).toOffsetDateTime();
                OffsetDateTime end = LocalDateTime.parse(matcher.group(2), CHINA_TIME_FORMAT)
                        .atZone(SHANGHAI).toOffsetDateTime();
                if (!end.toInstant().isAfter(now)) {
                    continue;
                }
                String href = link.attr("href");
                String id = href.substring(href.lastIndexOf('/') + 1);
                Element rating = card.selectFirst(".icon-nc-flash2");
                boolean rated = card.selectFirst(".tag-rating") != null;
                items.add(new UpcomingContestItem(
                        "NOWCODER",
                        id,
                        link.text().trim(),
                        rated ? "Rated" : "Contest",
                        start.toString(),
                        safeMinutes(Duration.between(start, end).toMinutes()),
                        rating == null ? (rated ? "Rated" : "-") : rating.text().replace("不计Rating的范围：", ""),
                        NOWCODER_ORIGIN + href
                ));
            } catch (DateTimeParseException ignored) {
                // Skip a malformed card and keep the remaining official entries available.
            }
        }
        return List.copyOf(items);
    }

    List<UpcomingContestItem> parseLuoguContests(Document document, Instant now) {
        Element context = document.selectFirst("script#lentille-context");
        if (context == null) {
            throw new IllegalArgumentException("Luogu contest context is missing");
        }
        try {
            JsonNode contests = objectMapper.readTree(context.data()).path("data").path("contests").path("result");
            List<UpcomingContestItem> items = new ArrayList<>();
            for (JsonNode contest : contests) {
                long startSeconds = contest.path("startTime").asLong(0);
                long endSeconds = contest.path("endTime").asLong(startSeconds);
                if (startSeconds <= 0 || endSeconds <= now.getEpochSecond()) {
                    continue;
                }
                String id = contest.path("id").asText();
                items.add(new UpcomingContestItem(
                        "LUOGU",
                        id,
                        contest.path("name").asText("洛谷比赛 " + id),
                        luoguMethod(contest.path("method").asInt(-1)),
                        Instant.ofEpochSecond(startSeconds).toString(),
                        safeMinutes(Math.max(0, endSeconds - startSeconds) / 60),
                        contest.path("rated").asInt(0) > 0 ? "Rated" : "Unrated",
                        LUOGU_ORIGIN + "/contest/" + id
                ));
            }
            return List.copyOf(items);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid Luogu contest context", ex);
        }
    }

    private String readContestType(Element contestCell) {
        Element typeMarker = contestCell.selectFirst("span[title]");
        return typeMarker == null ? "Contest" : typeMarker.attr("title");
    }

    private int parseClockDuration(String durationText) {
        String[] parts = durationText.split(":");
        if (parts.length != 2) {
            return 0;
        }
        try {
            return safeMinutes(Long.parseLong(parts[0]) * 60 + Long.parseLong(parts[1]));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private int safeMinutes(long minutes) {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0, minutes));
    }

    private String luoguMethod(int method) {
        return switch (method) {
            case 1 -> "OI";
            case 2 -> "ICPC";
            case 3 -> "IOI";
            default -> "Contest";
        };
    }

    private record SourceResult(String platform, List<UpcomingContestItem> items, Exception error) {
    }

    private record CacheSnapshot(List<UpcomingContestItem> items, Instant expiresAt) {
    }
}
