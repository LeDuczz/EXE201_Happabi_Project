package com.minduc.happabi.service.doctor;

import com.minduc.happabi.dto.request.nurse.ReviewNurseProfileRequest;
import com.minduc.happabi.dto.response.nurse.NurseOnboardingResponse;
import com.minduc.happabi.dto.response.nurse.NurseReviewSummaryResponse;
import com.minduc.happabi.integration.s3.S3ObjectDownload;

import java.util.List;
import java.util.UUID;

public interface IDoctorNurseReviewService {

    List<NurseReviewSummaryResponse> getPendingReviews();

    NurseOnboardingResponse getForDoctor(UUID profileId);

    S3ObjectDownload getKycDocumentFile(UUID profileId, String side);

    S3ObjectDownload getCertificationDocumentFile(UUID certificationId);

    NurseOnboardingResponse approve(UUID profileId, ReviewNurseProfileRequest request);

    NurseOnboardingResponse reject(UUID profileId, ReviewNurseProfileRequest request);

}
