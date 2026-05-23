package com.minduc.happabi.exception.code;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public enum PaymentErrorCode implements ServiceErrorCode {
    FAIL_TO_CREATE_PAYMENT_LINK_FOR_NURSE(HttpStatus.INTERNAL_SERVER_ERROR, "Fail to create payment")
    ;
    HttpStatus httpStatus;
    String message;
}
