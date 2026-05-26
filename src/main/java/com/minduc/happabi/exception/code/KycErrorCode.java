package com.minduc.happabi.exception.code;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;

import static lombok.AccessLevel.PRIVATE;

@Getter
@RequiredArgsConstructor
@FieldDefaults(level = PRIVATE, makeFinal = true)
public enum KycErrorCode implements ServiceErrorCode {

    KYC_DOCUMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "KYC document not found.");

    HttpStatus httpStatus;
    String message;
}
