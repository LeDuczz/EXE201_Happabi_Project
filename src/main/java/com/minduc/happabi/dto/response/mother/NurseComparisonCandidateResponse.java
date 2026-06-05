package com.minduc.happabi.dto.response.mother;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.minduc.happabi.enums.AvailabilityStatus;
import com.minduc.happabi.enums.NurseSpecialty;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NurseComparisonCandidateResponse {

    private UUID profileId;
    private String fullName;
    private NurseSpecialty specialty;
    private Integer experienceYears;
    private String city;
    private String serviceArea;
    private AvailabilityStatus availabilityStatus;
    private BigDecimal ratingAvg;
    private Integer totalReviews;
    private Integer totalCompletedJobs;
    private BigDecimal responseRate;
    private Boolean backgroundChecked;
    private Boolean featured;
    private List<String> verifiedCertifications;
    private Integer fitScore;
    private List<String> strengths;
    private List<String> watchPoints;
}
