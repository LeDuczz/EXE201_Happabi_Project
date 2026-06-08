package com.minduc.happabi.dto.response.worksession;

import com.minduc.happabi.enums.WorkSessionEvidenceStatus;
import com.minduc.happabi.enums.WorkSessionEvidenceType;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
public class WorkSessionEvidenceResponse {
    private UUID id;
    private WorkSessionEvidenceType evidenceType;
    private WorkSessionEvidenceStatus status;
    private String previewUrl;
    private String contentType;
    private Long fileSize;
    private OffsetDateTime createdAt;
}
