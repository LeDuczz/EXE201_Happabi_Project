package com.minduc.happabi.exception.code;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public enum NurseReviewErrorCode implements ServiceErrorCode {
    NURSE_REVIEW_NOT_FOUND(HttpStatus.NOT_FOUND, "Nurse review was not found."),
    NURSE_REVIEW_ALREADY_EXISTS(HttpStatus.CONFLICT, "This work session has already been reviewed."),
    NURSE_REVIEW_INVALID_SESSION_STATE(HttpStatus.CONFLICT, "Only completed work sessions can be reviewed."),
    NURSE_REVIEW_RATING_REQUIRED(HttpStatus.UNPROCESSABLE_ENTITY, "Rating is required.");

    HttpStatus httpStatus;
    String message;
}
