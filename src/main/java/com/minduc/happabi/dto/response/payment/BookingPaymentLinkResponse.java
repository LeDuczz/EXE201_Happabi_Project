package com.minduc.happabi.dto.response.payment;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
public class BookingPaymentLinkResponse {
    private UUID bookingId;
    private Long transactionId;
    private Long amount;
    private String checkoutUrl;
    private OffsetDateTime paymentExpiresAt;
}
