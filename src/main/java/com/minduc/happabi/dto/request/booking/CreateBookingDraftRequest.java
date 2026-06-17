package com.minduc.happabi.dto.request.booking;

import com.minduc.happabi.enums.BookingPaymentOption;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
public class CreateBookingDraftRequest {

    @NotNull
    private UUID nurseProfileId;

    @NotNull
    private UUID serviceOfferingId;

    @NotNull
    @Future
    private OffsetDateTime startAt;

    @NotBlank
    @Size(max = 300)
    private String serviceAddress;

    @Size(max = 2000)
    private String motherNote;

    private BookingPaymentOption paymentOption;
}
