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
    FAIL_TO_CREATE_PAYMENT_LINK_FOR_NURSE(HttpStatus.INTERNAL_SERVER_ERROR, "Fail to create payment"),
    BOOKING_PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "Booking payment was not found."),
    BOOKING_PAYMENT_NOT_PAYABLE(HttpStatus.CONFLICT, "Booking is not payable."),
    BOOKING_PAYMENT_EXPIRED(HttpStatus.CONFLICT, "Booking payment has expired."),
    BOOKING_PAYMENT_AMOUNT_INVALID(HttpStatus.CONFLICT, "Booking payment amount is invalid.")
    ;
    HttpStatus httpStatus;
    String message;
}
