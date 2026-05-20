package com.minduc.happabi.dto.response.ai;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ConversationResponse(
        UUID id,
        UUID userId,
        String title,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
