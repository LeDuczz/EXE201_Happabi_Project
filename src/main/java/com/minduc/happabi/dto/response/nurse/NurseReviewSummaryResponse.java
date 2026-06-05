package com.minduc.happabi.dto.response.nurse;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.minduc.happabi.enums.NurseSpecialty;
import com.minduc.happabi.enums.NurseStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NurseReviewSummaryResponse {
    private UUID profileId;
    private UUID userId;
    private String fullName;
    private String phone;
    private String email;
    private NurseSpecialty specialty;
    private Integer experienceYears;
    private String city;
    private NurseStatus nurseStatus;
    private OffsetDateTime lastStatusChangedAt;
    private Boolean profileCompleted;
    private Boolean kycCompleted;
    private Boolean certificationsCompleted;
    private Long certificationCount;
}
