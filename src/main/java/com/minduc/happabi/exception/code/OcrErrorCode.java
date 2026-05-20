package com.minduc.happabi.exception.code;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public enum OcrErrorCode implements ServiceErrorCode {

    OCR_FILE_REQUIRED(HttpStatus.BAD_REQUEST, "CCCD front image is required."),
    OCR_FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "CCCD front image exceeds the maximum allowed size."),
    OCR_UNSUPPORTED_FILE_TYPE(HttpStatus.BAD_REQUEST, "Unsupported CCCD image type. Allowed: JPEG, PNG, WebP."),
    OCR_CONFIGURATION_MISSING(HttpStatus.INTERNAL_SERVER_ERROR, "OpenAI OCR configuration is missing."),
    OCR_PROVIDER_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "OCR provider is unavailable. Please try again."),
    OCR_RESPONSE_INVALID(HttpStatus.BAD_GATEWAY, "OCR provider returned an invalid response."),
    OCR_EXTRACTION_FAILED(HttpStatus.BAD_GATEWAY, "Failed to extract CCCD fields from image.");

    HttpStatus httpStatus;
    String message;
}
