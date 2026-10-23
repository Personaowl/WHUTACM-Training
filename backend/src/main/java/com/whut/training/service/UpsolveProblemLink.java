package com.whut.training.service;

import com.whut.training.exception.BusinessException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;

/** 仅解析链接，不发起外部请求；合并常见的同题不同 URL。 */
public final class UpsolveProblemLink {
    private UpsolveProblemLink() { }
    private static final Pattern CF_SET = Pattern.compile("/problemset/problem/(\\d+)/([A-Za-z0-9]+)/?");
    private static final Pattern CF_CONTEST = Pattern.compile("/(contest|gym)/(\\d+)/problem/([A-Za-z0-9]+)/?");

    public static String normalize(String raw) {
        try {
            URI uri = URI.create(raw == null ? "" : raw.trim()).normalize();
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            if ((!scheme.equals("http") && !scheme.equals("https")) || host.isEmpty()
                    || uri.getRawUserInfo() != null || uri.getPort() > 65535) throw new IllegalArgumentException();
            String path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
            int port = uri.getPort();
            boolean standardPort = port == -1 || (scheme.equals("https") && port == 443) || (scheme.equals("http") && port == 80);
            if (standardPort && (host.equals("codeforces.com") || host.equals("www.codeforces.com"))) {
                var set = CF_SET.matcher(path);
                if (set.matches()) return "https://codeforces.com/problemset/problem/" + set.group(1) + "/" + set.group(2).toUpperCase(Locale.ROOT);
                var contest = CF_CONTEST.matcher(path);
                if (contest.matches()) return contest.group(1).equals("gym")
                        ? "https://codeforces.com/gym/" + contest.group(2) + "/problem/" + contest.group(3).toUpperCase(Locale.ROOT)
                        : "https://codeforces.com/problemset/problem/" + contest.group(2) + "/" + contest.group(3).toUpperCase(Locale.ROOT);
            }
            if (standardPort && (host.equals("www.luogu.com.cn") || host.equals("luogu.com.cn"))
                    && path.matches("/problem/[A-Za-z0-9_-]+/?")) {
                return "https://www.luogu.com.cn" + path.replaceAll("/$", "");
            }
            if (standardPort && host.equals("atcoder.jp") && path.matches("/contests/[^/]+/tasks/[^/]+/?")) {
                return "https://atcoder.jp" + path.replaceAll("/$", "");
            }
            String authority = host + (standardPort ? "" : ":" + port);
            String result = scheme + "://" + authority + path + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
            if (result.length() > 1000) throw new IllegalArgumentException();
            return result;
        } catch (IllegalArgumentException error) {
            throw new BusinessException(400, "题目链接必须是有效的 http/https 地址，且不能包含账号密码");
        }
    }

    public static String hash(String link) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(link.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    public static String fallbackTitle(String link) {
        URI uri = URI.create(link);
        String path = uri.getPath().replaceAll("/$", "");
        String last = path.substring(path.lastIndexOf('/') + 1);
        String title = uri.getHost() + (last.isBlank() ? "" : " · " + last);
        return title.length() > 255 ? title.substring(0, 255) : title;
    }
}
