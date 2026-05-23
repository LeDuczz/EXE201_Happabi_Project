package com.minduc.happabi.dto.response.ai;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AiChatResponse {

    private UUID conversationId;

    private AiMessageResponse userMessage;

    private AiMessageResponse assistantMessage;

    private String modelUsed;

    private String resolutionSource;

    private Double ragScore;

    private Boolean pendingReviewCreated;
}
