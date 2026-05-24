package com.minduc.happabi.controller.nurse;

import com.minduc.happabi.common.base.BaseResponse;
import com.minduc.happabi.dto.request.nurse.*;
import com.minduc.happabi.dto.response.nurse.NurseCertificationResponse;
import com.minduc.happabi.dto.response.nurse.NurseOnboardingResponse;
import com.minduc.happabi.service.nurse.INurseOnboardingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/nurses/me/onboarding")
@RequiredArgsConstructor
public class NurseController {

    private final INurseOnboardingService nurseOnboardingService;

    @GetMapping
    public ResponseEntity<BaseResponse<NurseOnboardingResponse>> getMyOnboarding() {
        return ResponseEntity.ok(BaseResponse.ok(nurseOnboardingService.getMyOnboarding()));
    }

    @PatchMapping("/profile")
    public ResponseEntity<BaseResponse<NurseOnboardingResponse>> updateMyProfile(
            @Valid @RequestBody UpdateNurseProfileRequest request) {
        return ResponseEntity.ok(BaseResponse.ok(nurseOnboardingService.updateMyProfile(request)));
    }

    @PostMapping(value = "/kyc", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BaseResponse<NurseOnboardingResponse>> updateMyKyc(
            @Valid @ModelAttribute UpdateNurseKycRequest request,
            @RequestPart(value = "frontImage", required = false) MultipartFile frontImage,
            @RequestPart(value = "backImage", required = false) MultipartFile backImage) {
        return ResponseEntity.ok(BaseResponse.ok(nurseOnboardingService.updateMyKyc(request, frontImage, backImage)));
    }

    @PostMapping(value = "/certifications", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BaseResponse<NurseCertificationResponse>> addMyCertification(
            @Valid @ModelAttribute CreateNurseCertificationRequest request,
            @RequestPart("document") MultipartFile document) {
        return ResponseEntity.ok(BaseResponse.ok(nurseOnboardingService.addMyCertification(request, document)));
    }

    @PostMapping("/submit")
    public ResponseEntity<BaseResponse<NurseOnboardingResponse>> submitMyProfile() {
        return ResponseEntity.ok(BaseResponse.ok(nurseOnboardingService.submitMyProfile()));
    }

    @PostMapping("/contract/sign")
    public ResponseEntity<BaseResponse<NurseOnboardingResponse>> signContract(
            @Valid @RequestBody SignNurseContractRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(BaseResponse.ok(nurseOnboardingService.signContract(request, httpRequest)));
    }
}
