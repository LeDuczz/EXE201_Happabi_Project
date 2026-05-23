package com.minduc.happabi.dto.request.auth;

import com.minduc.happabi.enums.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank(message = "Phone number is required.")
    @Pattern(
            regexp = "^\\+84[3-9]\\d{8}$",
            message = "Phone number must follow Vietnam E.164 format, for example +84901234567."
    )
    private String phone;

    @NotBlank(message = "Password is required.")
    private String password;

    private UserRole portalRole;
}
