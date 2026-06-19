package com.minduc.happabi.dto.request.worksession;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReportWorkSessionIncidentRequest {

    @NotBlank(message = "Incident description is required.")
    @Size(max = 1500, message = "Incident description must be at most 1500 characters.")
    private String description;
}
