package com.minduc.happabi.dto.request.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RequestEmailChangeRequest {

    @NotBlank(message = "email is required")
    @Email(message = "Invalid email")
    @Size(max = 150, message = "Email must not exceed 150 characters")
    private String email;
}
