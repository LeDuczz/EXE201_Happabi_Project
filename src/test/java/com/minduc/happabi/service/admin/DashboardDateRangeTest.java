package com.minduc.happabi.service.admin;

import com.minduc.happabi.exception.AppException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DashboardDateRangeTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-08-04T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));

    @Test
    void defaultsToLastThirtyInclusiveDays() {
        DashboardDateRange range = DashboardDateRange.resolve(null, null, clock);

        assertThat(range.from()).isEqualTo(LocalDate.of(2026, 7, 6));
        assertThat(range.to()).isEqualTo(LocalDate.of(2026, 8, 4));
        assertThat(range.startAt(clock.getZone()).toLocalDate()).isEqualTo(range.from());
        assertThat(range.endExclusiveAt(clock.getZone()).toLocalDate()).isEqualTo(LocalDate.of(2026, 8, 5));
    }

    @Test
    void supportsSingleDayRange() {
        LocalDate date = LocalDate.of(2026, 7, 20);

        DashboardDateRange range = DashboardDateRange.resolve(date, date, clock);

        assertThat(range.from()).isEqualTo(date);
        assertThat(range.to()).isEqualTo(date);
        assertThat(range.endExclusiveAt(clock.getZone()).toLocalDate()).isEqualTo(date.plusDays(1));
    }

    @Test
    void rejectsInvertedRange() {
        assertThatThrownBy(() -> DashboardDateRange.resolve(
                LocalDate.of(2026, 8, 5),
                LocalDate.of(2026, 8, 4),
                clock))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("must not be after");
    }

    @Test
    void rejectsRangesOverOneYear() {
        assertThatThrownBy(() -> DashboardDateRange.resolve(
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2026, 8, 4),
                clock))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("must not exceed");
    }
}
