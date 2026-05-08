package com.minduc.happabi.common.base;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BaseResponse<T> {

    @Builder.Default
    boolean success = true;

    String message;

    T data;

    @Builder.Default
    Instant timestamp = Instant.now();

    /** Success with data */
    public static <T> BaseResponse<T> ok(T data) {
        return BaseResponse.<T>builder()
                .message("Operation completed successfully")
                .data(data)
                .build();
    }

    /** Success with data + custom message */
    public static <T> BaseResponse<T> ok(String message, T data) {
        return BaseResponse.<T>builder()
                .message(message)
                .data(data)
                .build();
    }

    /** Success with message only (no data, e.g. DELETE, logout) */
    public static <T> BaseResponse<T> ok(String message) {
        return BaseResponse.<T>builder()
                .message(message)
                .build();
    }

    /** Created (201) response */
    public static <T> BaseResponse<T> created(T data) {
        return BaseResponse.<T>builder()
                .message("Resource created successfully")
                .data(data)
                .build();
    }

    /** Created (201) with custom message */
    public static <T> BaseResponse<T> created(String message, T data) {
        return BaseResponse.<T>builder()
                .message(message)
                .data(data)
                .build();
    }
}
