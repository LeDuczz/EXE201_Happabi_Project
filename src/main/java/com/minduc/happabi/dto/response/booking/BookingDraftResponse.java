package com.minduc.happabi.dto.response.booking;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.minduc.happabi.enums.BookingPaymentOption;
import com.minduc.happabi.enums.BookingStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BookingDraftResponse {
    private UUID draftId;
    private UUID bookingId;
    private UUID slotId;
    private UUID nurseProfileId;
    private String nurseName;
    private UUID serviceOfferingId;
    private String serviceName;
    private BookingStatus status;
    private OffsetDateTime startAt;
    private OffsetDateTime endAt;
    private OffsetDateTime paymentExpiresAt;
    private Long grossAmount;
    private Long depositAmount;
    private Long remainingCashAmount;
    private Long appPaymentAmount;
    private BookingPaymentOption paymentOption;
    private String serviceAddress;
    private String motherNote;
}
