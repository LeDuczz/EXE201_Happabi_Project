package com.minduc.happabi.dto.request.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RequestPhoneChangeRequest {

    @NotBlank(message = "phone is required")
    @Pattern(regexp = "^\\+84[3-9]\\d{8}$", message = "Invalid Vietnamese phone number")
    private String phone;
}
