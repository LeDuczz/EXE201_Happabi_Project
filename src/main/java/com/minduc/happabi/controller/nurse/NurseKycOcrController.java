package com.minduc.happabi.controller.nurse;

import com.minduc.happabi.common.base.BaseResponse;
import com.minduc.happabi.dto.response.nurse.CccdOcrExtractionResponse;
import com.minduc.happabi.service.ocr.CccdOcrService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/nurses/onboarding/me/kyc")
@RequiredArgsConstructor
public class NurseKycOcrController {

    private final CccdOcrService cccdOcrService;

    @PostMapping(value = "/extract", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('NURSE')")
    public ResponseEntity<BaseResponse<CccdOcrExtractionResponse>> extractCccdFront(
            @RequestPart("frontImage") MultipartFile frontImage) {
        return ResponseEntity.ok(BaseResponse.ok(
                "CCCD fields extracted successfully.",
                cccdOcrService.extractFrontSide(frontImage)
        ));
    }
}
