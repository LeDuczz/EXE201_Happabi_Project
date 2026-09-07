package com.minduc.happabi.dto.request.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SendAiMessageRequest {

    @NotBlank(message = "Message is required.")
    @Size(max = 8000, message = "Message must be at most 8000 characters.")
    private String message;
}
