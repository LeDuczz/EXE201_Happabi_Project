package com.minduc.happabi.dto.response.worksession;

import com.minduc.happabi.enums.WorkSessionChecklistStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class WorkSessionChecklistItemResponse {
    private UUID id;
    private String title;
    private Integer sortOrder;
    private WorkSessionChecklistStatus status;
    private OffsetDateTime completedAt;
    private String note;
    private List<WorkSessionEvidenceResponse> evidences;
}
