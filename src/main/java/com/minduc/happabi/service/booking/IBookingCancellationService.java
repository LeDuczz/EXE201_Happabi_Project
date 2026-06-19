package com.minduc.happabi.service.booking;

import com.minduc.happabi.dto.request.booking.CancelBookingRequest;
import com.minduc.happabi.dto.response.booking.BookingCancellationResponse;

import java.util.UUID;

public interface IBookingCancellationService {
    BookingCancellationResponse cancelByMother(UUID bookingId, CancelBookingRequest request);

    BookingCancellationResponse cancelByNurse(UUID bookingId, CancelBookingRequest request);
}
