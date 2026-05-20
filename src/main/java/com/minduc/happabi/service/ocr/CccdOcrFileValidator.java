package com.minduc.happabi.service.ocr;

import com.minduc.happabi.exception.AppException;
import com.minduc.happabi.exception.code.OcrErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

@Component
public class CccdOcrFileValidator {

    private static final long MAX_SIZE_BYTES = 5L * 1024 * 1024;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    public void validateFrontImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AppException(OcrErrorCode.OCR_FILE_REQUIRED);
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new AppException(OcrErrorCode.OCR_FILE_TOO_LARGE,
                    "Max allowed size is " + (MAX_SIZE_BYTES / 1024 / 1024) + " MB.");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new AppException(OcrErrorCode.OCR_UNSUPPORTED_FILE_TYPE,
                    "Received contentType=" + contentType);
        }
    }
}
