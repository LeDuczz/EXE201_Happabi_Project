package com.minduc.happabi.dto.response.worksession;

import com.minduc.happabi.enums.WorkSessionStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class WorkSessionResponse {
    private UUID id;
    private UUID bookingId;
    private UUID nurseProfileId;
    private String nurseName;
    private UUID motherId;
    private String motherName;
    private String motherPhone;
    private UUID serviceOfferingId;
    private String serviceName;
    private String serviceAddress;
    private WorkSessionStatus status;
    private OffsetDateTime scheduledStartAt;
    private OffsetDateTime scheduledEndAt;
    private OffsetDateTime checkedInAt;
    private Integer lateMinutes;
    private OffsetDateTime checkedOutAt;
    private OffsetDateTime autoConfirmAt;
    private OffsetDateTime confirmedAt;
    private OffsetDateTime reportedAt;
    private String reportReason;
    private List<WorkSessionEvidenceResponse> checkInEvidences;
    private List<WorkSessionChecklistItemResponse> checklistItems;
}
