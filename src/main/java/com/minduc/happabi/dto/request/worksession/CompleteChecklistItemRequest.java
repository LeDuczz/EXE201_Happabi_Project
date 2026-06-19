package com.minduc.happabi.dto.request.worksession;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CompleteChecklistItemRequest {

    @Size(max = 1000, message = "Note must not exceed 1000 characters")
    private String note;
}
