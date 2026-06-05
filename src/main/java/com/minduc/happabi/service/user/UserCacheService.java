package com.minduc.happabi.service.user;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minduc.happabi.dto.response.mother.MotherProfileResponse;
import com.minduc.happabi.dto.response.nurse.NurseProfileResponse;
import com.minduc.happabi.dto.response.user.UserProfileResponse;
import com.minduc.happabi.observability.metrics.MetricsRecorder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserCacheService {

    private static final String CACHE_KEY_PREFIX = "user:profile:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(60);

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final MetricsRecorder metricsRecorder;

    public Optional<UserProfileResponse> getUserProfile(String cognitoSub) {
        return get("me", cacheKey("me", cognitoSub), UserProfileResponse.class);
    }

    public void putUserProfile(String cognitoSub, UserProfileResponse response) {
        put("me", cacheKey("me", cognitoSub), response);
    }

    public Optional<MotherProfileResponse> getMotherProfile(String cognitoSub) {
        return get("mother", cacheKey("mother", cognitoSub),
                MotherProfileResponse.class);
    }

    public void putMotherProfile(String cognitoSub, MotherProfileResponse response) {
        put("mother", cacheKey("mother", cognitoSub), response);
    }

    public Optional<NurseProfileResponse> getNurseProfile(String cognitoSub) {
        return get("nurse", cacheKey("nurse", cognitoSub), NurseProfileResponse.class);
    }

    public void putNurseProfile(String cognitoSub, NurseProfileResponse response) {
        put("nurse", cacheKey("nurse", cognitoSub), response);
    }

    public void evictProfiles(String cognitoSub) {
        List<String> keys = List.of(
                cacheKey("me", cognitoSub),
                cacheKey("mother", cognitoSub),
                cacheKey("nurse", cognitoSub)
        );

        try {
            stringRedisTemplate.delete(keys);
            record("all", "delete", "success", "none");
            log.info("[UserCache] Evicted profile caches for sub={}", cognitoSub);
        } catch (RuntimeException e) {
            record("all", "delete", "failure", "redis_error");
            log.warn("[UserCache] Failed to evict profile caches for sub={}", cognitoSub, e);
        }
    }

    private <T> Optional<T> get(String profileType, String key, Class<T> type) {
        String cached;
        try {
            cached = stringRedisTemplate.opsForValue().get(key);
        } catch (RuntimeException e) {
            record(profileType, "read", "fallback_db", "redis_error");
            log.warn("[UserCache] Redis read failed for key={}", key, e);
            return Optional.empty();
        }

        if (cached == null) {
            record(profileType, "read", "fallback_db", "miss");
            log.debug("[UserCache] MISS key={}", key);
            return Optional.empty();
        }

        try {
            record(profileType, "read", "hit", "none");
            log.debug("[UserCache] HIT key={}", key);
            return Optional.of(objectMapper.readValue(cached, type));
        } catch (JsonProcessingException e) {
            record(profileType, "read", "fallback_db", "corrupt_value");
            log.warn("[UserCache] Failed to parse cached value for key={}", key, e);
            safeDelete(profileType, key);
            return Optional.empty();
        }
    }

    private void put(String profileType, String key, Object value) {
        String serialized;
        try {
            serialized = objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            record(profileType, "write", "failure", "serialization_error");
            log.warn("[UserCache] Failed to serialize cache value for key={}", key, e);
            return;
        }

        try {
            stringRedisTemplate.opsForValue().set(key, serialized, CACHE_TTL);
            record(profileType, "write", "success", "none");
            log.debug("[UserCache] Cached key={} ttl={}s", key, CACHE_TTL.toSeconds());
        } catch (RuntimeException e) {
            record(profileType, "write", "failure", "redis_error");
            log.warn("[UserCache] Redis write failed for key={}", key, e);
        }
    }

    private void safeDelete(String profileType, String key) {
        try {
            stringRedisTemplate.delete(key);
            record(profileType, "delete", "success", "none");
        } catch (RuntimeException e) {
            record(profileType, "delete", "failure", "redis_error");
            log.warn("[UserCache] Failed to delete corrupt cache key={}", key, e);
        }
    }

    private void record(String profileType, String operation, String result, String reason) {
        metricsRecorder.increment("happabi.cache.operations", Map.of(
                "cache", "user_profile",
                "profile", profileType,
                "operation", operation,
                "result", result,
                "reason", reason
        ));
    }

    private String cacheKey(String profileType, String cognitoSub) {
        return CACHE_KEY_PREFIX + profileType + ":" + cognitoSub;
    }
}
