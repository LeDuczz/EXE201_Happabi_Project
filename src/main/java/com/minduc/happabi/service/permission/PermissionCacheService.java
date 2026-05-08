package com.minduc.happabi.service.permission;

import com.minduc.happabi.enums.UserRole;
import com.minduc.happabi.repository.RolePermissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
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
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * Lấy danh sách tên Permission của một Role.
     * Ưu tiên lấy từ Redis, nếu miss thì xuống DB rồi cache lại.
     *
     * @param roleName tên Role dạng enum string (VD: "MOTHER")
     * @return danh sách permissionName (VD: ["USER:READ", "NURSE:READ"])
     */
    @SuppressWarnings("unchecked")
    public List<String> getPermissions(String roleName) {
        String redisKey = CACHE_KEY_PREFIX + roleName;

        // ── 1. Check Redis cache ───────────────────────────────────────────────
        Object cached = redisTemplate.opsForValue().get(redisKey);
        if (cached instanceof List<?> cachedList) {
            log.debug("[PermissionCache] Redis HIT for role: {}", roleName);
            return (List<String>) cachedList;
        }

        // ── 2. Cache miss → truy vấn DB ───────────────────────────────────────
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

        // ── 3. Lưu vào Redis với TTL 1 giờ ───────────────────────────────────
        redisTemplate.opsForValue().set(redisKey, permissions, CACHE_TTL);
        log.info("[PermissionCache] Cached {} permissions for role: {}", permissions.size(), roleName);

        return permissions;
    }

    /**
     * Xóa cache của một Role cụ thể.
     * Gọi khi admin thay đổi quyền của Role đó.
     *
     * @param roleName tên Role cần xóa cache
     */
    public void evictRole(String roleName) {
        String redisKey = CACHE_KEY_PREFIX + roleName;
        redisTemplate.delete(redisKey);
        log.info("[PermissionCache] Evicted cache for role: {}", roleName);
    }

    /**
     * Xóa toàn bộ cache permissions của tất cả roles.
     * Dùng SCAN thay vì KEYS để tránh block Redis trong production.
     */
    public void evictAll() {
        ScanOptions scanOptions = ScanOptions.scanOptions()
                .match(CACHE_KEY_PREFIX + "*")
                .count(100)
                .build();

        List<String> keysToDelete = new ArrayList<>();
        try (Cursor<String> cursor = redisTemplate.scan(scanOptions)) {
            cursor.forEachRemaining(keysToDelete::add);
        }

        if (!keysToDelete.isEmpty()) {
            redisTemplate.delete(keysToDelete);
            log.info("[PermissionCache] Evicted all role permission caches ({} keys)", keysToDelete.size());
        }
    }
}
