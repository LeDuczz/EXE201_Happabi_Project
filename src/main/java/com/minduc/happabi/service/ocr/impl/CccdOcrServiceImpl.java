package com.minduc.happabi.service.ocr.impl;

import com.minduc.happabi.dto.openai.OpenAiCccdOcrResult;
import com.minduc.happabi.dto.response.nurse.CccdOcrExtractionResponse;
import com.minduc.happabi.exception.AppException;
import com.minduc.happabi.exception.code.OcrErrorCode;
import com.minduc.happabi.observability.annotation.AuditAction;
import com.minduc.happabi.observability.annotation.TimedAction;
import com.minduc.happabi.service.ai.OpenAiVisionOcrClient;
import com.minduc.happabi.service.ocr.CccdOcrFileValidator;
import com.minduc.happabi.service.ocr.CccdOcrService;
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
    @TimedAction("NURSE_KYC_EXTRACT")
    @AuditAction(action = "NURSE_KYC_EXTRACT", resourceType = "NURSE_KYC")
    public CccdOcrExtractionResponse extractFrontSide(MultipartFile frontImage) {
        fileValidator.validateFrontImage(frontImage);

        OpenAiCccdOcrResult result;
        try {
            result = openAiVisionOcrClient.extractCccdFront(frontImage);
        } catch (AppException e) {
            if (e.getErrorCode() != OcrErrorCode.OCR_PROVIDER_UNAVAILABLE) {
                throw e;
            }
            log.warn("[OCR] Provider unavailable; returning manual review fallback.");
            return CccdOcrExtractionResponse.builder()
                    .confidence(0.0)
                    .requiresManualReview(true)
                    .warnings(List.of("AI OCR is temporarily unavailable. " +
                            "Please enter CCCD information manually."))
                    .build();
        }
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
