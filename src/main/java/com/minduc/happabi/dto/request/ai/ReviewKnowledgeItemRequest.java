package com.minduc.happabi.dto.request.ai;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReviewKnowledgeItemRequest {

    @NotNull(message = "Approved flag is required.")
    private Boolean approved;
}
