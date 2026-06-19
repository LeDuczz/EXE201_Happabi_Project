package com.minduc.happabi.dto.response.worksession;

import com.minduc.happabi.enums.WorkSessionIncidentStatus;
import com.minduc.happabi.enums.WorkSessionIncidentType;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class WorkSessionIncidentResponse {
    private UUID id;
    private UUID workSessionId;
    private WorkSessionIncidentType incidentType;
    private WorkSessionIncidentStatus status;
    private String description;
    private String reportedByName;
    private String adminNote;
    private OffsetDateTime reviewedAt;
    private OffsetDateTime createdAt;
    private List<WorkSessionIncidentEvidenceResponse> evidences;
}
