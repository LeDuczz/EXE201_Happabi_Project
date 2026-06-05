package com.minduc.happabi.service.booking;

import com.minduc.happabi.dto.request.booking.CreateBookingDraftRequest;
import com.minduc.happabi.dto.response.booking.BookingDraftResponse;

public interface IBookingService {
    BookingDraftResponse createDraft(CreateBookingDraftRequest request);
}
