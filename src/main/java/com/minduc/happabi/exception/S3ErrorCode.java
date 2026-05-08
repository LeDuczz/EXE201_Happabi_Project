package com.minduc.happabi.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum S3ErrorCode implements ServiceErrorCode {

    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE,       "File size exceeds the maximum allowed limit of 5 MB."),
    UNSUPPORTED_FILE_TYPE(HttpStatus.BAD_REQUEST,      "Unsupported file type. Allowed: JPEG, PNG, WebP, PDF."),
    UPLOAD_FAILED(HttpStatus.BAD_GATEWAY,              "Failed to upload file to storage. Please try again."),
    DELETE_FAILED(HttpStatus.BAD_GATEWAY,              "Failed to delete file from storage."),
    FILE_NOT_FOUND(HttpStatus.NOT_FOUND,               "The requested file does not exist.");

    private final HttpStatus httpStatus;
    private final String message;
}
