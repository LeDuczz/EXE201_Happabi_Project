package com.minduc.happabi.controller.doctor;

import com.minduc.happabi.common.base.BaseResponse;
import com.minduc.happabi.dto.request.nurse.ReviewNurseProfileRequest;
import com.minduc.happabi.dto.response.nurse.NurseOnboardingResponse;
import com.minduc.happabi.dto.response.nurse.NurseReviewSummaryResponse;
import com.minduc.happabi.integration.s3.S3ObjectDownload;
import com.minduc.happabi.service.doctor.IDoctorNurseReviewService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<BaseResponse<List<NurseReviewSummaryResponse>>> getPendingReviews() {
        return ResponseEntity.ok(BaseResponse.ok(doctorNurseReviewService.getPendingReviews()));
    }

    @GetMapping("/{profileId}")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<BaseResponse<NurseOnboardingResponse>> getForDoctor(@PathVariable UUID profileId) {
        return ResponseEntity.ok(BaseResponse.ok(doctorNurseReviewService.getForDoctor(profileId)));
    }

    @GetMapping("/{profileId}/kyc/{side}/file")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<byte[]> getKycDocumentFile(
            @PathVariable UUID profileId,
            @PathVariable String side) {
        S3ObjectDownload file = doctorNurseReviewService.getKycDocumentFile(profileId, side);
        String filename = "cccd-" + side.toLowerCase() + ".jpg";
        return fileResponse(file, filename);
    }

    @GetMapping("/certifications/{certificationId}/file")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<byte[]> getCertificationDocumentFile(@PathVariable UUID certificationId) {
        S3ObjectDownload file = doctorNurseReviewService.getCertificationDocumentFile(certificationId);
        return fileResponse(file, "nurse-certification");
    }

    @PostMapping("/{profileId}/approve")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<BaseResponse<NurseOnboardingResponse>> approve(
            @PathVariable UUID profileId,
            @Valid @RequestBody ReviewNurseProfileRequest request) {
        return ResponseEntity.ok(BaseResponse.ok(doctorNurseReviewService.approve(profileId, request)));
    }

    @PostMapping("/{profileId}/reject")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<BaseResponse<NurseOnboardingResponse>> reject(
            @PathVariable UUID profileId,
            @Valid @RequestBody ReviewNurseProfileRequest request) {
        return ResponseEntity.ok(BaseResponse.ok(doctorNurseReviewService.reject(profileId, request)));
    }

    private ResponseEntity<byte[]> fileResponse(S3ObjectDownload file, String filename) {
        String contentType = file.contentType() == null || file.contentType().isBlank()
                ? MediaType.APPLICATION_OCTET_STREAM_VALUE
                : file.contentType();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .contentLength(file.contentLength() == null ? file.bytes().length : file.contentLength())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(filename)
                        .build()
                        .toString())
                .body(file.bytes());
    }
}
