package com.minduc.happabi.service.user;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minduc.happabi.dto.UserDTO;
import com.minduc.happabi.observability.metrics.MetricsRecorder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserListCacheService {

    private static final String CACHE_KEY_PREFIX = "admin:users:list:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(2);

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final MetricsRecorder metricsRecorder;

    public Optional<Page<UserDTO>> get(String query, Pageable pageable) {
        String cacheKey = cacheKey(query, pageable);
        String cached;
        try {
            cached = stringRedisTemplate.opsForValue().get(cacheKey);
        } catch (RuntimeException e) {
            record("read", "fallback_db", "redis_error");
            log.warn("[AdminUserListCache] Redis read failed for key={}", cacheKey, e);
            return Optional.empty();
        }

        if (cached == null || cached.isBlank()) {
            record("read", "fallback_db", "miss");
            return Optional.empty();
        }

        try {
            AdminUserPageCacheEntry entry = objectMapper.readValue(cached, AdminUserPageCacheEntry.class);
            record("read", "hit", "none");
            return Optional.of(entry.toPage());
        } catch (JsonProcessingException e) {
            record("read", "fallback_db", "corrupt_value");
            log.warn("[AdminUserListCache] Failed to parse cached value for key={}", cacheKey, e);
            safeDelete(cacheKey);
            return Optional.empty();
        }
    }

    public void put(String query, Pageable pageable, Page<UserDTO> users) {
        String cacheKey = cacheKey(query, pageable);
        String serialized;
        try {
            serialized = objectMapper.writeValueAsString(AdminUserPageCacheEntry.from(users));
        } catch (JsonProcessingException e) {
            record("write", "failure", "serialization_error");
            log.warn("[AdminUserListCache] Failed to serialize cache value for key={}", cacheKey, e);
            return;
        }

        try {
            stringRedisTemplate.opsForValue().set(cacheKey, serialized, CACHE_TTL);
            record("write", "success", "none");
        } catch (RuntimeException e) {
            record("write", "failure", "redis_error");
            log.warn("[AdminUserListCache] Redis write failed for key={}", cacheKey, e);
        }
    }

    @Async("appTaskExecutor")
    public void evictAll() {
        ScanOptions scanOptions = ScanOptions.scanOptions()
                .match(CACHE_KEY_PREFIX + "*")
                .count(100)
                .build();

        List<String> keys = new ArrayList<>();
        try (Cursor<String> cursor = stringRedisTemplate.scan(scanOptions)) {
            while (cursor.hasNext()) {
                keys.add(cursor.next());
            }
        } catch (RuntimeException e) {
            record("scan", "failure", "redis_error");
            log.warn("[AdminUserListCache] Redis scan failed while evicting admin user list cache", e);
            return;
        }

        if (keys.isEmpty()) {
            record("delete", "success", "empty");
            return;
        }

        try {
            stringRedisTemplate.delete(keys);
            record("delete", "success", "none");
            log.info("[AdminUserListCache] Evicted {} admin user list cache keys", keys.size());
        } catch (RuntimeException e) {
            record("delete", "failure", "redis_error");
            log.warn("[AdminUserListCache] Redis bulk delete failed", e);
        }
    }

    private void safeDelete(String cacheKey) {
        try {
            stringRedisTemplate.delete(cacheKey);
            record("delete", "success", "corrupt_value");
        } catch (RuntimeException e) {
            record("delete", "failure", "redis_error");
            log.warn("[AdminUserListCache] Failed to delete cache key={}", cacheKey, e);
        }
    }

    private String cacheKey(String query, Pageable pageable) {
        String normalizedQuery = query == null || query.isBlank()
                ? "all"
                : query.trim().toLowerCase().replaceAll("\\s+", " ");
        return CACHE_KEY_PREFIX
                + "q=" + normalizedQuery
                + ":page=" + pageable.getPageNumber()
                + ":size=" + pageable.getPageSize()
                + ":sort=" + sortKey(pageable.getSort());
    }

    private String sortKey(Sort sort) {
        if (sort == null || sort.isUnsorted()) {
            return "unsorted";
        }
        List<String> orders = new ArrayList<>();
        sort.forEach(order -> orders.add(order.getProperty() + "," + order.getDirection().name()));
        return String.join("|", orders);
    }

    private void record(String operation, String result, String reason) {
        metricsRecorder.increment("happabi.cache.operations", Map.of(
                "cache", "admin_user_list",
                "profile", "none",
                "operation", operation,
                "result", result,
                "reason", reason
        ));
    }

    private record AdminUserPageCacheEntry(
            List<UserSnapshot> content,
            int pageNumber,
            int pageSize,
            long totalElements,
            List<SortOrderSnapshot> sort
    ) {
        static AdminUserPageCacheEntry from(Page<UserDTO> page) {
            return new AdminUserPageCacheEntry(
                    page.getContent().stream().map(UserSnapshot::from).toList(),
                    page.getNumber(),
                    page.getSize(),
                    page.getTotalElements(),
                    page.getSort().stream().map(SortOrderSnapshot::from).toList()
            );
        }

        Page<UserDTO> toPage() {
            Sort restoredSort = sort == null || sort.isEmpty()
                    ? Sort.unsorted()
                    : Sort.by(sort.stream().map(SortOrderSnapshot::toOrder).toList());
            Pageable pageable = PageRequest.of(pageNumber, pageSize, restoredSort);
            List<UserDTO> users = content == null
                    ? List.of()
                    : content.stream().map(UserSnapshot::toUserDTO).toList();
            return new PageImpl<>(users, pageable, totalElements);
        }
    }

    private record UserSnapshot(
            UUID id,
            String fullName,
            String phone,
            String email,
            Boolean isActive,
            List<String> roles,
            OffsetDateTime createdAt
    ) {
        static UserSnapshot from(UserDTO user) {
            return new UserSnapshot(
                    user.getId(),
                    user.getFullName(),
                    user.getPhone(),
                    user.getEmail(),
                    user.getIsActive(),
                    user.getRoles(),
                    user.getCreatedAt()
            );
        }

        UserDTO toUserDTO() {
            return UserDTO.builder()
                    .id(id)
                    .fullName(fullName)
                    .phone(phone)
                    .email(email)
                    .isActive(isActive)
                    .roles(roles)
                    .createdAt(createdAt)
                    .build();
        }
    }

    private record SortOrderSnapshot(String property, Sort.Direction direction) {
        static SortOrderSnapshot from(Sort.Order order) {
            return new SortOrderSnapshot(order.getProperty(), order.getDirection());
        }

        Sort.Order toOrder() {
            return new Sort.Order(direction, property);
        }
    }
}
