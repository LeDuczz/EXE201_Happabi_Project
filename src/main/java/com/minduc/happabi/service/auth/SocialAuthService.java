package com.minduc.happabi.service.auth;

import com.minduc.happabi.common.utils.CookieUtils;
import com.minduc.happabi.dto.request.auth.SocialSyncRequest;
import com.minduc.happabi.dto.response.auth.AuthResponse;
import com.minduc.happabi.dto.response.user.UserProfileResponse;
import com.minduc.happabi.entity.Role;
import com.minduc.happabi.entity.User;
import com.minduc.happabi.enums.AuthProvider;
import com.minduc.happabi.enums.SocialAuthIntent;
import com.minduc.happabi.enums.UserRole;
import com.minduc.happabi.exception.AppException;
import com.minduc.happabi.exception.code.AuthErrorCode;
import com.minduc.happabi.integration.cognito.CognitoService;
import com.minduc.happabi.mapper.UserMapper;
import com.minduc.happabi.observability.annotation.AuditAction;
import com.minduc.happabi.observability.annotation.LogExecution;
import com.minduc.happabi.observability.annotation.TimedAction;
import com.minduc.happabi.repository.RoleRepository;
import com.minduc.happabi.repository.UserIdentityProviderRepository;
import com.minduc.happabi.repository.UserRepository;
import com.minduc.happabi.integration.s3.IS3Service;
import com.minduc.happabi.service.user.UserCacheService;
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
import java.util.EnumMap;
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
    private final IS3Service s3ServiceImpl;
    private final CognitoService cognitoService;
    private final UserProviderService userProviderService;
    private final UserCacheService userCacheService;

    @TimedAction("SOCIAL_SYNC")
    @LogExecution
    @AuditAction(action = "SOCIAL_SYNC", resourceType = "USER_SESSION")
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
            if (accessToken == null || accessToken.isBlank()
                    || refreshToken == null || refreshToken.isBlank()) {
                throw new AppException(AuthErrorCode.INVALID_CREDENTIALS,
                        "Failed to retrieve access or refresh token from Cognito");
            }

            SignedJWT signedJWT = SignedJWT.parse(idToken);
            String cognitoSub = signedJWT.getJWTClaimsSet().getStringClaim("sub");
            String email = signedJWT.getJWTClaimsSet().getStringClaim("email");
            String name = signedJWT.getJWTClaimsSet().getStringClaim("name");
            String username = signedJWT.getJWTClaimsSet().getStringClaim("cognito:username");
            boolean emailVerified = readBooleanClaim(signedJWT, "email_verified");
            String refreshUsername = resolveRefreshUsername(accessToken, username, cognitoSub);
            AuthProvider provider = request.getProvider() != null
                    ? request.getProvider()
                    : resolveProvider(signedJWT);
            Map<AuthProvider, String> linkedIdentities = resolveLinkedIdentitySubjects(signedJWT);
            String providerSubject = resolveProviderSubject(signedJWT, provider)
                    .orElse(cognitoSub);

            Role motherRole = roleRepository.findByRoleName(UserRole.MOTHER)
                    .orElseThrow(() -> new AppException(AuthErrorCode.AUTH_FAILED, "Role MOTHER not found"));

            User user = userRepository.findByCognitoSubWithRolesAndProviders(cognitoSub)
                    .or(() -> identityProviderRepository
                            .findUserByProviderAndProviderUid(provider, providerSubject))
                    .orElseGet(() -> resolveOrCreateSocialUser(
                            cognitoSub, refreshUsername, providerSubject, email, name, provider));

            syncMasterCognitoFields(user, cognitoSub, refreshUsername);
            syncVerifiedEmail(user, emailVerified);
            syncIdentityProviders(user, linkedIdentities, provider, providerSubject);

            String groupUsername = refreshUsername;
            user.getRoles().forEach(role -> assignCognitoGroup(groupUsername, role.getRoleName().name()));

            boolean shouldEnsureMotherRole = request.getIntent() == SocialAuthIntent.MOTHER_LOGIN_OR_REGISTER;
            boolean isMother = user.getRoles().stream().anyMatch(r -> r.getRoleName() == UserRole.MOTHER);
            if (shouldEnsureMotherRole && !isMother) {
                userProviderService.saveRoleAssignment(user, motherRole);
                userProviderService.createProfileForRole(user, UserRole.MOTHER);
                assignCognitoGroup(groupUsername, UserRole.MOTHER.name());
            }

            user.setLastLoginAt(OffsetDateTime.now());
            userRepository.save(user);
            userCacheService.evictProfiles(cognitoSub);

            CookieUtils.addRefreshTokenCookie(response, refreshToken + "::" + refreshUsername);

            String avatarUrl = s3ServiceImpl.presign(user.getAvatarS3Key());
            UserProfileResponse profile = userMapper.toProfileResponse(user, avatarUrl);

            log.info("[Auth] Social sync ok: sub={} provider={} emailVerified={}", cognitoSub, provider, emailVerified);

            return AuthResponse.builder()
                    .accessToken(accessToken)
                    .expiresIn(expiresIn)
                    .user(profile)
                    .build();

        } catch (HttpClientErrorException e) {
            throw new AppException(AuthErrorCode.INVALID_CREDENTIALS, "Invalid code or redirect_uri");
        } catch (ResourceAccessException e) {
            throw new AppException(AuthErrorCode.INVALID_CREDENTIALS,
                    "Cannot connect to Cognito token endpoint. Please try again.");
        } catch (ParseException e) {
            throw new AppException(AuthErrorCode.INVALID_CREDENTIALS, "Invalid idToken from Cognito");
        }
    }

    private User resolveOrCreateSocialUser(String cognitoSub, String username, String providerSubject,
                                           String email, String name, AuthProvider provider) {

        Optional<User> byEmail = (email != null)
                ? userRepository.findByEmailWithRolesAndProviders(email)
                : Optional.empty();

        if (byEmail.isPresent()) {
            User existing = byEmail.get();

            if (!identityProviderRepository.existsByUserAndProvider(existing, provider)) {
                userProviderService.saveIdentityProvider(existing, provider, providerSubject);
                log.info("[Auth] Account linked via email: userId={} email={} -> {} added",
                        existing.getId(), email, provider);
            }
            return existing;
        }

        User newUser = User.builder()
                .fullName(name != null ? name : "")
                .cognitoUsername(username)
                .cognitoSub(cognitoSub)
                .email(email)
                .isActive(true)
                .build();
        userRepository.save(newUser);

        userProviderService.saveIdentityProvider(newUser, provider, providerSubject);

        log.info("[Auth] Social new user: sub={} provider={} email={}", cognitoSub, provider, email);
        return newUser;
    }

    private void syncMasterCognitoFields(User user, String cognitoSub, String username) {
        if (user.getCognitoSub() == null) {
            user.setCognitoSub(cognitoSub);
        }
        if (username != null && !username.equals(user.getCognitoUsername())) {
            user.setCognitoUsername(username);
        }
    }

    private void syncVerifiedEmail(User user, boolean emailVerified) {
        if (emailVerified && !Boolean.TRUE.equals(user.getEmailVerified())) {
            user.setEmailVerified(true);
        }
    }

    private void syncIdentityProviders(User user, Map<AuthProvider, String> linkedIdentities,
                                       AuthProvider currentProvider, String currentProviderSubject) {
        if (linkedIdentities.isEmpty()) {
            userProviderService.saveIdentityProvider(user, currentProvider, currentProviderSubject);
            return;
        }

        linkedIdentities.forEach((provider, providerSubject) ->
                userProviderService.saveIdentityProvider(user, provider, providerSubject));

        if (!linkedIdentities.containsKey(currentProvider)) {
            userProviderService.saveIdentityProvider(user, currentProvider, currentProviderSubject);
        }
    }

    private boolean readBooleanClaim(SignedJWT jwt, String claimName) throws ParseException {
        Object value = jwt.getJWTClaimsSet().getClaim(claimName);
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof String stringValue) {
            return Boolean.parseBoolean(stringValue);
        }
        return false;
    }

    private String resolveRefreshUsername(String accessToken, String idTokenUsername, String fallbackSub) {
        try {
            String accessTokenUsername = SignedJWT.parse(accessToken)
                    .getJWTClaimsSet()
                    .getStringClaim("username");
            if (accessTokenUsername != null && !accessTokenUsername.isBlank()) {
                return accessTokenUsername;
            }
        } catch (Exception e) {
            log.warn("[Auth] Cannot extract social refresh username from access token, using fallback username");
        }

        if (idTokenUsername != null && !idTokenUsername.isBlank()) {
            return idTokenUsername;
        }
        return fallbackSub;
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

    private Optional<String> resolveProviderSubject(SignedJWT jwt, AuthProvider provider) throws ParseException {
        List<?> identities = (List<?>) jwt.getJWTClaimsSet().getClaim("identities");
        if (identities == null) {
            return Optional.empty();
        }

        for (Object identity : identities) {
            if (identity instanceof Map<?, ?> map) {
                Object providerName = map.get("providerName");
                Object userId = map.get("userId");
                if (providerName != null
                        && userId != null
                        && mapProvider(providerName.toString()) == provider) {
                    return Optional.of(userId.toString());
                }
            }
        }
        return Optional.empty();
    }

    private Map<AuthProvider, String> resolveLinkedIdentitySubjects(SignedJWT jwt) throws ParseException {
        Map<AuthProvider, String> result = new EnumMap<>(AuthProvider.class);
        List<?> identities = (List<?>) jwt.getJWTClaimsSet().getClaim("identities");
        if (identities == null) {
            return result;
        }

        for (Object identity : identities) {
            if (identity instanceof Map<?, ?> map) {
                Object providerName = map.get("providerName");
                Object userId = map.get("userId");
                if (providerName == null || userId == null) {
                    continue;
                }

                AuthProvider provider = mapProvider(providerName.toString());
                if (provider != AuthProvider.LOCAL) {
                    result.put(provider, userId.toString());
                }
            }
        }
        return result;
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
