package com.minduc.happabi.exception;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;

/**
 * Common cross-cutting error codes shared across all domains.
 * Domain-specific codes should live in their own ErrorCode enum
 * (e.g. AuthErrorCode, BookingErrorCode) and implement ServiceErrorCode.
 */
@Getter
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public enum CommonErrorCode implements ServiceErrorCode {

    // ── 2xx ────────────────────────────────────────────────────────────────────
    SUCCESS(HttpStatus.OK, "Operation completed successfully"),

    // ── 4xx Client Errors ──────────────────────────────────────────────────────
    BAD_REQUEST(HttpStatus.BAD_REQUEST,             "Invalid request data"),
    VALIDATION_FAILED(HttpStatus.UNPROCESSABLE_ENTITY, "Validation failed. Check your input."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED,           "Authentication required. Please provide a valid token."),
    FORBIDDEN(HttpStatus.FORBIDDEN,                 "Access denied. You do not have permission to perform this action."),
    NOT_FOUND(HttpStatus.NOT_FOUND,                 "The requested resource was not found."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "HTTP method not allowed."),
    CONFLICT(HttpStatus.CONFLICT,                   "Resource already exists or conflict detected."),
    PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "Request payload exceeds the allowed limit."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported media type."),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "Too many requests. Please slow down."),

    // ── 5xx Server Errors ──────────────────────────────────────────────────────
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred. Please try again later."),
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Service temporarily unavailable. Please try again."),
    GATEWAY_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT,     "Upstream service timed out."),
    BAD_GATEWAY(HttpStatus.BAD_GATEWAY,             "Received an invalid response from an upstream service.");

    HttpStatus httpStatus;
    String message;
}
