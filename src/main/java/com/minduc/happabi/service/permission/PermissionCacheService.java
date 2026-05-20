package com.minduc.happabi.service.permission;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minduc.happabi.enums.UserRole;
import com.minduc.happabi.observability.metrics.MetricsRecorder;
import com.minduc.happabi.repository.RolePermissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionCacheService {

    private static final String CACHE_KEY_PREFIX = "role:permissions:";
    private static final Duration CACHE_TTL = Duration.ofHours(1);

    private final RolePermissionRepository rolePermissionRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final MetricsRecorder metricsRecorder;

    public List<String> getPermissions(String roleName) {
        String redisKey = CACHE_KEY_PREFIX + roleName;

        RedisRead redisRead = readFromRedis(redisKey);
        String cached = redisRead.value();
        if (cached != null && !cached.isBlank()) {
            try {
                List<String> permissions = objectMapper.readValue(cached, new TypeReference<List<String>>() {});
                record("read", "hit", "none");
                log.debug("[PermissionCache] Redis HIT for role: {}", roleName);
                return permissions;
            } catch (JsonProcessingException e) {
                record("read", "fallback_db", "corrupt_value");
                log.warn("[PermissionCache] Failed to parse cached permissions for role: {}", roleName, e);
                safeDelete(redisKey);
            }
        }

        record("read", "fallback_db", redisRead.reason());
        log.debug("[PermissionCache] Redis MISS for role: {}. Querying DB...", roleName);

        UserRole role;
        try {
            role = UserRole.valueOf(roleName.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("[PermissionCache] Unknown role: {}", roleName);
            return List.of();
        }

        List<String> permissions = rolePermissionRepository.findByRoleName(role)
                .stream()
                .map(rp -> rp.getPermission().getPermissionName())
                .toList();

        writeToRedis(redisKey, permissions, roleName);
        return permissions;
    }

    public void evictRole(String roleName) {
        String redisKey = CACHE_KEY_PREFIX + roleName;
        if (safeDelete(redisKey)) {
            log.info("[PermissionCache] Evicted cache for role: {}", roleName);
        }
    }

    public void evictAll() {
        ScanOptions scanOptions = ScanOptions.scanOptions()
                .match(CACHE_KEY_PREFIX + "*")
                .count(100)
                .build();

        List<String> keysToDelete = new ArrayList<>();
        try (Cursor<String> cursor = stringRedisTemplate.scan(scanOptions)) {
            cursor.forEachRemaining(keysToDelete::add);
        } catch (RuntimeException e) {
            record("scan", "failure", "redis_error");
            log.warn("[PermissionCache] Redis scan failed while evicting all role permission caches", e);
            return;
        }

        if (keysToDelete.isEmpty()) {
            return;
        }

        try {
            stringRedisTemplate.delete(keysToDelete);
            record("delete", "success", "none");
            log.info("[PermissionCache] Evicted all role permission caches ({} keys)", keysToDelete.size());
        } catch (RuntimeException e) {
            record("delete", "failure", "redis_error");
            log.warn("[PermissionCache] Redis bulk delete failed while evicting all role permission caches", e);
        }
    }

    private RedisRead readFromRedis(String redisKey) {
        try {
            String value = stringRedisTemplate.opsForValue().get(redisKey);
            return new RedisRead(value, value == null ? "miss" : "none");
        } catch (RuntimeException e) {
            log.warn("[PermissionCache] Redis read failed for key={}", redisKey, e);
            return new RedisRead(null, "redis_error");
        }
    }

    private void writeToRedis(String redisKey, List<String> permissions, String roleName) {
        String json;
        try {
            json = objectMapper.writeValueAsString(permissions);
        } catch (JsonProcessingException e) {
            log.error("[PermissionCache] Failed to serialize permissions for role: {}", roleName, e);
            return;
        }

        try {
            stringRedisTemplate.opsForValue().set(redisKey, json, CACHE_TTL);
            record("write", "success", "none");
            log.info("[PermissionCache] Cached {} permissions for role: {}", permissions.size(), roleName);
        } catch (RuntimeException e) {
            record("write", "failure", "redis_error");
            log.warn("[PermissionCache] Redis write failed for key={}", redisKey, e);
        }
    }

    private boolean safeDelete(String redisKey) {
        try {
            stringRedisTemplate.delete(redisKey);
            record("delete", "success", "none");
            return true;
        } catch (RuntimeException e) {
            record("delete", "failure", "redis_error");
            log.warn("[PermissionCache] Redis delete failed for key={}", redisKey, e);
            return false;
        }
    }

    private void record(String operation, String result, String reason) {
        metricsRecorder.increment("happabi.cache.operations", Map.of(
                "cache", "role_permissions",
                "profile", "none",
                "operation", operation,
                "result", result,
                "reason", reason
        ));
    }

    private record RedisRead(String value, String reason) {}
}
