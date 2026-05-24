package com.minduc.happabi.dto.response.nurse;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.minduc.happabi.enums.NurseSpecialty;
import com.minduc.happabi.enums.NurseStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NurseOnboardingResponse {
    private UUID profileId;
    private UUID userId;
    private String fullName;
    private String phone;
    private String email;
    private String licenseNumber;
    private LocalDate dateOfBirth;
    private NurseSpecialty specialty;
    private Integer experienceYears;
    private String bio;
    private String serviceArea;
    private String address;
    private String city;
    private NurseStatus nurseStatus;
    private String rejectionReason;
    private OffsetDateTime lastStatusChangedAt;
    private Boolean profileCompleted;
    private Boolean kycCompleted;
    private Boolean certificationsCompleted;
    private Boolean contractSigned;
    private NurseKycResponse kyc;
    private List<NurseCertificationResponse> certifications;
    private NurseContractResponse latestContract;
}
