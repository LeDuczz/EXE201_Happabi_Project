package com.minduc.happabi.dto.request.ai;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateConversationRequest {

    @Size(max = 160, message = "Title must be at most 160 characters.")
    private String title;
}
