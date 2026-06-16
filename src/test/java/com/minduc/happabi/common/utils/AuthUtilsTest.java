package com.minduc.happabi.common.utils;

import com.minduc.happabi.config.security.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AuthUtilsTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getCurrentSubReadsSubFromUserContextJwt() {
        Jwt jwt = jwt(Map.of("sub", "cognito-sub", "email", "user@example.com"));
        UserContext principal = new UserContext(UUID.randomUUID(), jwt);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of(new SimpleGrantedAuthority("ROLE_NURSE"))));

        assertThat(AuthUtils.getCurrentSub()).contains("cognito-sub");
        assertThat(AuthUtils.getCurrentEmail()).contains("user@example.com");
        assertThat(AuthUtils.hasRole("nurse")).isTrue();
    }

    @Test
    void getJwtReturnsEmptyWhenAuthenticationIsMissingOrUnauthenticated() {
        assertThat(AuthUtils.getJwt()).isEmpty();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("principal", null, List.of()));

        assertThat(AuthUtils.getJwt()).isEmpty();
    }

    private Jwt jwt(Map<String, Object> claims) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .claims(map -> map.putAll(claims))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
    }
}
