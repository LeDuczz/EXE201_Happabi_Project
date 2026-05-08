package com.minduc.happabi.dto.response;

import com.minduc.happabi.enums.AuthProvider;
import com.minduc.happabi.enums.UserRole;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class UserProfileResponse {

    private UUID id;

    private String cognitoSub;

    private String fullName;

    /** Present for LOCAL users; absent for social-only users. */
    private String phone;

    /** Present for social users; may be absent for pure LOCAL users. */
    private String email;

    private UserRole role;

    private AuthProvider authProvider;
    /**
     * Pre-signed S3 URL for the avatar, valid for 1 hour.
     * {@code null} if no avatar has been uploaded.
     */
    private String avatarUrl;

    private Boolean isActive;

}
