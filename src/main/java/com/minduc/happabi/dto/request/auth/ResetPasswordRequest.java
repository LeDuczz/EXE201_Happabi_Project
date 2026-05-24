package com.minduc.happabi.dto.request.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResetPasswordRequest {

    @NotBlank(message = "Phone number is required.")
    @Pattern(
            regexp = "^\\+84[3-9]\\d{8}$",
            message = "Phone number must follow Vietnam E.164 format, for example +84901234567."
    )
    private String phone;

    @NotBlank(message = "OTP code is required.")
    private String otpCode;

    @NotBlank(message = "New password is required.")
    @Size(min = 8, message = "Password must be at least 8 characters.")
    private String newPassword;
}
