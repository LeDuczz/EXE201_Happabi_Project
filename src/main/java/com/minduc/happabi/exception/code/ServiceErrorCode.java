package com.minduc.happabi.exception.code;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

public interface ServiceErrorCode {
    /** Unique error code string, e.g. "USER_NOT_FOUND" */
    String name();

    /** HTTP status to return */
    HttpStatus getHttpStatus();

    /** Human-readable message for the client */
    String getMessage();

    /** Convenience: HTTP status code value */
    default HttpStatusCode getCode() {
        return getHttpStatus();
    }
}
