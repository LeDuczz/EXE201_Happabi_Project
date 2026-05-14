package com.minduc.happabi.dto.request.auth;

import jakarta.validation.constraints.NotBlank;
import com.minduc.happabi.enums.AuthProvider;
import com.minduc.happabi.enums.SocialAuthIntent;
import lombok.Data;

@Data
public class SocialSyncRequest {

    @NotBlank(message = "code is required")
    private String code;

    @NotBlank(message = "redirectUri is required")
    private String redirectUri;

    private SocialAuthIntent intent = SocialAuthIntent.MOTHER_LOGIN_OR_REGISTER;

    private AuthProvider provider;

}
