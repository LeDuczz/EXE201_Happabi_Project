package com.minduc.happabi.config;

import com.minduc.happabi.enums.UserRole;
import com.minduc.happabi.exception.AppException;
import com.minduc.happabi.exception.code.AuthErrorCode;
import com.minduc.happabi.service.permission.PermissionCacheService;
import com.minduc.happabi.service.user.AuthenticatedUserIdentity;
import com.minduc.happabi.service.user.IUserIdentityService;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SecurityConfigTest {

    @Test
    void usesDatabaseRolesInsteadOfJwtGroups() {
        IUserIdentityService identityService = mock(IUserIdentityService.class);
        PermissionCacheService permissionCacheService = mock(PermissionCacheService.class);
        UUID userId = UUID.randomUUID();
        when(identityService.getActiveUserIdentity("subject"))
                .thenReturn(new AuthenticatedUserIdentity(userId, Set.of(UserRole.NURSE)));
        when(permissionCacheService.getPermissions("NURSE")).thenReturn(List.of("NURSE:READ"));

        var authentication = securityConfig(identityService, permissionCacheService)
                .cognitoJwtConverter()
                .convert(jwtWithGroups("MOTHER"));

        assertThat(authentication.getAuthorities())
                .extracting(authority -> authority.getAuthority())
                .containsExactlyInAnyOrder("ROLE_NURSE", "NURSE:READ");
    }

    @Test
    void rejectsDisabledAccountBeforeCreatingAuthentication() {
        IUserIdentityService identityService = mock(IUserIdentityService.class);
        when(identityService.getActiveUserIdentity("subject"))
                .thenThrow(new AppException(AuthErrorCode.ACCOUNT_DISABLED));

        assertThatThrownBy(() -> securityConfig(identityService, mock(PermissionCacheService.class))
                .cognitoJwtConverter()
                .convert(jwtWithGroups("ADMIN")))
                .isInstanceOf(OAuth2AuthenticationException.class);
    }

    private SecurityConfig securityConfig(IUserIdentityService identityService,
                                          PermissionCacheService permissionCacheService) {
        return new SecurityConfig(
                mock(com.minduc.happabi.exception.CustomAuthenticationEntryPoint.class),
                mock(com.minduc.happabi.exception.CustomAccessDeniedHandler.class),
                mock(org.springframework.web.cors.CorsConfigurationSource.class),
                permissionCacheService,
                mock(com.minduc.happabi.filter.GlobalIpRateLimitFilter.class),
                mock(com.minduc.happabi.filter.RateLimitFilter.class),
                mock(com.minduc.happabi.filter.TokenBlacklistFilter.class),
                identityService
        );
    }

    private Jwt jwtWithGroups(String group) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("subject")
                .claim("cognito:groups", List.of(group))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
    }
}
