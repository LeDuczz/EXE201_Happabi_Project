package com.minduc.happabi.config;

import com.minduc.happabi.exception.AppException;
import com.minduc.happabi.exception.CustomAccessDeniedHandler;
import com.minduc.happabi.exception.CustomAuthenticationEntryPoint;
import com.minduc.happabi.filter.GlobalIpRateLimitFilter;
import com.minduc.happabi.filter.RateLimitFilter;
import com.minduc.happabi.filter.TokenBlacklistFilter;
import com.minduc.happabi.service.permission.PermissionCacheService;
import com.minduc.happabi.service.user.AuthenticatedUserIdentity;
import com.minduc.happabi.service.user.IUserIdentityService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import com.minduc.happabi.config.security.UserContext;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomAuthenticationEntryPoint authEntryPoint;
    private final CustomAccessDeniedHandler accessDeniedHandler;
    private final CorsConfigurationSource corsConfigurationSource;
    private final PermissionCacheService permissionCacheService;
    private final GlobalIpRateLimitFilter globalIpRateLimitFilter;
    private final RateLimitFilter rateLimitFilter;
    private final TokenBlacklistFilter tokenBlacklistFilter;
    private final IUserIdentityService userIdentityService;

    private static final String[] PUBLIC_POST = {
            "/api/v1/auth/register",
            "/api/v1/auth/verify-otp",
            "/api/v1/auth/resend-otp",
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/auth/social/sync",
            "/api/v1/auth/forgot-password",
            "/api/v1/auth/reset-password",
            "/api/v1/webhook/payos",
    };

    private static final String[] PUBLIC_GET = {
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**",
            "/api-docs/**",
            "/actuator/health",
            "/actuator/prometheus", // Prometheus scraper không có token — bảo vệ ở network level
    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, PUBLIC_POST).permitAll()
                        .requestMatchers(HttpMethod.GET, PUBLIC_GET).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(cognitoJwtConverter()))
                        .authenticationEntryPoint(authEntryPoint))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(globalIpRateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(tokenBlacklistFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public Converter<Jwt, AbstractAuthenticationToken> cognitoJwtConverter() {
        return new Converter<Jwt, AbstractAuthenticationToken>() {
            @Override
            public AbstractAuthenticationToken convert(Jwt jwt) {
                String sub = jwt.getClaimAsString("sub");
                AuthenticatedUserIdentity identity = resolveIdentity(sub);
                Collection<GrantedAuthority> authorities = extractAuthorities(identity);
                UserContext principal = new UserContext(identity.userId(), jwt);

                return new UsernamePasswordAuthenticationToken(principal, null, authorities);
            }
        };
    }

    private AuthenticatedUserIdentity resolveIdentity(String cognitoSub) {
        if (cognitoSub == null || cognitoSub.isBlank()) {
            throw invalidToken("Missing subject claim.");
        }
        try {
            return userIdentityService.getActiveUserIdentity(cognitoSub);
        } catch (AppException exception) {
            throw invalidToken(exception.getMessage());
        }
    }

    private OAuth2AuthenticationException invalidToken(String description) {
        return new OAuth2AuthenticationException(new OAuth2Error("invalid_token"), description);
    }

    private Collection<GrantedAuthority> extractAuthorities(AuthenticatedUserIdentity identity) {
        Collection<GrantedAuthority> authorities = new ArrayList<>();
        for (var role : identity.roles()) {
            String roleName = role.name();
            authorities.add(new SimpleGrantedAuthority("ROLE_" + roleName));

            List<String> permissions = permissionCacheService.getPermissions(roleName);
            if (permissions != null) {
                for (String permission : permissions) {
                    authorities.add(new SimpleGrantedAuthority(permission));
                }
            }
        }

        return authorities;
    }
}
