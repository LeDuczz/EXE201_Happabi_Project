package com.minduc.happabi.config;

import com.minduc.happabi.enums.UserRole;
import com.minduc.happabi.exception.AppException;
import com.minduc.happabi.exception.CustomAccessDeniedHandler;
import com.minduc.happabi.exception.CustomAuthenticationEntryPoint;
import com.minduc.happabi.exception.code.AuthErrorCode;
import com.minduc.happabi.filter.GlobalIpRateLimitFilter;
import com.minduc.happabi.filter.RateLimitFilter;
import com.minduc.happabi.filter.TokenBlacklistFilter;
import com.minduc.happabi.service.permission.PermissionCacheService;
import com.minduc.happabi.service.user.AuthenticatedUserIdentity;
import com.minduc.happabi.service.user.IUserIdentityService;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.cors.CorsConfigurationSource;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

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

    @Test
    void csrfIsIgnoredOnlyForPublicPostEndpointsThatDoNotUseRefreshCookie() {
        RequestMatcher[] matchers = ReflectionTestUtils.invokeMethod(SecurityConfig.class, "csrfIgnoredPostMatchers");

        assertThat(matchesAny(matchers, "POST", "/api/v1/auth/login")).isTrue();
        assertThat(matchesAny(matchers, "POST", "/api/v1/webhook/payos")).isTrue();
        assertThat(matchesAny(matchers, "POST", "/api/v1/auth/refresh")).isFalse();
        assertThat(matchesAny(matchers, "POST", "/api/v1/auth/logout")).isFalse();
        assertThat(matchesAny(matchers, "GET", "/api/v1/auth/login")).isFalse();
    }

    @Test
    void csrfCookieFilterExposesDeferredTokenBeforeContinuingChain() throws Exception {
        AtomicBoolean tokenRead = new AtomicBoolean(false);
        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/refresh");
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.setAttribute(CsrfToken.class.getName(), new CsrfToken() {
            @Override
            public String getHeaderName() {
                return "X-XSRF-TOKEN";
            }

            @Override
            public String getParameterName() {
                return "_csrf";
            }

            @Override
            public String getToken() {
                tokenRead.set(true);
                return "token";
            }
        });

        csrfCookieFilter().doFilter(request, response, (servletRequest, servletResponse) -> chainInvoked.set(true));

        assertThat(tokenRead).isTrue();
        assertThat(chainInvoked).isTrue();
    }

    @Test
    void filterRegistrationBeansDisableDirectServletContainerRegistration() {
        SecurityConfig config = securityConfig(mock(IUserIdentityService.class), mock(PermissionCacheService.class));

        assertThat(config.globalIpRateLimitFilterRegistration(mock(GlobalIpRateLimitFilter.class)).isEnabled()).isFalse();
        assertThat(config.rateLimitFilterRegistration(mock(RateLimitFilter.class)).isEnabled()).isFalse();
        assertThat(config.tokenBlacklistFilterRegistration(mock(TokenBlacklistFilter.class)).isEnabled()).isFalse();
    }

    private SecurityConfig securityConfig(IUserIdentityService identityService,
                                          PermissionCacheService permissionCacheService) {
        return new SecurityConfig(
                mock(CustomAuthenticationEntryPoint.class),
                mock(CustomAccessDeniedHandler.class),
                mock(CorsConfigurationSource.class),
                permissionCacheService,
                mock(GlobalIpRateLimitFilter.class),
                mock(RateLimitFilter.class),
                mock(TokenBlacklistFilter.class),
                identityService
        );
    }

    private boolean matchesAny(RequestMatcher[] matchers, String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        for (RequestMatcher matcher : matchers) {
            if (matcher.matches(request)) {
                return true;
            }
        }
        return false;
    }

    private Filter csrfCookieFilter() throws Exception {
        Class<?> filterClass = Class.forName("com.minduc.happabi.config.SecurityConfig$CsrfCookieFilter");
        Constructor<?> constructor = filterClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        return (Filter) constructor.newInstance();
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
