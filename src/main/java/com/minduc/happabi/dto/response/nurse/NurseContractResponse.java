package com.minduc.happabi.dto.response.nurse;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.minduc.happabi.enums.NurseContractStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NurseContractResponse {
    private UUID id;
    private String contractVersion;
    private NurseContractStatus status;
    private String signedName;
    private OffsetDateTime signedAt;
}
