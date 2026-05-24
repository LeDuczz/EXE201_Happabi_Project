package com.minduc.happabi.dto.response.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.minduc.happabi.dto.response.user.UserProfileResponse;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthResponse {

    private String accessToken;

    @Builder.Default
    private String tokenType = "Bearer";

    private Integer expiresIn;

    private UserProfileResponse user;
}
