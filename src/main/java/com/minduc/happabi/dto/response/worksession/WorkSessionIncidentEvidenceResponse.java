package com.minduc.happabi.dto.response.worksession;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
public class WorkSessionIncidentEvidenceResponse {
    private UUID id;
    private String previewUrl;
    private String contentType;
    private Long fileSize;
    private OffsetDateTime createdAt;
}
