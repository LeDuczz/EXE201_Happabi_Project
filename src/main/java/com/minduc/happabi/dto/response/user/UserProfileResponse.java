package com.minduc.happabi.dto.response.user;

import com.minduc.happabi.enums.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileResponse {

    private UUID id;

    private String fullName;

    private String phone;

    private Boolean phoneVerified;

    private String email;

    private Boolean emailVerified;

    private List<UserRole> roles;

    private List<String> linkedProviders;

    private String avatarUrl;

    private Boolean isActive;
}
