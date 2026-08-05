package com.minduc.happabi.controller.admin;

import com.minduc.happabi.dto.request.admin.AdminBookingSearchCriteria;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
        when(adminBookingService.getBookings(any(AdminBookingSearchCriteria.class), eq(pageable)))
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

        ArgumentCaptor<AdminBookingSearchCriteria> criteria = ArgumentCaptor.forClass(AdminBookingSearchCriteria.class);
        verify(adminBookingService).getBookings(criteria.capture(), eq(pageable));
        assertThat(criteria.getValue().getQuery()).isEqualTo("ngoc");
        assertThat(criteria.getValue().getStatus()).isEqualTo(BookingStatus.COMPLETED);
        assertThat(criteria.getValue().getPaymentOption()).isEqualTo(BookingPaymentOption.FULL_APP_PAYMENT);
        assertThat(criteria.getValue().getCreatedFrom()).hasToString("2026-07-14T00:00+07:00");
        assertThat(criteria.getValue().getCreatedTo()).hasToString("2026-07-21T00:00+07:00");
        assertThat(criteria.getValue().getServiceFrom()).hasToString("2026-07-21T00:00+07:00");
        assertThat(criteria.getValue().getServiceTo()).hasToString("2026-07-26T00:00+07:00");
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
