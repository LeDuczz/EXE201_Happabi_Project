package com.minduc.happabi.service.ocr;

import com.minduc.happabi.exception.AppException;
import com.minduc.happabi.exception.code.OcrErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CccdOcrFileValidatorTest {

    private final CccdOcrFileValidator validator = new CccdOcrFileValidator();

    @Test
    void validateFrontImageAcceptsSupportedImageUnderLimit() {
        MockMultipartFile file = new MockMultipartFile("front", "front.jpg", "image/jpeg", new byte[] {1, 2, 3});

        assertThatCode(() -> validator.validateFrontImage(file)).doesNotThrowAnyException();
    }

    @Test
    void validateFrontImageRejectsMissingFile() {
        assertThatThrownBy(() -> validator.validateFrontImage(null))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(OcrErrorCode.OCR_FILE_REQUIRED);
    }

    @Test
    void validateFrontImageRejectsUnsupportedContentType() {
        MockMultipartFile file = new MockMultipartFile("front", "front.txt", "text/plain", new byte[] {1});

        assertThatThrownBy(() -> validator.validateFrontImage(file))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(OcrErrorCode.OCR_UNSUPPORTED_FILE_TYPE);
    }

    @Test
    void validateFrontImageRejectsFileLargerThanFiveMb() {
        byte[] payload = new byte[(5 * 1024 * 1024) + 1];
        MockMultipartFile file = new MockMultipartFile("front", "front.png", "image/png", payload);

        assertThatThrownBy(() -> validator.validateFrontImage(file))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(OcrErrorCode.OCR_FILE_TOO_LARGE);
    }
}
