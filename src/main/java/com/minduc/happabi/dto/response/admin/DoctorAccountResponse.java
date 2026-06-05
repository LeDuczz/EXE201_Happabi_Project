package com.minduc.happabi.dto.response.admin;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.minduc.happabi.enums.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DoctorAccountResponse {

    private UUID userId;
    private String fullName;
    private String phone;
    private String email;
    private UserRole role;
    private String initialPassword;
}
