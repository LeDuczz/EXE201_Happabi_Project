package com.minduc.happabi.dto.response.nurse;

import com.minduc.happabi.enums.AvailabilityStatus;
import com.minduc.happabi.enums.NurseAvailabilityWindowStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
public class NurseAvailabilityWindowResponse {

    private UUID id;
    private OffsetDateTime startAt;
    private OffsetDateTime endAt;
    private NurseAvailabilityWindowStatus status;
    private AvailabilityStatus nurseAvailabilityStatus;
}
