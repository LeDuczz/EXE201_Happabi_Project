package com.minduc.happabi.dto.request.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class VerifyOtpRequest {

    @NotBlank(message = "Phone number is required.")
    @Pattern(
            regexp = "^\\+84[3-9]\\d{8}$",
            message = "Phone number must follow Vietnam E.164 format, for example +84901234567."
    )
    private String phone;

    @NotBlank(message = "OTP code is required.")
    @Pattern(regexp = "^\\d{6}$", message = "OTP code must contain exactly 6 digits.")
    private String otpCode;
}
