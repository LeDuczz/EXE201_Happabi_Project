package com.minduc.happabi.service.user;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minduc.happabi.dto.response.user.MotherProfileResponse;
import com.minduc.happabi.dto.response.user.NurseProfileResponse;
import com.minduc.happabi.dto.response.user.UserProfileResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
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

    public Optional<UserProfileResponse> getUserProfile(String cognitoSub) {
        return get(cacheKey("me", cognitoSub), UserProfileResponse.class);
    }

    public void putUserProfile(String cognitoSub, UserProfileResponse response) {
        put(cacheKey("me", cognitoSub), response);
    }

    public Optional<MotherProfileResponse> getMotherProfile(String cognitoSub) {
        return get(cacheKey("mother", cognitoSub), MotherProfileResponse.class);
    }

    public void putMotherProfile(String cognitoSub, MotherProfileResponse response) {
        put(cacheKey("mother", cognitoSub), response);
    }

    public Optional<NurseProfileResponse> getNurseProfile(String cognitoSub) {
        return get(cacheKey("nurse", cognitoSub), NurseProfileResponse.class);
    }

    public void putNurseProfile(String cognitoSub, NurseProfileResponse response) {
        put(cacheKey("nurse", cognitoSub), response);
    }

    public void evictProfiles(String cognitoSub) {
        List<String> keys = List.of(
                cacheKey("me", cognitoSub),
                cacheKey("mother", cognitoSub),
                cacheKey("nurse", cognitoSub)
        );

        try {
            stringRedisTemplate.delete(keys);
            log.info("[UserCache] Evicted profile caches for sub={}", cognitoSub);
        } catch (RuntimeException e) {
            log.warn("[UserCache] Failed to evict profile caches for sub={}", cognitoSub, e);
        }
    }

    private <T> Optional<T> get(String key, Class<T> type) {
        String cached;
        try {
            cached = stringRedisTemplate.opsForValue().get(key);
        } catch (RuntimeException e) {
            log.warn("[UserCache] Redis read failed for key={}", key, e);
            return Optional.empty();
        }

        if (cached == null) {
            log.debug("[UserCache] MISS key={}", key);
            return Optional.empty();
        }

        try {
            log.debug("[UserCache] HIT key={}", key);
            return Optional.of(objectMapper.readValue(cached, type));
        } catch (JsonProcessingException e) {
            log.warn("[UserCache] Failed to parse cached value for key={}", key, e);
            safeDelete(key);
            return Optional.empty();
        }
    }

    private void put(String key, Object value) {
        String serialized;
        try {
            serialized = objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("[UserCache] Failed to serialize cache value for key={}", key, e);
            return;
        }

        try {
            stringRedisTemplate.opsForValue().set(key, serialized, CACHE_TTL);
            log.debug("[UserCache] Cached key={} ttl={}s", key, CACHE_TTL.toSeconds());
        } catch (RuntimeException e) {
            log.warn("[UserCache] Redis write failed for key={}", key, e);
        }
    }

    private void safeDelete(String key) {
        try {
            stringRedisTemplate.delete(key);
        } catch (RuntimeException e) {
            log.warn("[UserCache] Failed to delete corrupt cache key={}", key, e);
        }
    }

    private String cacheKey(String profileType, String cognitoSub) {
        return CACHE_KEY_PREFIX + profileType + ":" + cognitoSub;
    }
}
