package com.minduc.happabi.dto.user;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
public class UserProfileResponse {

    private UUID userId;
    private String cognitoSub;
    private String fullName;
    private String email;
    private String phone;
    private String role;
    private String authProvider;
    private String avatarUrl;       // pre-signed URL or null
    private Boolean isActive;
    private OffsetDateTime lastLoginAt;
    private OffsetDateTime createdAt;
}
