package com.minduc.happabi.service.doctor;

import com.minduc.happabi.dto.request.nurse.ReviewNurseProfileRequest;
import com.minduc.happabi.dto.response.nurse.NurseOnboardingResponse;

import java.util.List;
import java.util.UUID;

public interface IDoctorNurseReviewService {
    List<NurseOnboardingResponse> getPendingReviews();

    NurseOnboardingResponse getForDoctor(UUID profileId);

    String getKycDocumentUrl(UUID profileId, String side);

    String getCertificationDocumentUrl(UUID certificationId);

    NurseOnboardingResponse approve(UUID profileId, ReviewNurseProfileRequest request);

    NurseOnboardingResponse reject(UUID profileId, ReviewNurseProfileRequest request);

}
