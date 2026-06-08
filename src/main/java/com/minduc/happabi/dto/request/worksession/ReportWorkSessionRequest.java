package com.minduc.happabi.dto.request.worksession;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReportWorkSessionRequest {

    @NotBlank(message = "Report reason is required")
    @Size(max = 2000, message = "Report reason must not exceed 2000 characters")
    private String reason;
}
