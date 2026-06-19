package com.minduc.happabi.dto.request.booking;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CancelBookingRequest {

    @NotBlank(message = "Cancellation reason is required.")
    @Size(max = 1000, message = "Cancellation reason must be at most 1000 characters.")
    private String reason;
}
