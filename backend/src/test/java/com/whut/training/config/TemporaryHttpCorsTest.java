package com.whut.training.config;

import com.whut.training.interceptor.AccessTokenInterceptor;
import com.whut.training.interceptor.RequestLoggingInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.filter.ForwardedHeaderFilter;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TemporaryHttpCorsTest {
    private MockMvc mvc;
    private AnnotationConfigWebApplicationContext context;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(TestMvcConfiguration.class);
        context.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(new ForwardedHeaderFilter())
                .build();
    }

    @AfterEach
    void closeContext() { context.close(); }

    @Test
    void httpIpLoginUsesTheOriginalNginxHostAndScheme() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .header("Host", "127.0.0.1:8080")
                        .header("X-Forwarded-Host", "203.0.113.10")
                        .header("X-Forwarded-Proto", "http")
                        .header("Origin", "http://203.0.113.10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.host").value("203.0.113.10"))
                .andExpect(jsonPath("$.scheme").value("http"))
                .andExpect(jsonPath("$.port").value(80));
    }

    @Test
    void permitsConfiguredIpPreflightWithTokenHeaders() throws Exception {
        mvc.perform(options("/api/auth/login")
                        .header("Origin", "http://203.0.113.10")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Authorization,Content-Type,X-Refresh-Token"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://203.0.113.10"));
    }

    @Test
    void rejectsAnUnconfiguredOrigin() throws Exception {
        mvc.perform(post("/api/auth/login").header("Origin", "http://203.0.113.11"))
                .andExpect(status().isForbidden());
    }

    @Configuration
    @EnableWebMvc
    static class TestMvcConfiguration implements WebMvcConfigurer {
        @Bean
        LoginProbe loginProbe() { return new LoginProbe(); }

        @Override
        public void addCorsMappings(CorsRegistry registry) {
            new WebConfig(mock(AccessTokenInterceptor.class), mock(RequestLoggingInterceptor.class),
                    "http://203.0.113.10").addCorsMappings(registry);
        }
    }

    @RestController
    static class LoginProbe {
        @PostMapping("/api/auth/login")
        Map<String, Object> login(HttpServletRequest request) {
            return Map.of("host", request.getServerName(), "scheme", request.getScheme(), "port", request.getServerPort());
        }
    }
}
