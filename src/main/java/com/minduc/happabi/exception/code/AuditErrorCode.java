package com.minduc.happabi.exception.code;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public enum AuditErrorCode implements ServiceErrorCode {
    AUDIT_LOG_SEARCH_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to search audit logs.");

    HttpStatus httpStatus;
    String message;
}
