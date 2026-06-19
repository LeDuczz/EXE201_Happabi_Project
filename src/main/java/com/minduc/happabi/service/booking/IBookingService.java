package com.minduc.happabi.service.booking;

import com.minduc.happabi.dto.request.booking.CreateBookingRequest;
import com.minduc.happabi.dto.response.booking.BookingResponse;

import java.util.List;

public interface IBookingService {
    BookingResponse createBooking(CreateBookingRequest request);

    List<BookingResponse> getMyPendingPayments();
}

