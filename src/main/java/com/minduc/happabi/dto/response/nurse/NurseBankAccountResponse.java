package com.minduc.happabi.dto.response.nurse;

import com.minduc.happabi.enums.NurseBankAccountStatus;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class NurseBankAccountResponse {
    private UUID id;
    private UUID nurseProfileId;
    private String bankName;
    private String bankAccountNumber;
    private String bankAccountHolder;
    private NurseBankAccountStatus status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
