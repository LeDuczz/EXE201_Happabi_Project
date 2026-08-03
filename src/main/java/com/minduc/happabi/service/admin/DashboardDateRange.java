package com.minduc.happabi.service.admin;

import com.minduc.happabi.exception.AppException;
import com.minduc.happabi.exception.code.CommonErrorCode;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

public record DashboardDateRange(LocalDate from, LocalDate to) {

    private static final int DEFAULT_DAYS = 30;
    private static final int MAX_DAYS = 366;

    public static DashboardDateRange resolve(LocalDate requestedFrom, LocalDate requestedTo, Clock clock) {
        LocalDate today = LocalDate.now(clock);
        LocalDate to = requestedTo != null ? requestedTo : today;
        LocalDate from = requestedFrom != null ? requestedFrom : to.minusDays(DEFAULT_DAYS - 1L);

        if (from.isAfter(to)) {
            throw new AppException(CommonErrorCode.BAD_REQUEST, "Dashboard 'from' date must not be after 'to' date.");
        }

        long inclusiveDays = ChronoUnit.DAYS.between(from, to) + 1;
        if (inclusiveDays > MAX_DAYS) {
            throw new AppException(CommonErrorCode.BAD_REQUEST,
                    "Dashboard date range must not exceed " + MAX_DAYS + " days.");
        }

        return new DashboardDateRange(from, to);
    }

    public OffsetDateTime startAt(ZoneId zoneId) {
        return from.atStartOfDay(zoneId).toOffsetDateTime();
    }

    public OffsetDateTime endExclusiveAt(ZoneId zoneId) {
        return to.plusDays(1).atStartOfDay(zoneId).toOffsetDateTime();
    }
}
