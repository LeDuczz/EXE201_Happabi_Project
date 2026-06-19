package com.minduc.happabi.dto.request.admin;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReviewWorkSessionIncidentRequest {

    @Size(max = 500, message = "Admin note must be at most 500 characters.")
    private String adminNote;
}
