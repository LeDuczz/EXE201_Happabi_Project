package com.minduc.happabi.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import static org.assertj.core.api.Assertions.assertThat;

class CorsConfigTest {

    @Test
    void corsConfigurationAllowsFrontendCredentialsAndExposesCsrfHeader() {
        CorsConfig config = new CorsConfig();
        ReflectionTestUtils.setField(config, "corsUrl", "https://happabi.com");
        UrlBasedCorsConfigurationSource source =
                (UrlBasedCorsConfigurationSource) config.corsConfigurationSource();

        CorsConfiguration cors = source.getCorsConfiguration(new MockHttpServletRequest("POST", "/api/v1/auth/login"));

        assertThat(cors).isNotNull();
        assertThat(cors.getAllowedOrigins()).containsExactly("https://happabi.com");
        assertThat(cors.getAllowedMethods()).containsExactly("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS");
        assertThat(cors.getAllowedHeaders()).containsExactly("*");
        assertThat(cors.getExposedHeaders()).containsExactly("X-HAPPABI-CSRF");
        assertThat(cors.getAllowCredentials()).isTrue();
        assertThat(cors.getMaxAge()).isEqualTo(3600L);
    }
}
