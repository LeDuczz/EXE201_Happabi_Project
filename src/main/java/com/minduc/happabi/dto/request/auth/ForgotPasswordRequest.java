package com.minduc.happabi.dto.request.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ForgotPasswordRequest {

    @NotBlank(message = "Phone number is required.")
    @Pattern(
            regexp = "^\\+84[3-9]\\d{8}$",
            message = "Phone number must follow Vietnam E.164 format, for example +84901234567."
    )
    private String phone;
}
