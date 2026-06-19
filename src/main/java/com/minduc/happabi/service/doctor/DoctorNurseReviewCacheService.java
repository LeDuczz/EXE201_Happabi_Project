package com.minduc.happabi.service.doctor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minduc.happabi.dto.response.nurse.NurseOnboardingResponse;
import com.minduc.happabi.dto.response.nurse.NurseReviewSummaryResponse;
import com.minduc.happabi.observability.metrics.MetricsRecorder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DoctorNurseReviewCacheService {

    private static final String PENDING_REVIEWS_KEY = "doctor:nurse-review:pending";
    private static final String DETAIL_KEY_PREFIX = "doctor:nurse-review:detail:";
    private static final Duration PENDING_TTL = Duration.ofSeconds(45);
    private static final Duration DETAIL_TTL = Duration.ofMinutes(5);

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final MetricsRecorder metricsRecorder;

    public Optional<List<NurseReviewSummaryResponse>> getPendingReviews() {
        return getList(PENDING_REVIEWS_KEY, NurseReviewSummaryResponse.class, "pending");
    }

    public void putPendingReviews(List<NurseReviewSummaryResponse> value) {
        put(PENDING_REVIEWS_KEY, value, PENDING_TTL, "pending");
    }

    public Optional<NurseOnboardingResponse> getDetail(UUID profileId) {
        return get(detailKey(profileId), NurseOnboardingResponse.class, "detail");
    }

    public void putDetail(UUID profileId, NurseOnboardingResponse value) {
        put(detailKey(profileId), value, DETAIL_TTL, "detail");
    }

    @Async("appTaskExecutor")
    public void evictPendingReviews() {
        delete(PENDING_REVIEWS_KEY, "pending");
    }

    @Async("appTaskExecutor")
    public void evictDetail(UUID profileId) {
        if (profileId != null) {
            delete(detailKey(profileId), "detail");
        }
    }

    @Async("appTaskExecutor")
    public void evictReviewCaches(UUID profileId) {
        delete(PENDING_REVIEWS_KEY, "pending");
        if (profileId != null) {
            delete(detailKey(profileId), "detail");
        }
    }

    private <T> Optional<T> get(String key, Class<T> type, String cacheType) {
        String cached = read(key, cacheType);
        if (cached == null) return Optional.empty();

        try {
            record(cacheType, "read", "hit", "none");
            log.debug("[DoctorNurseReviewCache] HIT key={}", key);
            return Optional.of(objectMapper.readValue(cached, type));
        } catch (JsonProcessingException e) {
            record(cacheType, "read", "fallback_db", "corrupt_value");
            log.warn("[DoctorNurseReviewCache] Failed to parse cached value for key={}", key, e);
            delete(key, cacheType);
            return Optional.empty();
        }
    }

    private <T> Optional<List<T>> getList(String key, Class<T> elementType, String cacheType) {
        String cached = read(key, cacheType);
        if (cached == null) return Optional.empty();

        try {
            record(cacheType, "read", "hit", "none");
            log.debug("[DoctorNurseReviewCache] HIT key={}", key);
            return Optional.of(objectMapper.readValue(cached,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, elementType)));
        } catch (JsonProcessingException e) {
            record(cacheType, "read", "fallback_db", "corrupt_value");
            log.warn("[DoctorNurseReviewCache] Failed to parse cached list for key={}", key, e);
            delete(key, cacheType);
            return Optional.empty();
        }
    }

    private String read(String key, String cacheType) {
        try {
            String cached = stringRedisTemplate.opsForValue().get(key);
            if (cached == null) {
                record(cacheType, "read", "fallback_db", "miss");
                log.debug("[DoctorNurseReviewCache] MISS key={}", key);
            }
            return cached;
        } catch (RuntimeException e) {
            record(cacheType, "read", "fallback_db", "redis_error");
            log.warn("[DoctorNurseReviewCache] Redis read failed for key={}", key, e);
            return null;
        }
    }

    private void put(String key, Object value, Duration ttl, String cacheType) {
        String serialized;
        try {
            serialized = objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            record(cacheType, "write", "failure", "serialization_error");
            log.warn("[DoctorNurseReviewCache] Failed to serialize cache value for key={}", key, e);
            return;
        }

        try {
            stringRedisTemplate.opsForValue().set(key, serialized, ttl);
            record(cacheType, "write", "success", "none");
            log.debug("[DoctorNurseReviewCache] Cached key={} ttl={}s", key, ttl.toSeconds());
        } catch (RuntimeException e) {
            record(cacheType, "write", "failure", "redis_error");
            log.warn("[DoctorNurseReviewCache] Redis write failed for key={}", key, e);
        }
    }

    private void delete(String key, String cacheType) {
        try {
            stringRedisTemplate.delete(key);
            record(cacheType, "delete", "success", "none");
        } catch (RuntimeException e) {
            record(cacheType, "delete", "failure", "redis_error");
            log.warn("[DoctorNurseReviewCache] Redis delete failed for key={}", key, e);
        }
    }

    private String detailKey(UUID profileId) {
        return DETAIL_KEY_PREFIX + profileId;
    }

    private void record(String cacheType, String operation, String result, String reason) {
        metricsRecorder.increment("happabi.cache.operations", Map.of(
                "cache", "doctor_nurse_review",
                "profile", cacheType,
                "operation", operation,
                "result", result,
                "reason", reason
        ));
    }
}
