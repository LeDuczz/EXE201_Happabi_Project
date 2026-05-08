package com.minduc.happabi.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuthResponse {

    private String accessToken;

    /** Absent on refresh responses (Cognito does not rotate the refresh token). */
    private String refreshToken;

    @Builder.Default
    private String tokenType = "Bearer";

    /** Token lifetime in seconds (from Cognito AuthenticationResult.expiresIn). */
    private Integer expiresIn;

    /** Full user profile; present only after a login, null on refresh. */
    private UserProfileResponse user;
}
