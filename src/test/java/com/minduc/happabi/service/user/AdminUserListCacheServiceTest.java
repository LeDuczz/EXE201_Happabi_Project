package com.minduc.happabi.service.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minduc.happabi.dto.UserDTO;
import com.minduc.happabi.enums.UserRole;
import com.minduc.happabi.observability.metrics.MetricsRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserListCacheServiceTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private MetricsRecorder metricsRecorder;

    private AdminUserListCacheService cacheService;

    @BeforeEach
    void setUp() {
        cacheService = new AdminUserListCacheService(
                stringRedisTemplate,
                new ObjectMapper().findAndRegisterModules(),
                metricsRecorder
        );
    }

    @Test
    void putSerializesAdminUserPageWithStableCacheKey() {
        prepareValueOperations();
        Pageable pageable = PageRequest.of(1, 20, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<UserDTO> page = new PageImpl<>(List.of(user("Mother User")), pageable, 41);

        cacheService.put("  Mother  User ", UserRole.MOTHER, true, pageable, page);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(keyCaptor.capture(), valueCaptor.capture(), any());

        assertThat(keyCaptor.getValue())
                .isEqualTo("admin:users:list:q=mother user:role=MOTHER:status=active:page=1:size=20:sort=createdAt,DESC");
        assertThat(valueCaptor.getValue()).contains("Mother User", "\"totalElements\":41");
    }

    @Test
    void getReturnsCachedPageWhenRedisHasValidValue() {
        prepareValueOperations();
        Pageable pageable = PageRequest.of(0, 10);
        Page<UserDTO> page = new PageImpl<>(List.of(user("Cached User")), pageable, 1);
        cacheService.put(null, null, null, pageable, page);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(any(), valueCaptor.capture(), any());

        when(valueOperations.get("admin:users:list:q=all:role=all:status=all:page=0:size=10:sort=unsorted"))
                .thenReturn(valueCaptor.getValue());

        Page<UserDTO> cached = cacheService.get(null, null, null, pageable).orElseThrow();

        assertThat(cached.getTotalElements()).isEqualTo(1);
        assertThat(cached.getContent()).hasSize(1);
        assertThat(cached.getContent().getFirst().getFullName()).isEqualTo("Cached User");
    }

    @Test
    void getReturnsCachedPageWithRestoredSortOrder() {
        prepareValueOperations();
        Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "fullName"));
        Page<UserDTO> page = new PageImpl<>(List.of(user("Sorted User")), pageable, 1);
        cacheService.put(null, UserRole.NURSE, false, pageable, page);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(any(), valueCaptor.capture(), any());

        when(valueOperations.get("admin:users:list:q=all:role=NURSE:status=locked:page=0:size=10:sort=fullName,ASC"))
                .thenReturn(valueCaptor.getValue());

        Page<UserDTO> cached = cacheService.get(null, UserRole.NURSE, false, pageable).orElseThrow();

        assertThat(cached.getSort().getOrderFor("fullName")).isNotNull();
        assertThat(cached.getSort().getOrderFor("fullName").getDirection()).isEqualTo(Sort.Direction.ASC);
        assertThat(cached.getContent().getFirst().getFullName()).isEqualTo("Sorted User");
    }

    @Test
    void getReturnsEmptyWhenCachedValueIsBlank() {
        prepareValueOperations();
        Pageable pageable = PageRequest.of(0, 10);
        when(valueOperations.get("admin:users:list:q=all:role=all:status=all:page=0:size=10:sort=unsorted"))
                .thenReturn(" ");

        assertThat(cacheService.get(null, null, null, pageable)).isEmpty();

        verify(stringRedisTemplate, never()).delete(any(String.class));
    }

    @Test
    void getFallsBackWhenRedisValueIsCorruptAndDeletesKey() {
        prepareValueOperations();
        Pageable pageable = PageRequest.of(0, 10);
        String key = "admin:users:list:q=all:role=all:status=all:page=0:size=10:sort=unsorted";
        when(valueOperations.get(key)).thenReturn("{bad-json");

        assertThat(cacheService.get(null, null, null, pageable)).isEmpty();

        verify(stringRedisTemplate).delete(key);
    }

    @Test
    void getFallsBackWhenRedisValueIsCorruptAndDeleteFails() {
        prepareValueOperations();
        Pageable pageable = PageRequest.of(0, 10);
        String key = "admin:users:list:q=all:role=all:status=all:page=0:size=10:sort=unsorted";
        when(valueOperations.get(key)).thenReturn("{bad-json");
        doThrow(new RuntimeException("redis down")).when(stringRedisTemplate).delete(key);

        assertThat(cacheService.get(null, null, null, pageable)).isEmpty();

        verify(stringRedisTemplate).delete(key);
    }

    @Test
    void getReturnsEmptyPageWhenCachedContentAndSortAreNull() {
        prepareValueOperations();
        Pageable pageable = PageRequest.of(2, 5);
        String key = "admin:users:list:q=all:role=all:status=all:page=2:size=5:sort=unsorted";
        when(valueOperations.get(key)).thenReturn("""
                {
                  "content": null,
                  "pageNumber": 2,
                  "pageSize": 5,
                  "totalElements": 0,
                  "sort": null
                }
                """);

        Page<UserDTO> cached = cacheService.get(null, null, null, pageable).orElseThrow();

        assertThat(cached.getContent()).isEmpty();
        assertThat(cached.getSort().isUnsorted()).isTrue();
        assertThat(cached.getNumber()).isEqualTo(2);
        assertThat(cached.getSize()).isEqualTo(5);
    }

    @Test
    void getFallsBackWhenRedisReadFails() {
        prepareValueOperations();
        Pageable pageable = PageRequest.of(0, 10);
        when(valueOperations.get(any())).thenThrow(new RuntimeException("redis down"));

        assertThat(cacheService.get(null, null, null, pageable)).isEmpty();

        verify(stringRedisTemplate, never()).delete(any(String.class));
    }

    @Test
    void putDoesNotThrowWhenRedisWriteFails() {
        prepareValueOperations();
        Page<UserDTO> page = new PageImpl<>(List.of(user("Cached User")), PageRequest.of(0, 10), 1);
        doThrow(new RuntimeException("redis down")).when(valueOperations).set(any(), any(), any());

        cacheService.put(null, null, false, PageRequest.of(0, 10), page);

        verify(valueOperations).set(any(), any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void evictAllDeletesKeysMatchingAdminUserListPrefix() {
        Cursor<String> cursor = mock(Cursor.class);
        when(cursor.hasNext()).thenReturn(true, true, false);
        when(cursor.next())
                .thenReturn("admin:users:list:q=all:page=0:size=20:sort=unsorted")
                .thenReturn("admin:users:list:q=nurse:page=0:size=20:sort=unsorted");
        when(stringRedisTemplate.scan(any())).thenReturn(cursor);

        cacheService.evictAll();

        verify(stringRedisTemplate).delete(List.of(
                "admin:users:list:q=all:page=0:size=20:sort=unsorted",
                "admin:users:list:q=nurse:page=0:size=20:sort=unsorted"
        ));
    }

    @Test
    @SuppressWarnings("unchecked")
    void evictAllDoesNotThrowWhenBulkDeleteFails() {
        Cursor<String> cursor = mock(Cursor.class);
        List<String> keys = List.of("admin:users:list:q=all:page=0:size=20:sort=unsorted");
        when(cursor.hasNext()).thenReturn(true, false);
        when(cursor.next()).thenReturn(keys.getFirst());
        when(stringRedisTemplate.scan(any())).thenReturn(cursor);
        doThrow(new RuntimeException("redis down")).when(stringRedisTemplate).delete(keys);

        cacheService.evictAll();

        verify(stringRedisTemplate).delete(keys);
    }

    @Test
    @SuppressWarnings("unchecked")
    void evictAllStopsWhenRedisScanFails() {
        when(stringRedisTemplate.scan(any())).thenThrow(new RuntimeException("redis down"));

        cacheService.evictAll();

        verify(stringRedisTemplate, never()).delete(any(List.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void evictAllRecordsEmptyScanWithoutDeleting() {
        Cursor<String> cursor = mock(Cursor.class);
        when(cursor.hasNext()).thenReturn(false);
        when(stringRedisTemplate.scan(any())).thenReturn(cursor);

        cacheService.evictAll();

        verify(stringRedisTemplate, never()).delete(any(List.class));
    }

    private void prepareValueOperations() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    private UserDTO user(String fullName) {
        return UserDTO.builder()
                .id(UUID.randomUUID())
                .fullName(fullName)
                .phone("0912345678")
                .email("user@happabi.local")
                .isActive(true)
                .roles(List.of("MOTHER"))
                .createdAt(OffsetDateTime.parse("2026-08-04T09:00:00+07:00"))
                .build();
    }
}
