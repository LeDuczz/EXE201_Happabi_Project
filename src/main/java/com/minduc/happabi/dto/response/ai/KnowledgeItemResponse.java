package com.minduc.happabi.dto.response.ai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.minduc.happabi.enums.KnowledgeStatus;
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
public class KnowledgeItemResponse {

    private UUID id;

    private String question;

    private String answer;

    private String context;

    private String title;

    private String sourceType;

    private String sourceId;

    private String language;

    private KnowledgeStatus status;

    private Boolean vectorIndexed;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
