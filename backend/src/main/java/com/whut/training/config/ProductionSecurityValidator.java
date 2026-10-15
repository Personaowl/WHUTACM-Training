package com.whut.training.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

@Component
@Profile("prod")
public class ProductionSecurityValidator {

    private static final Set<String> FORBIDDEN_SECRETS = Set.of(
            "123456", "password", "root", "admin", "superadmin123",
            "replace-with-a-long-random-password",
            "replace-with-a-different-long-random-password"
    );

    private final String mysqlPassword;
    private final String superAdminUsername;
    private final String superAdminPassword;
    private final String allowedOrigins;
    private final boolean allowInsecureHttp;

    public ProductionSecurityValidator(
            @Value("${spring.datasource.password}") String mysqlPassword,
            @Value("${superAdmin.username}") String superAdminUsername,
            @Value("${superAdmin.password}") String superAdminPassword,
            @Value("${app.cors.allowed-origins}") String allowedOrigins,
            @Value("${app.security.allow-insecure-http:false}") boolean allowInsecureHttp
    ) {
        this.mysqlPassword = mysqlPassword;
        this.superAdminUsername = superAdminUsername;
        this.superAdminPassword = superAdminPassword;
        this.allowedOrigins = allowedOrigins;
        this.allowInsecureHttp = allowInsecureHttp;
    }

    @PostConstruct
    public void validate() {
        requireStrongSecret(mysqlPassword, "MYSQL_PASSWORD");
        requireStrongSecret(superAdminPassword, "SUPERADMIN_PASSWORD");
        if (superAdminUsername == null || superAdminUsername.isBlank()
                || "superadmin".equalsIgnoreCase(superAdminUsername.trim())
                || superAdminUsername.toLowerCase(Locale.ROOT).startsWith("replace-with")) {
            throw new IllegalStateException("SUPERADMIN_USERNAME must be a private, non-default production account name");
        }
        String[] origins = Arrays.stream(allowedOrigins == null ? new String[0] : allowedOrigins.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toArray(String[]::new);
        if (origins.length == 0 || Arrays.stream(origins).anyMatch(origin -> !isAllowedOrigin(origin))) {
            throw new IllegalStateException("CORS_ALLOWED_ORIGINS requires explicit HTTPS origins; temporary HTTP IPv4 origins require ALLOW_INSECURE_HTTP=true");
        }
    }

    private boolean isAllowedOrigin(String origin) {
        try {
            URI uri = new URI(origin);
            String host = uri.getHost();
            if (host == null || host.contains("*") || host.toLowerCase(Locale.ROOT).contains("localhost")
                    || host.startsWith("127.") || "[::1]".equals(host) || "0.0.0.0".equals(host)
                    || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || (uri.getRawPath() != null && !uri.getRawPath().isEmpty())
                    || uri.getPort() == 0 || uri.getPort() > 65535) return false;
            if ("https".equalsIgnoreCase(uri.getScheme())) return true;
            if (!allowInsecureHttp || !"http".equalsIgnoreCase(uri.getScheme())
                    || !host.matches("(?:0|[1-9]\\d{0,2})(?:\\.(?:0|[1-9]\\d{0,2})){3}")) return false;
            int[] octets = Arrays.stream(host.split("\\.")).mapToInt(Integer::parseInt).toArray();
            return octets[0] > 0 && octets[0] < 224 && Arrays.stream(octets).allMatch(value -> value <= 255);
        } catch (URISyntaxException | IllegalArgumentException ex) {
            return false;
        }
    }

    private void requireStrongSecret(String value, String name) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() < 16 || FORBIDDEN_SECRETS.contains(normalized.toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException(name + " must contain at least 16 non-default characters");
        }
    }
}
