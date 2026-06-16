package com.minduc.happabi.observability.logging;

import com.minduc.happabi.dto.request.auth.LoginRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveDataSanitizerTest {

    @Test
    void sanitizeNamedValueMasksSensitiveNames() {
        assertThat(SensitiveDataSanitizer.sanitizeNamedValue("accessToken", "secret-token")).isEqualTo("****");
        assertThat(SensitiveDataSanitizer.sanitizeNamedValue("phone", "0900000000")).isEqualTo("****");
    }

    @Test
    void sanitizeMasksSensitiveMapEntriesButKeepsNonSensitiveValues() {
        Object sanitized = SensitiveDataSanitizer.sanitize(Map.of(
                "password", "raw",
                "role", "NURSE",
                "nested", Map.of("email", "a@example.com", "count", 2)));

        assertThat(sanitized).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) sanitized;
        assertThat(map).containsEntry("password", "****");
        assertThat(map).containsEntry("role", "NURSE");
        @SuppressWarnings("unchecked")
        Map<String, Object> nested = (Map<String, Object>) map.get("nested");
        assertThat(nested).containsEntry("email", "****");
    }

    @Test
    void sanitizeReflectsSafeDtoAndMasksSensitiveProperties() {
        LoginRequest request = new LoginRequest();
        request.setPhone("0900000000");
        request.setPassword("secret");

        Object sanitized = SensitiveDataSanitizer.sanitize(request);

        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) sanitized;
        assertThat(map).containsEntry("type", "LoginRequest");
        assertThat(map).containsEntry("phone", "****");
        assertThat(map).containsEntry("password", "****");
    }

    @Test
    void sanitizeLimitsCollectionsToTwentyItems() {
        List<Integer> values = java.util.stream.IntStream.range(0, 25).boxed().toList();

        Object sanitized = SensitiveDataSanitizer.sanitize(values);

        assertThat((List<?>) sanitized).hasSize(20);
    }
}
