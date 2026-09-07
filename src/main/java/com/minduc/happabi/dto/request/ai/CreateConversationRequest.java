package com.minduc.happabi.dto.request.ai;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateConversationRequest {

    @Size(max = 160, message = "Title must be at most 160 characters.")
    private String title;
}
