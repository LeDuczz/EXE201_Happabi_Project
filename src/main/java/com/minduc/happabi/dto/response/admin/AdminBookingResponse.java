package com.minduc.happabi.dto.response.admin;

import com.minduc.happabi.enums.BookingPaymentOption;
import com.minduc.happabi.enums.BookingStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
public class AdminBookingResponse {

    private UUID id;
    private String bookingKey;
    private BookingStatus status;
    private BookingPaymentOption paymentOption;
    private OffsetDateTime startAt;
    private OffsetDateTime endAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private Long grossAmount;
    private Long appPaymentAmount;
    private Long depositAmount;
    private Long remainingCashAmount;
    private Long platformFeeAmount;
    private Long nurseEarningAmount;
    private String serviceAddress;
    private String motherNote;
    private Party mother;
    private Party nurse;
    private ServiceSummary service;

    @Getter
    @Builder
    public static class Party {
        private UUID id;
        private String fullName;
        private String phone;
        private String email;
    }

    @Getter
    @Builder
    public static class ServiceSummary {
        private UUID id;
        private String serviceCode;
        private String serviceName;
        private String groupName;
    }
}
