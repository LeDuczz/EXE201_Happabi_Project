package com.minduc.happabi.exception.code;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public enum NurseWalletErrorCode implements ServiceErrorCode {
    NURSE_WALLET_NOT_FOUND(HttpStatus.NOT_FOUND, "Nurse wallet not found"),
    CASH_BOOKING_ACCEPTANCE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Nurse booking acceptance error");
    ;
    HttpStatus httpStatus;
    String message;


}
