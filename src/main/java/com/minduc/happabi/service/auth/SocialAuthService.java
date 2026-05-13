package com.minduc.happabi.service.auth;

import com.minduc.happabi.common.utils.CookieUtils;
import com.minduc.happabi.dto.request.auth.SocialSyncRequest;
import com.minduc.happabi.dto.response.auth.AuthResponse;
import com.minduc.happabi.dto.response.user.UserProfileResponse;
import com.minduc.happabi.entity.Role;
import com.minduc.happabi.entity.User;
import com.minduc.happabi.enums.AuthProvider;
import com.minduc.happabi.enums.UserRole;
import com.minduc.happabi.exception.AppException;
import com.minduc.happabi.exception.code.AuthErrorCode;
import com.minduc.happabi.mapper.UserMapper;
import com.minduc.happabi.repository.RoleRepository;
import com.minduc.happabi.repository.UserIdentityProviderRepository;
import com.minduc.happabi.repository.UserRepository;
import com.minduc.happabi.service.metrics.AuthMetricsService;
import com.minduc.happabi.service.s3.S3Service;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.text.ParseException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SocialAuthService {

    private final UserRepository userRepository;
    private final UserIdentityProviderRepository identityProviderRepository;
    private final RoleRepository roleRepository;
    private final UserMapper userMapper;
    private final S3Service s3ServiceImpl;
    private final AuthMetricsService authMetrics;
    private final CognitoService cognitoService;
    private final UserProviderService userProviderService;

    @Transactional
    public AuthResponse socialSync(SocialSyncRequest request, HttpServletResponse response) {
        try {
            ResponseEntity<Map> tokenResponse = cognitoService.exchangeCodeForTokens(request);
            Map<String, Object> tokens = tokenResponse.getBody();
            if (tokens == null || !tokens.containsKey("id_token")) {
                throw new AppException(AuthErrorCode.INVALID_CREDENTIALS, "Failed to retrieve tokens from Cognito");
            }

            String idToken = (String) tokens.get("id_token");
            String accessToken = (String) tokens.get("access_token");
            String refreshToken = (String) tokens.get("refresh_token");
            Integer expiresIn = (Integer) tokens.get("expires_in");

            SignedJWT signedJWT = SignedJWT.parse(idToken);
            String cognitoSub = signedJWT.getJWTClaimsSet().getStringClaim("sub");
            String email = signedJWT.getJWTClaimsSet().getStringClaim("email");
            String name = signedJWT.getJWTClaimsSet().getStringClaim("name");
            String username = signedJWT.getJWTClaimsSet().getStringClaim("cognito:username");
            AuthProvider provider = resolveProvider(signedJWT);

            Role motherRole = roleRepository.findByRoleName(UserRole.MOTHER)
                    .orElseThrow(() -> new AppException(AuthErrorCode.AUTH_FAILED, "Role MOTHER not found"));

            User user = identityProviderRepository
                    .findUserByProviderAndProviderUid(provider, cognitoSub)
                    .orElseGet(() -> resolveOrCreateSocialUser(
                            cognitoSub, email, name, provider));

            user.getRoles().forEach(role -> assignCognitoGroup(cognitoSub, role.getRoleName().name()));

            boolean isMother = user.getRoles().stream().anyMatch(r -> r.getRoleName() == UserRole.MOTHER);
            if (!isMother) {
                userProviderService.saveRoleAssignment(user, motherRole);
                userProviderService.createProfileForRole(user, UserRole.MOTHER);
                assignCognitoGroup(cognitoSub, UserRole.MOTHER.name());
            }

            user.setLastLoginAt(OffsetDateTime.now());
            userRepository.save(user);

            CookieUtils.addRefreshTokenCookie(response, refreshToken + "::" + username);

            String avatarUrl = s3ServiceImpl.presign(user.getAvatarS3Key());
            UserProfileResponse profile = userMapper.toProfileResponse(user, avatarUrl);

            log.info("[Auth] Social sync ok: sub={} provider={}", cognitoSub, provider);
            authMetrics.recordSocialLoginSuccess(provider.name());

            return AuthResponse.builder()
                    .accessToken(accessToken)
                    .expiresIn(expiresIn)
                    .user(profile)
                    .build();

        } catch (HttpClientErrorException e) {
            log.error("[Auth] Cognito token exchange failed: {}", e.getResponseBodyAsString());
            throw new AppException(AuthErrorCode.INVALID_CREDENTIALS, "Invalid code or redirect_uri");
        } catch (ResourceAccessException e) {
            log.error("[Auth] Cognito token exchange timeout/network error: {}", e.getMessage(), e);
            throw new AppException(AuthErrorCode.INVALID_CREDENTIALS,
                    "Cannot connect to Cognito token endpoint. Please try again.");
        } catch (ParseException e) {
            log.error("Failed to parse idToken", e);
            throw new AppException(AuthErrorCode.INVALID_CREDENTIALS, "Invalid idToken from Cognito");
        }
    }

    private User resolveOrCreateSocialUser(String cognitoSub, String email, String name,
                                           AuthProvider provider) {

        Optional<User> byEmail = (email != null)
                ? userRepository.findByEmailWithRolesAndProviders(email)
                : Optional.empty();

        if (byEmail.isPresent()) {
            User existing = byEmail.get();

            if (!identityProviderRepository.existsByUserAndProvider(existing, provider)) {
                userProviderService.saveIdentityProvider(existing, provider, cognitoSub);
                log.info("[Auth] Account linked via email: userId={} email={} -> {} added",
                        existing.getId(), email, provider);
            }
            return existing;
        }

        User newUser = User.builder()
                .fullName(name != null ? name : "")
                .email(email)
                .isActive(true)
                .build();
        userRepository.save(newUser);

        userProviderService.saveIdentityProvider(newUser, provider, cognitoSub);

        log.info("[Auth] Social new user: sub={} provider={} email={}", cognitoSub, provider, email);
        return newUser;
    }

    private AuthProvider resolveProvider(SignedJWT jwt) throws ParseException {
        try {
            List<?> identities = (List<?>) jwt.getJWTClaimsSet().getClaim("identities");
            if (identities != null && !identities.isEmpty()) {
                Object first = identities.get(0);
                if (first instanceof Map<?, ?> map) {
                    Object providerName = map.get("providerName");
                    if (providerName != null) {
                        return mapProvider(providerName.toString());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[Auth] Failed to parse identities claim", e);
        }

        String username = jwt.getJWTClaimsSet().getStringClaim("username");
        if (username == null) {
            username = jwt.getJWTClaimsSet().getStringClaim("cognito:username");
        }
        if (username != null) {
            if (username.toLowerCase().startsWith("google_")) {
                return AuthProvider.GOOGLE;
            }
            if (username.toLowerCase().startsWith("facebook_")) {
                return AuthProvider.FACEBOOK;
            }
        }

        return AuthProvider.LOCAL;
    }

    private AuthProvider mapProvider(String providerName) {
        return switch (providerName.toLowerCase()) {
            case "google" -> AuthProvider.GOOGLE;
            case "facebook" -> AuthProvider.FACEBOOK;
            default -> AuthProvider.LOCAL;
        };
    }

    private void assignCognitoGroup(String username, String roleName) {
        try {
            cognitoService.adminAddUserToGroup(username, roleName);
            log.info("[Cognito] Added user {} to group {}", username, roleName);
        } catch (Exception e) {
            log.error("[Cognito] Failed to add user {} to group {}: {}", username, roleName, e.getMessage());
            throw new AppException(AuthErrorCode.AUTH_FAILED,
                    "Failed to assign Cognito group: " + roleName);
        }
    }
}
