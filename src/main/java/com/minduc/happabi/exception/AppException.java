package com.minduc.happabi.exception;

import com.minduc.happabi.exception.code.ServiceErrorCode;
import lombok.Getter;

@Getter
public class AppException extends RuntimeException {

    private final ServiceErrorCode errorCode;

    /** Basic – message taken from errorCode */
    public AppException(ServiceErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    /** With extra context detail appended to the message */
    public AppException(ServiceErrorCode errorCode, String detail) {
        super(errorCode.getMessage() + " — " + detail);
        this.errorCode = errorCode;
    }

    /** Wrapping a root cause */
    public AppException(ServiceErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }

    /** Full: detail + root cause */
    public AppException(ServiceErrorCode errorCode, String detail, Throwable cause) {
        super(errorCode.getMessage() + " — " + detail, cause);
        this.errorCode = errorCode;
    }
}
