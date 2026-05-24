package com.minduc.happabi.dto.response.ai;

import com.fasterxml.jackson.annotation.JsonInclude;
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
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KnowledgeChunkResponse {

    private UUID id;

    private String title;

    private String question;

    private String answer;

    private String sourceType;

    private String sourceId;

    private String language;

    private Boolean verified;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
