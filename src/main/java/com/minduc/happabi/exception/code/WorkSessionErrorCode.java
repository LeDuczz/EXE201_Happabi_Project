package com.minduc.happabi.exception.code;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public enum WorkSessionErrorCode implements ServiceErrorCode {
    WORK_SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "Work session was not found."),
    WORK_SESSION_ACCESS_DENIED(HttpStatus.FORBIDDEN, "Current user cannot access this work session."),
    WORK_SESSION_INVALID_STATE(HttpStatus.CONFLICT, "Work session is not in a valid state for this action."),
    WORK_SESSION_CHECK_IN_TOO_EARLY(HttpStatus.CONFLICT, "Check-in is not open yet."),
    WORK_SESSION_CHECKLIST_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "Checklist item was not found."),
    WORK_SESSION_CHECKLIST_INCOMPLETE(HttpStatus.CONFLICT, "All checklist items must be completed before checkout."),
    WORK_SESSION_EVIDENCE_REQUIRED(HttpStatus.UNPROCESSABLE_ENTITY, "At least one evidence image is required."),
    WORK_SESSION_EVIDENCE_UPLOAD_FAILED(HttpStatus.BAD_GATEWAY, "Failed to upload work session evidence."),
    WORK_SESSION_ALREADY_EXISTS(HttpStatus.CONFLICT, "Work session already exists for this booking.");

    HttpStatus httpStatus;
    String message;
}
