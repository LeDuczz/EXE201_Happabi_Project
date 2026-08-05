package com.minduc.happabi.dto.request.admin;

import com.minduc.happabi.enums.BookingPaymentOption;
import com.minduc.happabi.enums.BookingStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

@Getter
@Builder
public class AdminBookingSearchCriteria {

    private String query;
    private BookingStatus status;
    private BookingPaymentOption paymentOption;
    private OffsetDateTime createdFrom;
    private OffsetDateTime createdTo;
    private OffsetDateTime serviceFrom;
    private OffsetDateTime serviceTo;
}
