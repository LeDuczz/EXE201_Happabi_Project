package com.minduc.happabi.controller.booking;

import com.minduc.happabi.common.base.BaseResponse;
import com.minduc.happabi.dto.request.booking.CreateBookingDraftRequest;
import com.minduc.happabi.dto.response.booking.BookingDraftResponse;
import com.minduc.happabi.service.booking.IBookingService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
@Tag(name = "Bookings", description = "Mother booking draft and hold flow")
@SecurityRequirement(name = "bearerAuth")
public class BookingController {

    private final IBookingService bookingService;

    @PostMapping("/drafts")
    @PreAuthorize("hasRole('MOTHER')")
    public ResponseEntity<BaseResponse<BookingDraftResponse>> createDraft(
            @Valid @RequestBody CreateBookingDraftRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.created("Booking draft created and slot held.", bookingService.createDraft(request)));
    }
}
