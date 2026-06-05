package com.minduc.happabi.dto.response.booking;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.minduc.happabi.enums.BookingStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BookingDraftResponse {
    private UUID draftId;
    private UUID nurseProfileId;
    private String nurseName;
    private UUID serviceOfferingId;
    private String serviceName;
    private BookingStatus status;
    private OffsetDateTime startAt;
    private OffsetDateTime endAt;
    private OffsetDateTime holdExpiresAt;
    private Long grossAmount;
    private String serviceAddress;
    private String motherNote;
}
