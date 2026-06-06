package com.minduc.happabi.exception.code;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public enum BookingErrorCode implements ServiceErrorCode {
    SERVICE_OFFERING_NOT_FOUND(HttpStatus.NOT_FOUND, "Service offering was not found or is inactive."),
    NURSE_NOT_AVAILABLE(HttpStatus.CONFLICT, "Selected nurse is not available for booking."),
    NURSE_SKILL_NOT_ELIGIBLE(HttpStatus.CONFLICT, "Selected nurse does not have verified skills required for this service."),
    BOOKING_SLOT_ALREADY_HELD(HttpStatus.CONFLICT, "Selected booking slot is currently held by another user."),
    BOOKING_SLOT_ALREADY_BOOKED(HttpStatus.CONFLICT, "Selected booking slot was already booked."),
    BOOKING_DRAFT_CREATE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to create booking draft.");

    HttpStatus httpStatus;
    String message;
}
