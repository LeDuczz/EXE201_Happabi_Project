package com.minduc.happabi.dto.response.auth;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RegisterResponse {

    private final boolean autoConfirmed;
    private final boolean otpRequired;
}