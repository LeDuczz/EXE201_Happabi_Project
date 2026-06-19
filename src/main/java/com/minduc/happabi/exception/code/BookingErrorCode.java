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
    BOOKING_SLOT_INVALID(HttpStatus.UNPROCESSABLE_ENTITY, "Booking start time must be aligned to a one-hour slot."),
    NURSE_NOT_AVAILABLE(HttpStatus.CONFLICT, "Selected nurse is not available for booking."),
    NURSE_SKILL_NOT_ELIGIBLE(HttpStatus.CONFLICT, "Selected nurse does not have verified skills required for this service."),
    BOOKING_SLOT_ALREADY_BOOKED(HttpStatus.CONFLICT, "Selected booking slot was already booked."),
    BOOKING_CREATE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to create booking."),
    BOOKING_SETTLEMENT_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to settle booking."),
    BOOKING_NOT_FOUND(HttpStatus.NOT_FOUND, "Booking was not found."),
    BOOKING_ACCESS_DENIED(HttpStatus.FORBIDDEN, "Current user cannot access this booking."),
    BOOKING_CANCELLATION_NOT_ALLOWED(HttpStatus.CONFLICT, "Booking cannot be cancelled in its current state."),
    BOOKING_CANCELLATION_TOO_LATE(HttpStatus.CONFLICT, "Booking cancellation is no longer allowed by policy."),
    BOOKING_ALREADY_CANCELLED(HttpStatus.CONFLICT, "Booking has already been cancelled."),
    MOTHER_REFUND_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "Mother refund request was not found."),
    MOTHER_REFUND_REQUEST_NOT_PENDING(HttpStatus.CONFLICT, "Mother refund request is not pending."),
    MOTHER_REFUND_EVIDENCE_REQUIRED(HttpStatus.BAD_REQUEST, "Refund transfer evidence is required.");

    HttpStatus httpStatus;
    String message;
}

