package com.minduc.happabi.service.ocr.impl;

import com.minduc.happabi.dto.openai.OpenAiCccdOcrResult;
import com.minduc.happabi.dto.response.nurse.CccdOcrExtractionResponse;
import com.minduc.happabi.exception.AppException;
import com.minduc.happabi.exception.code.OcrErrorCode;
import com.minduc.happabi.integration.openai.OpenAiVisionOcrClient;
import com.minduc.happabi.service.ocr.CccdOcrFileValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CccdOcrServiceImplTest {

    @Mock
    private CccdOcrFileValidator fileValidator;

    @Mock
    private OpenAiVisionOcrClient openAiVisionOcrClient;

    @InjectMocks
    private CccdOcrServiceImpl service;

    private final MockMultipartFile file = new MockMultipartFile("front", "front.jpg", "image/jpeg", new byte[] {1});

    @Test
    void extractFrontSideMapsProviderResultAndDoesNotRequireManualReviewWhenFieldsAreComplete() {
        OpenAiCccdOcrResult result = new OpenAiCccdOcrResult();
        result.setCccdNumber("079123456789");
        result.setCccdName("Nguyen Van A");
        result.setCccdDob(LocalDate.of(1995, 1, 2));
        result.setCccdAddress("Ho Chi Minh");
        result.setConfidence(0.95);
        result.setRequiresManualReview(false);
        result.setWarnings(List.of("clear image"));
        when(openAiVisionOcrClient.extractCccdFront(file)).thenReturn(result);

        CccdOcrExtractionResponse response = service.extractFrontSide(file);

        assertThat(response.getCccdNumber()).isEqualTo("079123456789");
        assertThat(response.getRequiresManualReview()).isFalse();
        assertThat(response.getWarnings()).containsExactly("clear image");
        verify(fileValidator).validateFrontImage(file);
    }

    @Test
    void extractFrontSideRequiresManualReviewWhenRequiredProviderFieldsAreMissing() {
        OpenAiCccdOcrResult result = new OpenAiCccdOcrResult();
        result.setCccdNumber("079123456789");
        result.setConfidence(0.3);
        when(openAiVisionOcrClient.extractCccdFront(file)).thenReturn(result);

        CccdOcrExtractionResponse response = service.extractFrontSide(file);

        assertThat(response.getRequiresManualReview()).isTrue();
    }

    @Test
    void extractFrontSideReturnsFallbackWhenProviderIsUnavailable() {
        when(openAiVisionOcrClient.extractCccdFront(file))
                .thenThrow(new AppException(OcrErrorCode.OCR_PROVIDER_UNAVAILABLE));

        CccdOcrExtractionResponse response = service.extractFrontSide(file);

        assertThat(response.getRequiresManualReview()).isTrue();
        assertThat(response.getConfidence()).isZero();
        assertThat(response.getWarnings()).singleElement().asString().contains("temporarily unavailable");
    }

    @Test
    void extractFrontSidePropagatesValidationFailure() {
        doThrow(new AppException(OcrErrorCode.OCR_FILE_REQUIRED)).when(fileValidator).validateFrontImage(file);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.extractFrontSide(file))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(OcrErrorCode.OCR_FILE_REQUIRED);
    }
}
