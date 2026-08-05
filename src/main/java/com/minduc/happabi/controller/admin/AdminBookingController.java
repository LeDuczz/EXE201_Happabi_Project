package com.minduc.happabi.controller.admin;

import com.minduc.happabi.common.base.BaseResponse;
import com.minduc.happabi.dto.response.admin.AdminBookingResponse;
import com.minduc.happabi.enums.BookingPaymentOption;
import com.minduc.happabi.enums.BookingStatus;
import com.minduc.happabi.service.admin.IAdminBookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/bookings")
@Tag(name = "Admin Booking Management", description = "Booking list and detail views for administrators")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminBookingController {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final IAdminBookingService adminBookingService;

    @GetMapping
    @Operation(summary = "Get bookings with search, filters and pagination")
    public ResponseEntity<BaseResponse<Page<AdminBookingResponse>>> getBookings(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) BookingStatus status,
            @RequestParam(required = false) BookingPaymentOption paymentOption,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdTo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate serviceFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate serviceTo,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(BaseResponse.ok("Get admin bookings successfully",
                adminBookingService.getBookings(
                        query,
                        status,
                        paymentOption,
                        startOfDay(createdFrom),
                        exclusiveEndOfDay(createdTo),
                        startOfDay(serviceFrom),
                        exclusiveEndOfDay(serviceTo),
                        pageable)));
    }

    @GetMapping("/{bookingId}")
    @Operation(summary = "Get booking detail for admin")
    public ResponseEntity<BaseResponse<AdminBookingResponse>> getBookingDetail(@PathVariable UUID bookingId) {
        return ResponseEntity.ok(BaseResponse.ok("Get admin booking detail successfully",
                adminBookingService.getBookingDetail(bookingId)));
    }

    private OffsetDateTime startOfDay(LocalDate date) {
        return date == null ? null : date.atStartOfDay(BUSINESS_ZONE).toOffsetDateTime();
    }

    private OffsetDateTime exclusiveEndOfDay(LocalDate date) {
        return date == null ? null : date.plusDays(1).atStartOfDay(BUSINESS_ZONE).toOffsetDateTime();
    }
}
