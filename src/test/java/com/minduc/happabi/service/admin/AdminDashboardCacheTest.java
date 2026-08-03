package com.minduc.happabi.service.admin;

import com.minduc.happabi.dto.response.admin.dashboard.AdminOperationsDashboardResponse;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class AdminDashboardCacheTest {

    @Test
    void returnsCachedResponseUntilTtlExpires() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-04T00:00:00Z"));
        AdminDashboardCache cache = new AdminDashboardCache(clock, 60);
        LocalDate from = LocalDate.of(2026, 7, 6);
        LocalDate to = LocalDate.of(2026, 8, 4);
        AdminOperationsDashboardResponse response = AdminOperationsDashboardResponse.builder().build();

        cache.put(from, to, response);

        assertThat(cache.get(from, to)).containsSame(response);
        clock.advance(Duration.ofSeconds(61));
        assertThat(cache.get(from, to)).isEmpty();
    }

    @Test
    void separatesRangesByDateKey() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-04T00:00:00Z"));
        AdminDashboardCache cache = new AdminDashboardCache(clock, 60);
        AdminOperationsDashboardResponse first = AdminOperationsDashboardResponse.builder().build();
        AdminOperationsDashboardResponse second = AdminOperationsDashboardResponse.builder().build();

        cache.put(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 1), first);
        cache.put(LocalDate.of(2026, 8, 2), LocalDate.of(2026, 8, 2), second);

        assertThat(cache.get(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 1))).containsSame(first);
        assertThat(cache.get(LocalDate.of(2026, 8, 2), LocalDate.of(2026, 8, 2))).containsSame(second);
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("Asia/Ho_Chi_Minh");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
