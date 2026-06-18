package com.minduc.happabi.dto.request.nurse;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
public class CreateNurseAvailabilityWindowRequest {

    @NotNull(message = "Start time is required")
    private OffsetDateTime startAt;

    @NotNull(message = "End time is required")
    private OffsetDateTime endAt;
}
