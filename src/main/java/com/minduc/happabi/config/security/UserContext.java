package com.minduc.happabi.config.security;

import org.springframework.security.oauth2.jwt.Jwt;
import java.util.UUID;

/**
 * Custom principal to hold both database userId and JWT information.
 */
public record UserContext(UUID userId, Jwt jwt) {

    @Override
    public String toString() {
        return userId != null ? userId.toString() : (jwt != null ? jwt.getSubject() : "");
    }

    public String getSub() {
        return jwt != null ? jwt.getClaimAsString("sub") : null;
    }
}
