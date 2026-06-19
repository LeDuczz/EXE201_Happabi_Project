package com.minduc.happabi.dto.response.booking;

import com.minduc.happabi.enums.MotherRefundStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
public class MotherRefundResponse {
    private UUID id;
    private UUID bookingId;
    private UUID motherId;
    private String motherName;
    private String motherPhone;
    private Long amount;
    private MotherRefundStatus status;
    private String reason;
    private String adminNote;
    private String bankTransactionCode;
    private String transferEvidenceUrl;
    private String processedByAdminName;
    private OffsetDateTime createdAt;
    private OffsetDateTime approvedAt;
    private OffsetDateTime rejectedAt;
}
