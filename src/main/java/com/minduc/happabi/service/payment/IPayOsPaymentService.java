package com.minduc.happabi.service.payment;

import com.minduc.happabi.dto.request.nurse.TopUpRequest;
import com.minduc.happabi.dto.response.payment.BookingPaymentLinkResponse;

import java.util.UUID;

public interface IPayOsPaymentService {

    String createTopUpPaymentLink(TopUpRequest request);

    BookingPaymentLinkResponse createBookingPaymentLink(UUID bookingId);

}
