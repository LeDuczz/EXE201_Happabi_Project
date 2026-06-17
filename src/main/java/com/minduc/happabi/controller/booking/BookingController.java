package com.minduc.happabi.controller.booking;

import com.minduc.happabi.common.base.BaseResponse;
import com.minduc.happabi.dto.request.booking.CreateBookingRequest;
import com.minduc.happabi.dto.response.booking.BookingResponse;
import com.minduc.happabi.service.booking.IBookingService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
@Tag(name = "Bookings", description = "Mother booking flow")
@SecurityRequirement(name = "bearerAuth")
public class BookingController {

    private final IBookingService bookingService;

    @PostMapping
    @PreAuthorize("hasRole('MOTHER')")
    public ResponseEntity<BaseResponse<BookingResponse>> createBooking(
            @Valid @RequestBody CreateBookingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.created("Booking created and waiting for payment.", bookingService.createBooking(request)));
    }

    @GetMapping("/me/pending-payments")
    @PreAuthorize("hasRole('MOTHER')")
    public ResponseEntity<BaseResponse<List<BookingResponse>>> getMyPendingPayments() {
        return ResponseEntity.ok(BaseResponse.ok(bookingService.getMyPendingPayments()));
    }
}

