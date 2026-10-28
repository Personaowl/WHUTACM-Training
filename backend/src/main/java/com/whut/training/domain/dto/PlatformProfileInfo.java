package com.whut.training.domain.dto;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Optional public facts. No contact details, avatar URLs, or unbounded profile HTML. */
public record PlatformProfileInfo(Map<String, String> fields) {
    private static final Set<String> TEXT = Set.of("country", "city", "organization", "realName", "lastCompetedDate");
    private static final Set<String> NUMBERS = Set.of("globalRank", "ratedContestCount", "friendOfCount", "contribution",
            "followingCount", "followerCount", "attemptedCount", "acceptedSubmissionCount", "submissionCount",
            "registrationTimeSeconds", "lastOnlineTimeSeconds");

    public PlatformProfileInfo {
        var safe = new LinkedHashMap<String, String>();
        if (fields != null) fields.forEach((key, value) -> {
            if (value == null || (!TEXT.contains(key) && !NUMBERS.contains(key))) return;
            String cleaned = value.replaceAll("[\\p{Cc}\\p{Cf}]", "")
                    .replaceAll("WHUT(?:-ACM)?-[A-Fa-f0-9]{8,64}", "").strip();
            if (cleaned.isBlank()) return;
            if (NUMBERS.contains(key)) {
                try {
                    long number = Long.parseLong(cleaned);
                    if (number < -9_007_199_254_740_991L || number > 9_007_199_254_740_991L || (number < 0 && !"contribution".equals(key))
                            || (number == 0 && (key.endsWith("TimeSeconds") || "globalRank".equals(key)))) return;
                } catch (NumberFormatException invalid) { return; }
            }
            if ("lastCompetedDate".equals(key)) {
                try { java.time.LocalDate.parse(cleaned); }
                catch (java.time.format.DateTimeParseException invalid) { return; }
            }
            safe.put(key, cleaned.substring(0, cleaned.offsetByCodePoints(0, Math.min(128, cleaned.codePointCount(0, cleaned.length())))));
        });
        fields = Collections.unmodifiableMap(safe);
    }

    public static PlatformProfileInfo empty() { return new PlatformProfileInfo(Map.of()); }

    public PlatformProfileInfo with(String key, Object value) {
        var next = new LinkedHashMap<>(fields);
        if (value instanceof String || value instanceof Number) next.put(key, value.toString());
        return new PlatformProfileInfo(next);
    }
}
