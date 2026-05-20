package com.minduc.happabi.common.base;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.minduc.happabi.exception.code.ServiceErrorCode;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)  // omit null fields (e.g. errors on non-validation responses)
public class ErrorResponse {

    int status;
    String error;
    String message;
    String path;

    @Builder.Default
    Instant timestamp = Instant.now();

    List<FieldError> errors;

    public static ErrorResponse of(ServiceErrorCode errorCode, String path) {
        return ErrorResponse.builder()
                .status(errorCode.getHttpStatus().value())
                .error(errorCode.name())
                .message(errorCode.getMessage())
                .path(path)
                .build();
    }

    public static ErrorResponse of(ServiceErrorCode errorCode, String path, String detail) {
        return ErrorResponse.builder()
                .status(errorCode.getHttpStatus().value())
                .error(errorCode.name())
                .message(detail != null ? detail : errorCode.getMessage())
                .path(path)
                .build();
    }

    public static ErrorResponse ofValidation(ServiceErrorCode errorCode,
                                             String path,
                                             List<FieldError> fieldErrors) {
        return ErrorResponse.builder()
                .status(errorCode.getHttpStatus().value())
                .error(errorCode.name())
                .message(errorCode.getMessage())
                .path(path)
                .errors(fieldErrors)
                .build();
    }

    @Getter
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FieldError {
        String field;
        Object rejectedValue;
        String message;

        public static FieldError of(String field, String message) {
            return FieldError.builder().field(field).message(message).build();
        }

        public static FieldError of(String field, Object rejectedValue, String message) {
            return FieldError.builder().field(field).rejectedValue(rejectedValue).message(message).build();
        }
    }
}
