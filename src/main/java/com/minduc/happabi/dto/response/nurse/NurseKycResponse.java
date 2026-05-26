package com.minduc.happabi.dto.response.nurse;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.minduc.happabi.enums.EkycStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NurseKycResponse {
    private UUID id;
    private String cccdNumber;
    private String cccdName;
    private LocalDate cccdDob;
    private String cccdAddress;
    private Boolean hasFrontImage;
    private Boolean hasBackImage;
    private EkycStatus ekycStatus;
    private String reviewNote;
    private OffsetDateTime reviewedAt;
}
