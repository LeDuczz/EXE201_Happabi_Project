package com.minduc.happabi.config;


import com.minduc.happabi.enums.UserRole;
import com.minduc.happabi.exception.CustomAccessDeniedHandler;
import com.minduc.happabi.exception.CustomAuthenticationEntryPoint;
import com.minduc.happabi.filter.GlobalIpRateLimitFilter;
import com.minduc.happabi.filter.RateLimitFilter;
import com.minduc.happabi.service.permission.PermissionCacheService;
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
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.*;
import java.util.stream.Collectors;

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

    private static final String[] PUBLIC_POST = {
            "/api/v1/auth/register",
            "/api/v1/auth/verify-otp",
            "/api/v1/auth/resend-otp",
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/auth/social/sync",
            "/api/v1/auth/forgot-password",
            "/api/v1/auth/reset-password",
    };

    private static final String[] PUBLIC_GET = {
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**",
            "/api-docs/**",
            "/actuator/health",
            "/actuator/prometheus",   // Prometheus scraper không có token — bảo vệ ở network level
    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .sessionManagement(sm ->
                sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.POST, PUBLIC_POST).permitAll()
                .requestMatchers(HttpMethod.GET,  PUBLIC_GET).permitAll()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(cognitoJwtConverter()))
                .authenticationEntryPoint(authEntryPoint)
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(authEntryPoint)
                .accessDeniedHandler(accessDeniedHandler)
            )
            // Thứ tự filter: GlobalIpRateLimit (Order=1) → RateLimit (Order=2) → Security
            .addFilterBefore(globalIpRateLimitFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter cognitoJwtConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();

        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Collection<GrantedAuthority> authorities = new ArrayList<>();

            List<String> groups = jwt.getClaimAsStringList("cognito:groups");

            Set<String> validRoles = Arrays.stream(UserRole.values())
                    .map(Enum::name)
                    .collect(Collectors.toSet());

            String roleName = "MOTHER";
            if (groups != null) {
                roleName = groups.stream()
                        .map(String::toUpperCase)
                        .filter(validRoles::contains)
                        .findFirst()
                        .orElse("MOTHER");
            }

            // ── 2. Thêm ROLE_XXX authority ────────────────────────────────────
            authorities.add(new SimpleGrantedAuthority("ROLE_" + roleName));

            // ── 3. Tra cứu Permissions từ Redis/DB ───────────────────────────
            List<String> permissions = permissionCacheService.getPermissions(roleName);
            for (String permission : permissions) {
                authorities.add(new SimpleGrantedAuthority(permission));
            }

            return authorities;
        });

        converter.setPrincipalClaimName("sub");
        return converter;
    }
}
