package com.minduc.happabi.service.ocr;

import com.minduc.happabi.dto.openai.OpenAiCccdOcrResult;
import com.minduc.happabi.dto.response.nurse.CccdOcrExtractionResponse;
import com.minduc.happabi.observability.annotation.AuditAction;
import com.minduc.happabi.observability.annotation.TimedAction;
import com.minduc.happabi.service.openai.OpenAiVisionOcrClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CccdOcrServiceImpl implements CccdOcrService {

    private final CccdOcrFileValidator fileValidator;
    private final OpenAiVisionOcrClient openAiVisionOcrClient;

    @Override
    @PreAuthorize("hasRole('NURSE')")
    @TimedAction("nurse_kyc_extract")
    @AuditAction(action = "NURSE_KYC_EXTRACT", resourceType = "NURSE_KYC")
    public CccdOcrExtractionResponse extractFrontSide(MultipartFile frontImage) {
        fileValidator.validateFrontImage(frontImage);

        OpenAiCccdOcrResult result = openAiVisionOcrClient.extractCccdFront(frontImage);
        List<String> warnings = result.getWarnings() != null ? result.getWarnings() : List.of();

        boolean requiresManualReview = Boolean.TRUE.equals(result.getRequiresManualReview())
                || result.getCccdNumber() == null
                || result.getCccdName() == null
                || result.getCccdDob() == null
                || result.getCccdAddress() == null;

        log.info("[OCR] CCCD front extraction completed: confidence={} manualReview={}",
                result.getConfidence(), requiresManualReview);

        return CccdOcrExtractionResponse.builder()
                .cccdNumber(result.getCccdNumber())
                .cccdName(result.getCccdName())
                .cccdDob(result.getCccdDob())
                .cccdAddress(result.getCccdAddress())
                .confidence(result.getConfidence())
                .requiresManualReview(requiresManualReview)
                .warnings(warnings)
                .build();
    }
}
