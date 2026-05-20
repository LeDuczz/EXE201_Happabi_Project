package com.minduc.happabi.dto.request.ai;

import jakarta.validation.constraints.Size;

public record CreateConversationRequest(
        @Size(max = 160, message = "Title must be at most 160 characters.")
        String title
) {
}
