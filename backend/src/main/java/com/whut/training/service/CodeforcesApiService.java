package com.whut.training.service;

import com.whut.training.aspect.annotation.ServiceLog;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.whut.training.domain.entity.CfProblem;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Codeforces 外部 API 客户端。
 *
 * <p>负责读取用户资料、题库和提交状态。当前实现会把大多数外部错误折叠为 empty / empty list，便于业务继续运行，但也意味着瞬时故障可能不会直接暴露，属于已知可观测性缺口。
 */
@Service
@ServiceLog
public class CodeforcesApiService {

    private final String baseUrl;
    private final CodeforcesRequestCoordinator requestCoordinator;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 创建 Codeforces API 服务。
     *
     * @param baseUrl Codeforces API 基础地址。
     */
    public CodeforcesApiService(
            @Value("${codeforces.base_url:https://codeforces.com/api}") String baseUrl,
            CodeforcesRequestCoordinator requestCoordinator
    ) {
        this.baseUrl = baseUrl;
        this.requestCoordinator = requestCoordinator;
    }

    /**
     * 获取用户资料。
     *
     * @param handle Codeforces handle。
     * @return 用户资料。
     */
    public Optional<CodeforcesUserProfile> getUserInfo(String handle) {
        if (handle == null || handle.isBlank()) {
            return Optional.empty();
        }

        String encodedHandle = URLEncoder.encode(handle.trim(), StandardCharsets.UTF_8);
        String url = baseUrl + "/user.info?handles=" + encodedHandle;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .timeout(Duration.ofSeconds(5))
                .build();

        try {
            HttpResponse<String> response = sendWithRateLimit(request, CodeforcesRequestCoordinator.Priority.NORMAL);
            if (response.statusCode() != 200) {
                return Optional.empty();
            }

            JsonNode root = objectMapper.readTree(response.body());
            if (!"OK".equalsIgnoreCase(root.path("status").asText())) {
                return Optional.empty();
            }

            JsonNode result = root.path("result");
            if (!result.isArray() || result.isEmpty()) {
                return Optional.empty();
            }

            JsonNode user = result.get(0);
            return Optional.of(new CodeforcesUserProfile(
                    nullableInt(user, "rating"),
                    nullableInt(user, "maxRating"),
                    nullableBoolean(user, "online"),
                    nullableLong(user, "lastOnlineTimeSeconds"),
                    nullableText(user, "titlePhoto")
            ));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    /**
     * 拉取 Codeforces 题库。
     *
     * @return 题目列表；失败时返回空列表。
     */
    public List<CfProblem> fetchProblemSet() {
        String url = baseUrl + "/problemset.problems";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .timeout(Duration.ofSeconds(10))
                .build();

        try {
            HttpResponse<String> response = sendWithRateLimit(request, CodeforcesRequestCoordinator.Priority.BACKGROUND);
            if (response.statusCode() != 200) {
                return List.of();
            }

            JsonNode root = objectMapper.readTree(response.body());
            if (!"OK".equalsIgnoreCase(root.path("status").asText())) {
                return List.of();
            }

            JsonNode result = root.path("result");
            JsonNode problemsNode = result.path("problems");
            JsonNode statsNode = result.path("problemStatistics");
            if (!problemsNode.isArray() || !statsNode.isArray()) {
                return List.of();
            }

            Map<String, Integer> solvedCountMap = new HashMap<>();
            for (JsonNode stat : statsNode) {
                Integer contestId = nullableInt(stat, "contestId");
                String index = nullableText(stat, "index");
                Integer solvedCount = nullableInt(stat, "solvedCount");
                if (contestId == null || index == null || solvedCount == null) {
                    continue;
                }
                solvedCountMap.put(buildProblemKey(contestId, index), solvedCount);
            }

            List<CfProblem> problems = new ArrayList<>();
            for (JsonNode node : problemsNode) {
                Integer contestId = nullableInt(node, "contestId");
                String problemIndex = nullableText(node, "index");
                String name = nullableText(node, "name");
                if (contestId == null || problemIndex == null || name == null) {
                    continue;
                }
                if (contestId >= 100000) {
                    continue;
                }
                String problemKey = buildProblemKey(contestId, problemIndex);
                Integer sourceContestId = nullableInt(node, "sourceContestId");
                Integer rating = nullableInt(node, "rating");
                boolean interactive = node.path("interactive").asBoolean(false);
                String tags = readTags(node.path("tags"));
                Integer solvedCount = solvedCountMap.get(problemKey);
                String sourceUrl = "https://codeforces.com/problemset/problem/" + contestId + "/" + problemIndex;
                problems.add(new CfProblem(
                        problemKey,
                        contestId,
                        problemIndex,
                        name,
                        rating,
                        tags,
                        interactive,
                        sourceContestId,
                        solvedCount,
                        sourceUrl
                ));
            }
            return problems;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return List.of();
        } catch (Exception ex) {
            return List.of();
        }
    }

    /**
     * 获取提交状态。
     *
     * @param handle       Codeforces handle。
     * @param submissionId 提交 ID。
     * @return 提交状态。
     */
    public Optional<SubmissionStatus> getSubmissionStatus(String handle, Long submissionId) {
        if (handle == null || handle.isBlank() || submissionId == null) {
            return Optional.empty();
        }

        String encodedHandle = URLEncoder.encode(handle.trim(), StandardCharsets.UTF_8);
        // A user may submit more than 1,000 times in a day or during a contest.
        // Codeforces permits up to 10,000 records here; checking the full supported
        // recent window prevents a valid Hard submission from being missed after an
        // Easy submission (or a long practice session).
        String url = baseUrl + "/user.status?handle=" + encodedHandle + "&from=1&count=10000";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .timeout(Duration.ofSeconds(8))
                .build();

        try {
            HttpResponse<String> response = sendWithRateLimit(request, CodeforcesRequestCoordinator.Priority.CHECK_IN);
            if (response.statusCode() != 200) {
                return Optional.empty();
            }
            JsonNode root = objectMapper.readTree(response.body());
            if (!"OK".equalsIgnoreCase(root.path("status").asText())) {
                return Optional.empty();
            }
            JsonNode result = root.path("result");
            if (!result.isArray()) {
                return Optional.empty();
            }

            for (JsonNode row : result) {
                Long id = nullableLong(row, "id");
                if (id == null || !id.equals(submissionId)) {
                    continue;
                }
                JsonNode problem = row.path("problem");
                Integer contestId = nullableInt(problem, "contestId");
                String index = nullableText(problem, "index");
                String verdict = nullableText(row, "verdict");
                Long creationTimeSeconds = nullableLong(row, "creationTimeSeconds");
                if (contestId == null || index == null) {
                    return Optional.empty();
                }
                return Optional.of(new SubmissionStatus(
                        submissionId,
                        contestId,
                        index,
                        verdict == null ? "UNKNOWN" : verdict,
                        creationTimeSeconds == null ? null : Instant.ofEpochSecond(creationTimeSeconds)
                ));
            }
            return Optional.empty();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    /**
     * 检查指定 Handle 是否在验证开始后向 Codeforces 1A 提交过编译错误。
     *
     * <p>返回 empty 表示 Codeforces API 当前不可用；返回 false 表示 API 请求成功，
     * 但没有找到符合条件的提交。
     *
     * @param handle         Codeforces Handle。
     * @param startedAtSeconds 验证开始时间（Unix 秒）。
     * @return 是否找到所有权验证提交。
     */
    public Optional<Boolean> hasOwnershipVerificationSubmission(String handle, long startedAtSeconds) {
        if (handle == null || handle.isBlank()) {
            return Optional.of(false);
        }

        String encodedHandle = URLEncoder.encode(handle.trim(), StandardCharsets.UTF_8);
        String url = baseUrl + "/user.status?handle=" + encodedHandle + "&from=1&count=30";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .timeout(Duration.ofSeconds(8))
                .build();

        try {
            HttpResponse<String> response = sendWithRateLimit(request, CodeforcesRequestCoordinator.Priority.NORMAL);
            if (response.statusCode() != 200) {
                return Optional.empty();
            }
            JsonNode root = objectMapper.readTree(response.body());
            if (!"OK".equalsIgnoreCase(root.path("status").asText())) {
                return Optional.empty();
            }
            JsonNode result = root.path("result");
            if (!result.isArray()) {
                return Optional.empty();
            }

            for (JsonNode row : result) {
                Long creationTimeSeconds = nullableLong(row, "creationTimeSeconds");
                if (creationTimeSeconds == null || creationTimeSeconds < startedAtSeconds) {
                    continue;
                }
                JsonNode problem = row.path("problem");
                Integer contestId = nullableInt(problem, "contestId");
                String index = nullableText(problem, "index");
                String verdict = nullableText(row, "verdict");
                if (Integer.valueOf(1).equals(contestId)
                        && "A".equalsIgnoreCase(index)
                        && "COMPILATION_ERROR".equalsIgnoreCase(verdict)) {
                    return Optional.of(true);
                }
            }
            return Optional.of(false);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    /**
     * 获取用户最近提交。empty 表示 API 不可用，空列表表示请求成功但没有提交。
     */
    public Optional<List<CodeforcesSubmission>> getUserSubmissions(String handle, int count) {
        if (handle == null || handle.isBlank()) {
            return Optional.of(List.of());
        }
        int safeCount = Math.max(1, Math.min(count, 10000));
        String encodedHandle = URLEncoder.encode(handle.trim(), StandardCharsets.UTF_8);
        String url = baseUrl + "/user.status?handle=" + encodedHandle + "&from=1&count=" + safeCount;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .timeout(Duration.ofSeconds(12))
                .build();

        try {
            HttpResponse<String> response = sendWithRateLimit(request, CodeforcesRequestCoordinator.Priority.NORMAL);
            if (response.statusCode() != 200) {
                return Optional.empty();
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode result = root.path("result");
            if (!"OK".equalsIgnoreCase(root.path("status").asText()) || !result.isArray()) {
                return Optional.empty();
            }

            List<CodeforcesSubmission> submissions = new ArrayList<>();
            for (JsonNode row : result) {
                JsonNode problem = row.path("problem");
                submissions.add(new CodeforcesSubmission(
                        nullableLong(row, "id"),
                        nullableInt(problem, "contestId"),
                        nullableText(problem, "index"),
                        nullableText(problem, "name"),
                        nullableInt(problem, "rating"),
                        readTagList(problem.path("tags")),
                        nullableText(row, "verdict"),
                        nullableLong(row, "creationTimeSeconds")
                ));
            }
            return Optional.of(submissions);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    /**
     * 获取用户全部 rated 比赛 rating 变化记录。
     */
    public Optional<List<CodeforcesRatingChange>> getUserRatingHistory(String handle) {
        if (handle == null || handle.isBlank()) {
            return Optional.of(List.of());
        }
        String encodedHandle = URLEncoder.encode(handle.trim(), StandardCharsets.UTF_8);
        String url = baseUrl + "/user.rating?handle=" + encodedHandle;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .timeout(Duration.ofSeconds(10))
                .build();

        try {
            HttpResponse<String> response = sendWithRateLimit(request, CodeforcesRequestCoordinator.Priority.NORMAL);
            if (response.statusCode() != 200) {
                return Optional.empty();
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode result = root.path("result");
            if (!"OK".equalsIgnoreCase(root.path("status").asText()) || !result.isArray()) {
                return Optional.empty();
            }

            List<CodeforcesRatingChange> changes = new ArrayList<>();
            for (JsonNode row : result) {
                changes.add(new CodeforcesRatingChange(
                        nullableLong(row, "contestId"),
                        nullableText(row, "contestName"),
                        nullableInt(row, "rank"),
                        nullableInt(row, "oldRating"),
                        nullableInt(row, "newRating"),
                        nullableLong(row, "ratingUpdateTimeSeconds")
                ));
            }
            return Optional.of(changes);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    /**
     * 所有 Codeforces 请求共用启动间隔限制，避免不同业务并发触发官方限流。
     */
    private HttpResponse<String> sendWithRateLimit(HttpRequest request, CodeforcesRequestCoordinator.Priority priority)
            throws java.io.IOException, InterruptedException {
        return requestCoordinator.execute(
                priority,
                () -> httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        );
    }

    /**
     * 读取整数字段。
     *
     * @param node  JSON 节点。
     * @param field 字段名。
     * @return 整数或 null。
     */
    private Integer nullableInt(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asInt();
    }

    /**
     * 读取长整型字段。
     *
     * @param node  JSON 节点。
     * @param field 字段名。
     * @return 长整型或 null。
     */
    private Long nullableLong(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asLong();
    }

    /**
     * 读取布尔字段。
     *
     * @param node  JSON 节点。
     * @param field 字段名。
     * @return 布尔值或 null。
     */
    private Boolean nullableBoolean(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asBoolean();
    }

    /**
     * 读取文本字段。
     *
     * @param node  JSON 节点。
     * @param field 字段名。
     * @return 文本或 null。
     */
    private String nullableText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text;
    }

    /**
     * 将标签数组转成逗号分隔文本。
     *
     * @param tagsNode 标签数组节点。
     * @return 标签字符串。
     */
    private String readTags(JsonNode tagsNode) {
        return String.join(",", readTagList(tagsNode));
    }

    private List<String> readTagList(JsonNode tagsNode) {
        if (!tagsNode.isArray()) {
            return List.of();
        }
        List<String> tags = new ArrayList<>();
        for (JsonNode tagNode : tagsNode) {
            String tag = tagNode.asText();
            if (tag != null && !tag.isBlank()) {
                tags.add(tag);
            }
        }
        return tags;
    }

    /**
     * 生成题目唯一键。
     *
     * @param contestId    比赛 ID。
     * @param problemIndex 题号。
     * @return 题目键。
     */
    private String buildProblemKey(Integer contestId, String problemIndex) {
        return contestId + "-" + problemIndex;
    }

    /**
     * Codeforces 用户资料摘要。
     *
     * @param rating               当前 rating。
     * @param maxRating            历史最高 rating。
     * @param online               在线状态。
     * @param lastOnlineTimeSeconds 最近在线时间戳。
     * @param avatarUrl            头像地址。
     */
    public record CodeforcesUserProfile(Integer rating, Integer maxRating, Boolean online,
                                        Long lastOnlineTimeSeconds, String avatarUrl) {
    }

    /**
     * Codeforces 提交状态摘要。
     *
     * @param submissionId 提交 ID。
     * @param contestId    比赛 ID。
     * @param problemIndex 题号。
     * @param verdict      判题结果。
     * @param creationTime 提交时间。
     */
    public record SubmissionStatus(Long submissionId, Integer contestId, String problemIndex, String verdict,
                                   Instant creationTime) {
    }

    public record CodeforcesSubmission(
            Long id,
            Integer contestId,
            String problemIndex,
            String problemName,
            Integer rating,
            List<String> tags,
            String verdict,
            Long creationTimeSeconds
    ) {
        public String problemKey() {
            if (contestId != null && problemIndex != null) {
                return contestId + "-" + problemIndex;
            }
            if (problemName != null && problemIndex != null) {
                return problemName + "-" + problemIndex;
            }
            return null;
        }
    }

    public record CodeforcesRatingChange(
            Long contestId,
            String contestName,
            Integer rank,
            Integer oldRating,
            Integer newRating,
            Long ratingUpdateTimeSeconds
    ) {
    }
}
