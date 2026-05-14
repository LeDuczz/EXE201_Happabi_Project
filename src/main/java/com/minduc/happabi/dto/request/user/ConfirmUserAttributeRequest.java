package com.minduc.happabi.dto.request.user;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ConfirmUserAttributeRequest {

    @NotBlank(message = "code is required")
    private String code;
}
