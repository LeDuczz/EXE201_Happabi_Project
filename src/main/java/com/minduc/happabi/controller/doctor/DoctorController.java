package com.minduc.happabi.controller.doctor;

import com.minduc.happabi.common.base.BaseResponse;
import com.minduc.happabi.dto.request.nurse.ReviewNurseProfileRequest;
import com.minduc.happabi.dto.response.nurse.NurseOnboardingResponse;
import com.minduc.happabi.service.doctor.IDoctorNurseReviewService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/doctor/nurses")
@RequiredArgsConstructor
@Tag(name = "Doctor Nurse Review", description = "APIs for doctors to review nurse onboarding applications")
public class DoctorController {

    private final IDoctorNurseReviewService doctorNurseReviewService;

    @GetMapping("/pending-review")
    public ResponseEntity<BaseResponse<List<NurseOnboardingResponse>>> getPendingReviews() {
        return ResponseEntity.ok(BaseResponse.ok(doctorNurseReviewService.getPendingReviews()));
    }

    @GetMapping("/{profileId}")
    public ResponseEntity<BaseResponse<NurseOnboardingResponse>> getForDoctor(@PathVariable UUID profileId) {
        return ResponseEntity.ok(BaseResponse.ok(doctorNurseReviewService.getForDoctor(profileId)));
    }

    @GetMapping("/{profileId}/kyc/{side}/url")
    public ResponseEntity<BaseResponse<String>> getKycDocumentUrl(
            @PathVariable UUID profileId,
            @PathVariable String side) {
        return ResponseEntity.ok(BaseResponse.ok(doctorNurseReviewService.getKycDocumentUrl(profileId, side)));
    }

    @GetMapping("/certifications/{certificationId}/url")
    public ResponseEntity<BaseResponse<String>> getCertificationDocumentUrl(@PathVariable UUID certificationId) {
        return ResponseEntity.ok(BaseResponse.ok(doctorNurseReviewService.getCertificationDocumentUrl(certificationId)));
    }

    @PostMapping("/{profileId}/approve")
    public ResponseEntity<BaseResponse<NurseOnboardingResponse>> approve(
            @PathVariable UUID profileId,
            @Valid @RequestBody ReviewNurseProfileRequest request) {
        return ResponseEntity.ok(BaseResponse.ok(doctorNurseReviewService.approve(profileId, request)));
    }

    @PostMapping("/{profileId}/reject")
    public ResponseEntity<BaseResponse<NurseOnboardingResponse>> reject(
            @PathVariable UUID profileId,
            @Valid @RequestBody ReviewNurseProfileRequest request) {
        return ResponseEntity.ok(BaseResponse.ok(doctorNurseReviewService.reject(profileId, request)));
    }
}
