package com.minduc.happabi.service.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minduc.happabi.dto.UserDTO;
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
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
        Pageable pageable = PageRequest.of(1, 20);
        Page<UserDTO> page = new PageImpl<>(List.of(user("Mother User")), pageable, 41);

        cacheService.put("  Mother  User ", pageable, page);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(keyCaptor.capture(), valueCaptor.capture(), any());

        assertThat(keyCaptor.getValue())
                .isEqualTo("admin:users:list:q=mother user:page=1:size=20:sort=unsorted");
        assertThat(valueCaptor.getValue()).contains("Mother User", "\"totalElements\":41");
    }

    @Test
    void getReturnsCachedPageWhenRedisHasValidValue() {
        prepareValueOperations();
        Pageable pageable = PageRequest.of(0, 10);
        Page<UserDTO> page = new PageImpl<>(List.of(user("Cached User")), pageable, 1);
        cacheService.put(null, pageable, page);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(any(), valueCaptor.capture(), any());

        when(valueOperations.get("admin:users:list:q=all:page=0:size=10:sort=unsorted"))
                .thenReturn(valueCaptor.getValue());

        Page<UserDTO> cached = cacheService.get(null, pageable).orElseThrow();

        assertThat(cached.getTotalElements()).isEqualTo(1);
        assertThat(cached.getContent()).hasSize(1);
        assertThat(cached.getContent().getFirst().getFullName()).isEqualTo("Cached User");
    }

    @Test
    void getFallsBackWhenRedisValueIsCorruptAndDeletesKey() {
        prepareValueOperations();
        Pageable pageable = PageRequest.of(0, 10);
        String key = "admin:users:list:q=all:page=0:size=10:sort=unsorted";
        when(valueOperations.get(key)).thenReturn("{bad-json");

        assertThat(cacheService.get(null, pageable)).isEmpty();

        verify(stringRedisTemplate).delete(key);
    }

    @Test
    void getFallsBackWhenRedisReadFails() {
        prepareValueOperations();
        Pageable pageable = PageRequest.of(0, 10);
        when(valueOperations.get(any())).thenThrow(new RuntimeException("redis down"));

        assertThat(cacheService.get(null, pageable)).isEmpty();

        verify(stringRedisTemplate, never()).delete(any(String.class));
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
