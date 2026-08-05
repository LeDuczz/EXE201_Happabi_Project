package com.minduc.happabi.controller.admin;

import com.minduc.happabi.dto.response.admin.AdminBookingResponse;
import com.minduc.happabi.enums.BookingPaymentOption;
import com.minduc.happabi.enums.BookingStatus;
import com.minduc.happabi.service.admin.IAdminBookingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminBookingControllerTest {

    @Mock
    private IAdminBookingService adminBookingService;

    @Test
    void getBookingsPassesFiltersWithVietnamBusinessDayBounds() {
        AdminBookingController controller = new AdminBookingController(adminBookingService);
        Pageable pageable = Pageable.unpaged();
        when(adminBookingService.getBookings(
                eq("ngoc"),
                eq(BookingStatus.COMPLETED),
                eq(BookingPaymentOption.FULL_APP_PAYMENT),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                eq(pageable)))
                .thenReturn(Page.empty());

        var response = controller.getBookings(
                "ngoc",
                BookingStatus.COMPLETED,
                BookingPaymentOption.FULL_APP_PAYMENT,
                LocalDate.of(2026, 7, 14),
                LocalDate.of(2026, 7, 20),
                LocalDate.of(2026, 7, 21),
                LocalDate.of(2026, 7, 25),
                pageable);

        ArgumentCaptor<OffsetDateTime> createdFrom = ArgumentCaptor.forClass(OffsetDateTime.class);
        ArgumentCaptor<OffsetDateTime> createdTo = ArgumentCaptor.forClass(OffsetDateTime.class);
        ArgumentCaptor<OffsetDateTime> serviceFrom = ArgumentCaptor.forClass(OffsetDateTime.class);
        ArgumentCaptor<OffsetDateTime> serviceTo = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(adminBookingService).getBookings(
                eq("ngoc"),
                eq(BookingStatus.COMPLETED),
                eq(BookingPaymentOption.FULL_APP_PAYMENT),
                createdFrom.capture(),
                createdTo.capture(),
                serviceFrom.capture(),
                serviceTo.capture(),
                eq(pageable));
        assertThat(createdFrom.getValue()).isEqualTo(OffsetDateTime.parse("2026-07-14T00:00:00+07:00"));
        assertThat(createdTo.getValue()).isEqualTo(OffsetDateTime.parse("2026-07-21T00:00:00+07:00"));
        assertThat(serviceFrom.getValue()).isEqualTo(OffsetDateTime.parse("2026-07-21T00:00:00+07:00"));
        assertThat(serviceTo.getValue()).isEqualTo(OffsetDateTime.parse("2026-07-26T00:00:00+07:00"));
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).isEmpty();
    }

    @Test
    void getBookingDetailReturnsServicePayload() {
        AdminBookingController controller = new AdminBookingController(adminBookingService);
        UUID bookingId = UUID.randomUUID();
        AdminBookingResponse booking = AdminBookingResponse.builder()
                .id(bookingId)
                .bookingKey("W9-PROD-001")
                .build();
        when(adminBookingService.getBookingDetail(bookingId)).thenReturn(booking);

        var response = controller.getBookingDetail(bookingId);

        verify(adminBookingService).getBookingDetail(bookingId);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData().getBookingKey()).isEqualTo("W9-PROD-001");
    }
}
