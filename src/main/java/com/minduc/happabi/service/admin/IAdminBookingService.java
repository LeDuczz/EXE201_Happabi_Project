package com.minduc.happabi.service.admin;

import com.minduc.happabi.dto.response.admin.AdminBookingResponse;
import com.minduc.happabi.enums.BookingPaymentOption;
import com.minduc.happabi.enums.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface IAdminBookingService {

    Page<AdminBookingResponse> getBookings(
            String query,
            BookingStatus status,
            BookingPaymentOption paymentOption,
            OffsetDateTime createdFrom,
            OffsetDateTime createdTo,
            OffsetDateTime serviceFrom,
            OffsetDateTime serviceTo,
            Pageable pageable);

    AdminBookingResponse getBookingDetail(UUID bookingId);
}
