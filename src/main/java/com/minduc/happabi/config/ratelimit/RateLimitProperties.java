package com.minduc.happabi.config.ratelimit;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

@Data
@ConfigurationProperties(prefix = "rate-limit")
public class RateLimitProperties {

    private BucketConfig global = new BucketConfig();

    /**
     * Phải là mutable Map để Spring Boot binding hoạt động.
     * Không dùng Map.of() — immutable sẽ throw UnsupportedOperationException khi bind.
     */
    private Map<String, BucketConfig> endpoints = new HashMap<>();

    @Data
    public static class BucketConfig {
        /** Số token tối đa trong bucket. */
        private int capacity = 10;
        /** Số token được nạp lại mỗi interval. */
        private int refillTokens = 1;
        /** Khoảng thời gian (giây) giữa mỗi lần nạp. */
        private int refillSeconds = 60;
    }

    /**
     * Lấy config của một endpoint theo tên (key trong YAML).
     * Trả về global config nếu không tìm thấy endpoint cụ thể.
     *
     * @param endpointKey tên key trong YAML, ví dụ "login", "register"
     */
    public BucketConfig getEndpoint(String endpointKey) {
        return endpoints.getOrDefault(endpointKey, global);
    }
}
