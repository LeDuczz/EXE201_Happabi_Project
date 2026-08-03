package com.minduc.happabi.service.admin;

import com.minduc.happabi.dto.response.admin.dashboard.AdminOperationsDashboardResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class AdminDashboardCache {

    private final ConcurrentMap<String, CacheEntry> entries = new ConcurrentHashMap<>();
    private final Clock clock;
    private final Duration ttl;

    public AdminDashboardCache(
            Clock clock,
            @Value("${app.dashboard.cache-ttl-seconds:120}") long ttlSeconds) {
        this.clock = clock;
        this.ttl = Duration.ofSeconds(Math.max(1, ttlSeconds));
    }

    public Optional<AdminOperationsDashboardResponse> get(LocalDate from, LocalDate to) {
        String key = key(from, to);
        CacheEntry entry = entries.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        if (!entry.expiresAt().isAfter(Instant.now(clock))) {
            entries.remove(key, entry);
            return Optional.empty();
        }
        return Optional.of(entry.response());
    }

    public void put(LocalDate from, LocalDate to, AdminOperationsDashboardResponse response) {
        entries.put(key(from, to), new CacheEntry(response, Instant.now(clock).plus(ttl)));
    }

    void clear() {
        entries.clear();
    }

    private String key(LocalDate from, LocalDate to) {
        return from + ":" + to;
    }

    private record CacheEntry(AdminOperationsDashboardResponse response, Instant expiresAt) {
    }
}
