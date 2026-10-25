package com.whut.training.config;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import java.net.URI;

/** Test-classpath only: fail before datasource creation if the regression target is unsafe. */
@Configuration(proxyBeanMethods = false)
@Profile("mysql-test")
public class MySqlTestSafetyConfiguration {
    @Bean
    static BeanFactoryPostProcessor mysqlRegressionTargetGuard(Environment environment) {
        return beanFactory -> requireIsolatedTarget(environment.getProperty("spring.datasource.url"),
                environment.getProperty("spring.datasource.username"), environment.getProperty("spring.datasource.password"));
    }

    static void requireIsolatedTarget(String url, String username, String password) {
        try {
            if (url == null || !url.startsWith("jdbc:mysql://")) throw new IllegalArgumentException();
            URI target = URI.create(url.substring("jdbc:".length()));
            int port = target.getPort();
            if (!"127.0.0.1".equals(target.getHost()) || target.getUserInfo() != null
                    || port < 1 || port > 65535 || port == 3306 || port == 33060
                    || target.getPath() == null || !target.getPath().matches("/whut_acm_test_[a-f0-9]{12}")
                    || !"whut_test".equals(username) || password == null || password.isBlank()) {
                throw new IllegalArgumentException();
            }
        } catch (RuntimeException invalid) {
            throw new IllegalStateException("mysql-test requires a temporary loopback instance, non-default port, "
                    + "random whut_acm_test_<12 hex> schema and non-root whut_test credentials");
        }
    }
}
