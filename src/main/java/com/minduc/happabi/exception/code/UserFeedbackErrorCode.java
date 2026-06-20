package com.minduc.happabi.exception.code;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public enum UserFeedbackErrorCode implements ServiceErrorCode {
    USER_FEEDBACK_NOT_FOUND(HttpStatus.NOT_FOUND, "User feedback was not found."),
    USER_FEEDBACK_ACCESS_DENIED(HttpStatus.FORBIDDEN, "Current user cannot access this feedback."),
    USER_FEEDBACK_ROLE_NOT_ALLOWED(HttpStatus.FORBIDDEN, "Current role cannot submit feedback."),
    USER_FEEDBACK_INVALID_STATUS(HttpStatus.BAD_REQUEST, "Feedback status is invalid.");

    HttpStatus httpStatus;
    String message;
}
