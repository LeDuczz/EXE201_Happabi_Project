package com.minduc.happabi.dto.response.user;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserAttributeChangeResponse {

    private final boolean autoConfirmed;
    private final boolean verificationRequired;
}