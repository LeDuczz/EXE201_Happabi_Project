package com.minduc.happabi.dto.response.booking;

import com.minduc.happabi.enums.BookingCancellationActor;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
public class BookingCancellationResponse {
    private UUID id;
    private UUID bookingId;
    private BookingCancellationActor actor;
    private String reason;
    private boolean refundable;
    private Long refundableAmount;
    private OffsetDateTime policyCutoffAt;
    private OffsetDateTime createdAt;
}
