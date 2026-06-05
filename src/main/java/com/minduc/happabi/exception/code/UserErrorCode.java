package com.minduc.happabi.exception.code;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public enum UserErrorCode implements ServiceErrorCode {

    MOTHER_PROFILE_NOT_FOUND(HttpStatus.UNAUTHORIZED, "Mother profile not found for the current user."),
    NURSE_PROFILE_NOT_FOUND(HttpStatus.UNAUTHORIZED, "Nurse profile not found for the current user."),
    PHONE_ALREADY_SET(HttpStatus.BAD_REQUEST, "Phone cannot be changed after it has been set."),
    EMAIL_ALREADY_SET(HttpStatus.BAD_REQUEST, "Email cannot be changed after it has been set.");

    HttpStatus httpStatus;
    String message;

}
