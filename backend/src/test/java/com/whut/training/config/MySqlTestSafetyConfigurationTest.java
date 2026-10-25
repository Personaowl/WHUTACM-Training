package com.whut.training.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MySqlTestSafetyConfigurationTest {
    private final String safe = "jdbc:mysql://127.0.0.1:33306/whut_acm_test_012345abcdef?useSSL=false";
    @Test void permitsOnlyTheIsolatedLocalTestTarget() {
        assertDoesNotThrow(() -> MySqlTestSafetyConfiguration.requireIsolatedTarget(safe, "whut_test", "local-test"));
    }
    @Test void refusesNormalPortsRemoteHostsAndNonTestSchemas() {
        for (String url : new String[]{null, safe.replace("33306", "3306"), safe.replace("33306", "33060"),
                safe.replace("127.0.0.1", "example.org"), safe.replace("whut_acm_test_012345abcdef", "whut_training"),
                safe.replace("012345abcdef", "not-random"), "jdbc:h2:mem:training"}) {
            assertThrows(IllegalStateException.class, () -> MySqlTestSafetyConfiguration.requireIsolatedTarget(url, "whut_test", "local-test"));
        }
    }
    @Test void refusesRootAndMissingCredentials() {
        assertThrows(IllegalStateException.class, () -> MySqlTestSafetyConfiguration.requireIsolatedTarget(safe, "root", "local-test"));
        assertThrows(IllegalStateException.class, () -> MySqlTestSafetyConfiguration.requireIsolatedTarget(safe, "whut_test", ""));
    }
}
