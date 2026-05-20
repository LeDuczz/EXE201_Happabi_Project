package com.minduc.happabi.dto.response.ai;

import com.minduc.happabi.enums.ChatRole;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AiMessageResponse(
        UUID id,
        ChatRole role,
        String content,
        String modelUsed,
        Integer inputTokens,
        Integer outputTokens,
        OffsetDateTime createdAt
) {
}
