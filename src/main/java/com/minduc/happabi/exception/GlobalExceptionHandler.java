package com.minduc.happabi.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ErrorResponse> handleAppException(AppException ex,
                                                            HttpServletRequest request) {
        ServiceErrorCode code = ex.getErrorCode();
        log.warn("[AppException] {} | path={} | detail={}",
                code.name(), request.getRequestURI(), ex.getMessage());

        return ResponseEntity
                .status(code.getHttpStatus())
                .body(ErrorResponse.of(code, request.getRequestURI(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        List<ErrorResponse.FieldError> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fe -> ErrorResponse.FieldError.of(
                        fe.getField(),
                        fe.getRejectedValue(),
                        fe.getDefaultMessage()))
                .collect(Collectors.toList());

        log.warn("[ValidationFailed] path={} | fields={}", request.getRequestURI(), fieldErrors);

        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.ofValidation(
                        CommonErrorCode.VALIDATION_FAILED,
                        request.getRequestURI(),
                        fieldErrors));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex,
            HttpServletRequest request) {

        List<ErrorResponse.FieldError> fieldErrors = ex.getConstraintViolations()
                .stream()
                .map(cv -> ErrorResponse.FieldError.of(
                        extractFieldName(cv),
                        cv.getInvalidValue(),
                        cv.getMessage()))
                .collect(Collectors.toList());

        log.warn("[ConstraintViolation] path={} | violations={}", request.getRequestURI(), fieldErrors);

        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.ofValidation(
                        CommonErrorCode.VALIDATION_FAILED,
                        request.getRequestURI(),
                        fieldErrors));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMessageNotReadable(
            HttpMessageNotReadableException ex,
            HttpServletRequest request) {
        log.warn("[MalformedRequest] path={} | {}", request.getRequestURI(), ex.getMessage());
        return buildError(CommonErrorCode.BAD_REQUEST,
                "Malformed JSON or unreadable request body.",
                request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(
            MissingServletRequestParameterException ex,
            HttpServletRequest request) {
        String detail = "Required parameter '" + ex.getParameterName() + "' is missing.";
        log.warn("[MissingParam] path={} | {}", request.getRequestURI(), detail);
        return buildError(CommonErrorCode.BAD_REQUEST, detail, request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest request) {
        String detail = String.format(
                "Parameter '%s' should be of type '%s'.",
                ex.getName(),
                ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown");
        log.warn("[TypeMismatch] path={} | {}", request.getRequestURI(), detail);
        return buildError(CommonErrorCode.BAD_REQUEST, detail, request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException ex,
            HttpServletRequest request) {
        log.warn("[MethodNotAllowed] method={} path={}", ex.getMethod(), request.getRequestURI());
        return buildError(CommonErrorCode.METHOD_NOT_ALLOWED,
                ex.getMessage(), request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException ex,
            HttpServletRequest request) {
        log.warn("[UnsupportedMediaType] path={} | {}", request.getRequestURI(), ex.getMessage());
        return buildError(CommonErrorCode.UNSUPPORTED_MEDIA_TYPE,
                ex.getMessage(), request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSize(
            MaxUploadSizeExceededException ex,
            HttpServletRequest request) {
        log.warn("[FileTooLarge] path={} | {}", request.getRequestURI(), ex.getMessage());
        return buildError(CommonErrorCode.PAYLOAD_TOO_LARGE,
                "Uploaded file exceeds the maximum allowed size.", request);
    }


    @ExceptionHandler(AuthenticationException.class)
    public void handleAuthenticationException(
            AuthenticationException ex,
            HttpServletRequest request) {
        log.warn("[Unauthorized] path={} | {}", request.getRequestURI(), ex.getMessage());
        throw ex;
    }

    @ExceptionHandler(AccessDeniedException.class)
    public void handleAccessDenied(
            AccessDeniedException ex,
            HttpServletRequest request) {
        log.warn("[Forbidden] path={} | {}", request.getRequestURI(), ex.getMessage());
        throw ex;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex,
                                                          HttpServletRequest request) {
        // Log full stack trace for unexpected errors
        log.error("[UnexpectedError] path={} | {}", request.getRequestURI(), ex.getMessage(), ex);
        return buildError(CommonErrorCode.INTERNAL_ERROR, null, request);
    }

    private ResponseEntity<ErrorResponse> buildError(ServiceErrorCode code,
                                                     String detail,
                                                     HttpServletRequest request) {
        return ResponseEntity
                .status(code.getHttpStatus())
                .body(ErrorResponse.of(code, request.getRequestURI(), detail));
    }

    private String extractFieldName(ConstraintViolation<?> cv) {
        String path = cv.getPropertyPath().toString();
        int dot = path.lastIndexOf('.');
        return dot >= 0 ? path.substring(dot + 1) : path;
    }
}
