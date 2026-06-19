package com.minduc.happabi.service.nurse;

import com.minduc.happabi.enums.NurseStatus;
import com.minduc.happabi.observability.metrics.MetricsRecorder;
import com.minduc.happabi.repository.NurseProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NurseAccessCacheService {

    private static final String CACHE_KEY_PREFIX = "nurse:access:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final StringRedisTemplate stringRedisTemplate;
    private final NurseProfileRepository nurseProfileRepository;
    private final MetricsRecorder metricsRecorder;

    public Optional<NurseStatus> getStatus(UUID userId) {
        String key = cacheKey(userId);
        Optional<NurseStatus> cached = read(key);
        if (cached.isPresent()) {
            return cached;
        }

        Optional<NurseStatus> status = nurseProfileRepository.findNurseStatusByUserId(userId);
        status.ifPresent(value -> write(key, value));
        return status;
    }

    @Async("appTaskExecutor")
    public void evict(UUID userId) {
        if (userId == null) {
            return;
        }
        String key = cacheKey(userId);
        try {
            stringRedisTemplate.delete(key);
            record("delete", "success", "none");
            log.debug("[NurseAccessCache] Evicted key={}", key);
        } catch (RuntimeException e) {
            record("delete", "failure", "redis_error");
            log.warn("[NurseAccessCache] Redis delete failed for key={}", key, e);
        }
    }

    private Optional<NurseStatus> read(String key) {
        String cached;
        try {
            cached = stringRedisTemplate.opsForValue().get(key);
        } catch (RuntimeException e) {
            record("read", "fallback_db", "redis_error");
            log.warn("[NurseAccessCache] Redis read failed for key={}", key, e);
            return Optional.empty();
        }

        if (cached == null) {
            record("read", "fallback_db", "miss");
            log.debug("[NurseAccessCache] MISS key={}", key);
            return Optional.empty();
        }

        try {
            NurseStatus status = NurseStatus.valueOf(cached);
            record("read", "hit", "none");
            log.debug("[NurseAccessCache] HIT key={} status={}", key, status);
            return Optional.of(status);
        } catch (IllegalArgumentException e) {
            record("read", "fallback_db", "corrupt_value");
            log.warn("[NurseAccessCache] Invalid cached status for key={} value={}", key, cached);
            evictKey(key);
            return Optional.empty();
        }
    }

    private void write(String key, NurseStatus status) {
        try {
            stringRedisTemplate.opsForValue().set(key, status.name(), CACHE_TTL);
            record("write", "success", "none");
            log.debug("[NurseAccessCache] Cached key={} status={} ttl={}s",
                    key, status, CACHE_TTL.toSeconds());
        } catch (RuntimeException e) {
            record("write", "failure", "redis_error");
            log.warn("[NurseAccessCache] Redis write failed for key={}", key, e);
        }
    }

    private void evictKey(String key) {
        try {
            stringRedisTemplate.delete(key);
            record("delete", "success", "none");
        } catch (RuntimeException e) {
            record("delete", "failure", "redis_error");
            log.warn("[NurseAccessCache] Redis delete failed for key={}", key, e);
        }
    }

    private String cacheKey(UUID userId) {
        return CACHE_KEY_PREFIX + userId;
    }

    private void record(String operation, String result, String reason) {
        metricsRecorder.increment("happabi.cache.operations", Map.of(
                "cache", "nurse_access",
                "profile", "status",
                "operation", operation,
                "result", result,
                "reason", reason
        ));
    }
}
