package com.minduc.happabi.dto.response.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiChatResponse {

    private UUID conversationId;

    private AiMessageResponse userMessage;

    private AiMessageResponse assistantMessage;

    private String modelUsed;
}
