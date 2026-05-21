package com.minduc.happabi.dto.response.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeChunkResponse {

    private UUID id;

    private String title;

    private String sourceType;

    private String sourceId;

    private String language;

    private Boolean verified;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
