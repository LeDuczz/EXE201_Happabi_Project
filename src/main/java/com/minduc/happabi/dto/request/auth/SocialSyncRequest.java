package com.minduc.happabi.dto.request.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SocialSyncRequest {

    @NotBlank(message = "code is required")
    private String code;

    @NotBlank(message = "redirectUri is required")
    private String redirectUri;

}
