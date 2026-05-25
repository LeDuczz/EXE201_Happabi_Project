package com.minduc.happabi.exception.code;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum WalletTransactionErrorCode implements ServiceErrorCode {
    NURSE_ID_NOT_FOUND(HttpStatus.BAD_REQUEST, "Nurse Id not found in Wallet Transaction"),
    ;
    HttpStatus httpStatus;
    String message;
}
