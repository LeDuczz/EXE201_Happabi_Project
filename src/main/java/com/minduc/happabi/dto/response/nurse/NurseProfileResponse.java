package com.minduc.happabi.dto.response.nurse;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.minduc.happabi.enums.AvailabilityStatus;
import com.minduc.happabi.enums.EkycStatus;
import com.minduc.happabi.enums.NurseContractStatus;
import com.minduc.happabi.enums.NurseSpecialty;
import com.minduc.happabi.enums.NurseStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NurseProfileResponse {

    private UUID id;

    private UUID profileId;

    private String fullName;

    private String phone;

    private String email;

    private String dayOfBirth;

    private String licenseNumber;

    private NurseSpecialty specialty;

    private Integer experienceYears;

    private String bio;

    private String serviceArea;

    private String address;

    private String city;

    private String avatarUrl;

    private NurseStatus nurseStatus;

    private AvailabilityStatus availabilityStatus;

    private BigDecimal ratingAvg;

    private Integer totalReviews;

    private Integer totalCompletedJobs;

    private BigDecimal responseRate;

    private Boolean backgroundChecked;

    private Boolean featured;

    private EkycStatus kycStatus;

    private Boolean kycVerified;

    private Boolean kycHasFrontImage;

    private Boolean kycHasBackImage;

    private NurseContractStatus contractStatus;

    private Boolean contractSigned;

    private OffsetDateTime contractSignedAt;

    private Boolean profileCompleted;

    private Boolean certificationsCompleted;

    private Long certificationCount;

    private List<NurseCertificationResponse> certifications;

    private Boolean canEditProfessionalInfo;
}
