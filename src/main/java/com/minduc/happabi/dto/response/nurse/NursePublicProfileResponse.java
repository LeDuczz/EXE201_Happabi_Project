package com.minduc.happabi.dto.response.nurse;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.minduc.happabi.enums.AvailabilityStatus;
import com.minduc.happabi.enums.NurseSpecialty;
import com.minduc.happabi.dto.response.booking.ServiceOfferingResponse;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder(toBuilder = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NursePublicProfileResponse {
    private UUID profileId;
    private String fullName;
    private String avatarUrl;
    private NurseSpecialty specialty;
    private Integer experienceYears;
    private String bio;
    private String serviceArea;
    private String city;
    private AvailabilityStatus availabilityStatus;
    private BigDecimal ratingAvg;
    private Integer totalReviews;
    private Integer totalCompletedJobs;
    private Integer noShowViolationCount;
    private OffsetDateTime bookingSuspendedUntil;
    private String bookingSuspensionReason;
    private Boolean backgroundChecked;
    private Boolean featured;
    private OffsetDateTime availabilityWindowStartAt;
    private OffsetDateTime availabilityWindowEndAt;
    private Long certificationCount;
    private List<NursePublicCertificationResponse> certifications;
    private List<NurseSkillResponse> skills;
    private List<ServiceOfferingResponse> eligibleServiceOfferings;
}
