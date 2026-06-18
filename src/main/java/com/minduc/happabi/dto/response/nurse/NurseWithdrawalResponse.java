package com.minduc.happabi.dto.response.nurse;

import com.minduc.happabi.enums.NurseWithdrawalStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class NurseWithdrawalResponse {
    private UUID id;
    private UUID nurseProfileId;
    private String nurseName;
    private UUID bankAccountId;
    private BigDecimal amount;
    private String bankName;
    private String bankAccountNumber;
    private String bankAccountHolder;
    private NurseWithdrawalStatus status;
    private String nurseNote;
    private String adminNote;
    private String bankTransactionCode;
    private String transferEvidenceUrl;
    private String processedByAdminName;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private OffsetDateTime approvedAt;
    private OffsetDateTime rejectedAt;
    private OffsetDateTime cancelledAt;
}
