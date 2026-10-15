package com.whut.training.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductionSecurityValidatorTest {

    @Test
    void acceptsStrongProductionConfiguration() {
        var validator = new ProductionSecurityValidator(
                "database-secret-6Bz!2pQx", "private-root-owner",
                "administrator-secret-9Yt!7LmQ", "https://acm.example.com", false
        );

        assertDoesNotThrow(validator::validate);
    }

    @Test
    void rejectsDefaultSecretsAndInsecureCors() {
        assertThrows(IllegalStateException.class, () ->
                new ProductionSecurityValidator(
                        "123456", "superadmin", "superadmin123", "http://localhost:5173", false
                ).validate()
        );
    }

    @Test
    void httpIpRequiresExplicitOptIn() {
        assertThrows(IllegalStateException.class, () -> validator("http://203.0.113.10", false).validate());
        assertDoesNotThrow(() -> validator("http://203.0.113.10", true).validate());
        assertDoesNotThrow(() -> validator("http://203.0.113.10:8081", true).validate());
        assertDoesNotThrow(() -> validator("https://acm.example.com", false).validate());
    }

    @ParameterizedTest
    @ValueSource(strings = {"*", "http://localhost", "http://127.0.0.1", "http://0.0.0.0",
            "http://example.com", "http://203.0.113.10/path", "http://user@203.0.113.10",
            "http://203.0.113.10?x=1", "http://203.0.113.10#x", "http://203.0.113.10:0",
            "http://203.0.113.10:70000", "http://999.1.1.1", "http://224.0.0.1",
            "http://203.0.113.10/", "https://*.example.com", "https://[::1]", "https://acm.example.com/path"})
    void optInNeverAllowsWildcardsLocalAddressesDomainsOrInvalidOrigins(String origin) {
        assertThrows(IllegalStateException.class, () -> validator(origin, true).validate());
    }

    @Test
    void httpOptInDoesNotBypassSecretChecks() {
        assertThrows(IllegalStateException.class, () -> new ProductionSecurityValidator(
                "123456", "private-root-owner", "administrator-secret-9Yt!7LmQ", "http://203.0.113.10", true
        ).validate());
        assertThrows(IllegalStateException.class, () -> new ProductionSecurityValidator(
                "database-secret-6Bz!2pQx", "superadmin", "123456", "http://203.0.113.10", true
        ).validate());
    }

    private ProductionSecurityValidator validator(String origins, boolean allowHttp) {
        return new ProductionSecurityValidator("database-secret-6Bz!2pQx", "private-root-owner",
                "administrator-secret-9Yt!7LmQ", origins, allowHttp);
    }
}
