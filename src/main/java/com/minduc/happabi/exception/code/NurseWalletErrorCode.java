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
    WITHDRAWAL_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "Withdrawal request not found"),
    WITHDRAWAL_AMOUNT_INVALID(HttpStatus.BAD_REQUEST, "Withdrawal amount is invalid"),
    WITHDRAWAL_BALANCE_INSUFFICIENT(HttpStatus.CONFLICT, "Nurse wallet balance is not enough for withdrawal"),
    WITHDRAWAL_REQUEST_NOT_PENDING(HttpStatus.CONFLICT, "Withdrawal request is not pending"),
    WITHDRAWAL_EVIDENCE_REQUIRED(HttpStatus.BAD_REQUEST, "Withdrawal transfer evidence is required"),
    WITHDRAWAL_BANK_ACCOUNT_REQUIRED(HttpStatus.BAD_REQUEST, "Active nurse bank account is required before withdrawal"),
    CASH_BOOKING_ACCEPTANCE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Nurse booking acceptance error"),
    DATA_WEBHOOK_ERROR(HttpStatus.BAD_REQUEST, "Data Webhook error"),

    ;
    HttpStatus httpStatus;
    String message;


}
