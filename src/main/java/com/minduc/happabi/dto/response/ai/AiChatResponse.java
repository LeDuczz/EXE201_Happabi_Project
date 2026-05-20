package com.minduc.happabi.dto.response.ai;

import java.util.UUID;

public record AiChatResponse(
        UUID conversationId,
        AiMessageResponse userMessage,
        AiMessageResponse assistantMessage,
        String modelUsed
) {
}
