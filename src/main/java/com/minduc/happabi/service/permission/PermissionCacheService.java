package com.minduc.happabi.service.permission;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minduc.happabi.enums.UserRole;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionCacheService {

    private static final String CACHE_KEY_PREFIX = "role:permissions:";
    private static final Duration CACHE_TTL = Duration.ofHours(1);

    private final RolePermissionRepository rolePermissionRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public List<String> getPermissions(String roleName) {
        String redisKey = CACHE_KEY_PREFIX + roleName;

        String cached = readFromRedis(redisKey);
        if (cached != null && !cached.isBlank()) {
            try {
                List<String> permissions = objectMapper.readValue(cached, new TypeReference<List<String>>() {});
                log.debug("[PermissionCache] Redis HIT for role: {}", roleName);
                return permissions;
            } catch (JsonProcessingException e) {
                log.warn("[PermissionCache] Failed to parse cached permissions for role: {}", roleName, e);
                safeDelete(redisKey);
            }
        }

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
            log.warn("[PermissionCache] Redis scan failed while evicting all role permission caches", e);
            return;
        }

        if (keysToDelete.isEmpty()) {
            return;
        }

        try {
            stringRedisTemplate.delete(keysToDelete);
            log.info("[PermissionCache] Evicted all role permission caches ({} keys)", keysToDelete.size());
        } catch (RuntimeException e) {
            log.warn("[PermissionCache] Redis bulk delete failed while evicting all role permission caches", e);
        }
    }

    private String readFromRedis(String redisKey) {
        try {
            return stringRedisTemplate.opsForValue().get(redisKey);
        } catch (RuntimeException e) {
            log.warn("[PermissionCache] Redis read failed for key={}", redisKey, e);
            return null;
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
            log.info("[PermissionCache] Cached {} permissions for role: {}", permissions.size(), roleName);
        } catch (RuntimeException e) {
            log.warn("[PermissionCache] Redis write failed for key={}", redisKey, e);
        }
    }

    private boolean safeDelete(String redisKey) {
        try {
            stringRedisTemplate.delete(redisKey);
            return true;
        } catch (RuntimeException e) {
            log.warn("[PermissionCache] Redis delete failed for key={}", redisKey, e);
            return false;
        }
    }
}
