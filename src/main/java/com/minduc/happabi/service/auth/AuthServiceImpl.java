package com.minduc.happabi.service.auth;

import com.minduc.happabi.common.utils.CookieUtils;
import com.minduc.happabi.config.CognitoSecretHashUtil;
import com.minduc.happabi.dto.request.auth.*;
import com.minduc.happabi.dto.request.auth.ForgotPasswordRequest;
import com.minduc.happabi.dto.response.auth.AuthResponse;
import com.minduc.happabi.dto.response.user.UserProfileResponse;
import com.minduc.happabi.entity.*;
import com.minduc.happabi.enums.AuthProvider;
import com.minduc.happabi.enums.UserRole;
import com.minduc.happabi.exception.AppException;
import com.minduc.happabi.exception.code.AuthErrorCode;
import com.minduc.happabi.mapper.UserMapper;
import com.minduc.happabi.repository.MotherProfileRepository;
import com.minduc.happabi.repository.NurseProfileRepository;
import com.minduc.happabi.repository.RoleRepository;
import com.minduc.happabi.repository.UserIdentityProviderRepository;
import com.minduc.happabi.repository.UserRepository;
import com.minduc.happabi.repository.UserRoleAssignmentRepository;
import com.minduc.happabi.service.metrics.AuthMetricsService;
import com.minduc.happabi.service.s3.S3Service;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;

import java.text.ParseException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;


@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final CognitoIdentityProviderClient cognitoClient;
    private final UserRepository userRepository;
    private final UserIdentityProviderRepository identityProviderRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final UserMapper userMapper;
    private final S3Service s3ServiceImpl;
    private final MotherProfileRepository motherProfileRepository;
    private final NurseProfileRepository nurseProfileRepository;
    private final RoleRepository roleRepository;
    private final RestTemplate restTemplate;
    private final AuthMetricsService authMetrics;
    private final TokenBlacklistService tokenBlacklistService;

    @Value("${aws.cognito.user-pool-id}")
    private String userPoolId;

    @Value("${aws.cognito.client-id}")
    private String clientId;

    @Value("${aws.cognito.client-secret}")
    private String clientSecret;

    @Value("${aws.cognito.domain}")
    private String cognitoDomain;

    private static final Set<UserRole> LOCAL_ALLOWED_ROLES = Set.of(UserRole.MOTHER, UserRole.NURSE);

    @Override
    @Transactional
    public void register(RegisterRequest request) {
        if (!LOCAL_ALLOWED_ROLES.contains(request.getRole())) {
            throw new AppException(AuthErrorCode.INVALID_ROLE_FOR_REGISTRATION);
        }
        Role userRole = roleRepository.findByRoleName(request.getRole())
                .orElseThrow(() -> new AppException(AuthErrorCode.AUTH_FAILED, "Role not found"));

        Optional<User> existingByPhone = userRepository
                .findByPhoneWithRolesAndProviders(request.getPhone());

        String secretHash = CognitoSecretHashUtil.calculateSecretHash(
                request.getPhone(), clientId, clientSecret);

        if (existingByPhone.isPresent()) {
            User existing = existingByPhone.get();
            boolean hasRequestedRole = existing.getRoles().stream()
                    .anyMatch(r -> r.getRoleName() == request.getRole());

            if (existing.hasProvider(AuthProvider.LOCAL)) {
                handleExistingLocalUser(request, secretHash, existing, hasRequestedRole, userRole);
                return;
            }
            log.info("[Auth] Existing user with social login found for phone {}. " +
                     "Will attempt to link LOCAL after OTP verification.", request.getPhone());
        }

        String cognitoSub = signUpToCognito(request, secretHash);
        linkOrCreateUser(request, existingByPhone, userRole, cognitoSub);

        authMetrics.recordRegisterSuccess(request.getRole().name());
        log.info("[Auth] User registered: phone={} role={}", request.getPhone(), request.getRole());
    }

    private void handleExistingLocalUser(RegisterRequest request, String secretHash,
                                         User existing, boolean hasRequestedRole, Role userRole) {
        if (hasRequestedRole) {
            throw new AppException(AuthErrorCode.PHONE_ALREADY_EXISTS,
                    "Vai trò " + request.getRole().name() + " đã được liên kết với tài khoản này.");
        }
        try {
            cognitoClient.adminInitiateAuth(AdminInitiateAuthRequest.builder()
                    .userPoolId(userPoolId)
                    .clientId(clientId)
                    .authFlow(AuthFlowType.ADMIN_USER_PASSWORD_AUTH)
                    .authParameters(Map.of(
                            "USERNAME", request.getPhone(),
                            "PASSWORD", request.getPassword(),
                            "SECRET_HASH", secretHash
                    ))
                    .build());
        } catch (NotAuthorizedException e) {
            authMetrics.recordRegisterFailure("INVALID_CREDENTIALS_FOR_ROLE_ADD");
            throw new AppException(AuthErrorCode.INVALID_CREDENTIALS,
                    "Số điện thoại đã tồn tại. Vui lòng nhập đúng mật khẩu để thêm vai trò mới.");
        }
        assignCognitoGroup(request.getPhone(), request.getRole().name());
        saveRoleAssignment(existing, userRole);
        createProfileForRole(existing, request.getRole());
        log.info("[Auth] Existing user {} assigned new role {} via phone verification",
                existing.getId(), request.getRole());
        authMetrics.recordRegisterSuccess(request.getRole().name());
    }

    private String signUpToCognito(RegisterRequest request, String secretHash) {
        try {
            SignUpResponse signUpResponse = cognitoClient.signUp(SignUpRequest.builder()
                    .clientId(clientId)
                    .secretHash(secretHash)
                    .username(request.getPhone())
                    .password(request.getPassword())
                    .userAttributes(
                            AttributeType.builder().name("phone_number").value(request.getPhone()).build(),
                            AttributeType.builder().name("name").value(request.getFullName()).build()
                    )
                    .build());
            String cognitoSub = signUpResponse.userSub();
            log.info("[Auth] Cognito signUp ok: phone={} sub={} role={}",
                    request.getPhone(), cognitoSub, request.getRole());
            return cognitoSub;
        } catch (UsernameExistsException e) {
            authMetrics.recordRegisterFailure("PHONE_ALREADY_EXISTS");
            throw new AppException(AuthErrorCode.PHONE_ALREADY_EXISTS);
        } catch (InvalidPasswordException e) {
            authMetrics.recordRegisterFailure("PASSWORD_POLICY_VIOLATED");
            throw new AppException(AuthErrorCode.PASSWORD_POLICY_VIOLATED);
        } catch (CognitoIdentityProviderException e) {
            authMetrics.recordRegisterFailure("COGNITO_ERROR");
            log.error("[Auth] Cognito signUp error: {}", e.awsErrorDetails().errorMessage(), e);
            throw new AppException(AuthErrorCode.INVALID_CREDENTIALS, e.awsErrorDetails().errorMessage());
        }
    }

    private void linkOrCreateUser(RegisterRequest request, Optional<User> existingByPhone,
                                   Role userRole, String cognitoSub) {
        if (existingByPhone.isPresent()) {
            User existing = existingByPhone.get();
            saveIdentityProvider(existing, AuthProvider.LOCAL, cognitoSub);
            boolean hasRequestedRole = existing.getRoles().stream()
                    .anyMatch(r -> r.getRoleName() == request.getRole());
            if (!hasRequestedRole) {
                saveRoleAssignment(existing, userRole);
                createProfileForRole(existing, request.getRole());
                assignCognitoGroup(request.getPhone(), request.getRole().name());
            }
            for (Role role : existing.getRoles()) {
                if (role.getRoleName() != request.getRole()) {
                    assignCognitoGroup(request.getPhone(), role.getRoleName().name());
                }
            }
            log.info("[Auth] Account linked via phone: userId={} phone={} -> LOCAL added",
                    existing.getId(), request.getPhone());
        } else {
            User newUser = User.builder()
                    .fullName(request.getFullName())
                    .phone(request.getPhone())
                    .isActive(true)
                    .build();
            userRepository.save(newUser);
            saveIdentityProvider(newUser, AuthProvider.LOCAL, cognitoSub);
            saveRoleAssignment(newUser, userRole);
            assignCognitoGroup(request.getPhone(), request.getRole().name());
            createProfileForRole(newUser, request.getRole());
        }
    }

    private void createProfileForRole(User user, UserRole roleName) {
        if (UserRole.MOTHER.equals(roleName)) {
            motherProfileRepository.save(MotherProfile.builder().user(user).build());
        } else if (UserRole.NURSE.equals(roleName)) {
            nurseProfileRepository.save(NurseProfile.builder().user(user).build());
        }
    }


    private void assignCognitoGroup(String username, String roleName) {
        try {
            cognitoClient.adminAddUserToGroup(AdminAddUserToGroupRequest.builder()
                    .userPoolId(userPoolId)
                    .username(username)
                    .groupName(roleName)
                    .build());
            log.info("[Cognito] Added user {} to group {}", username, roleName);
        } catch (Exception e) {
            log.error("[Cognito] Failed to add user {} to group {}: {}", username, roleName, e.getMessage());
            throw new AppException(AuthErrorCode.AUTH_FAILED,
                    "Failed to assign Cognito group: " + roleName);
        }
    }

    @Override
    public void verifyOtp(VerifyOtpRequest request) {
        String secretHash = CognitoSecretHashUtil.calculateSecretHash(
                request.getPhone(), clientId, clientSecret);
        try {
            cognitoClient.confirmSignUp(ConfirmSignUpRequest.builder()
                    .clientId(clientId)
                    .secretHash(secretHash)
                    .username(request.getPhone())
                    .confirmationCode(request.getOtpCode())
                    .build());

            log.info("[Auth] OTP verified ok: phone={}", request.getPhone());
            authMetrics.recordOtpVerifySuccess();

        } catch (CodeMismatchException e) {
            authMetrics.recordOtpVerifyFailure("CODE_MISMATCH");
            throw new AppException(AuthErrorCode.OTP_INVALID);
        } catch (ExpiredCodeException e) {
            authMetrics.recordOtpVerifyFailure("CODE_EXPIRED");
            throw new AppException(AuthErrorCode.OTP_EXPIRED);
        } catch (NotAuthorizedException e) {
            authMetrics.recordOtpVerifyFailure("ALREADY_CONFIRMED");
            throw new AppException(AuthErrorCode.USER_ALREADY_CONFIRMED);
        } catch (CognitoIdentityProviderException e) {
            authMetrics.recordOtpVerifyFailure("COGNITO_ERROR");
            log.error("[Auth] confirmSignUp error: {}", e.awsErrorDetails().errorMessage(), e);
            throw new AppException(AuthErrorCode.OTP_INVALID, e.awsErrorDetails().errorMessage());
        }
    }

    @Override
    public void resendOtp(ResendOtpRequest request) {
        String secretHash = CognitoSecretHashUtil.calculateSecretHash(
                request.getPhone(), clientId, clientSecret);
        try {
            cognitoClient.resendConfirmationCode(ResendConfirmationCodeRequest.builder()
                    .clientId(clientId)
                    .secretHash(secretHash)
                    .username(request.getPhone())
                    .build());

            log.info("[Auth] OTP resent: phone={}", request.getPhone());
            authMetrics.recordOtpResend();

        } catch (UserNotFoundException e) {
            throw new AppException(AuthErrorCode.USER_NOT_FOUND);
        } catch (NotAuthorizedException e) {
            throw new AppException(AuthErrorCode.USER_ALREADY_CONFIRMED);
        } catch (CognitoIdentityProviderException e) {
            log.error("[Auth] resendConfirmationCode error: {}", e.awsErrorDetails().errorMessage(), e);
            throw new AppException(AuthErrorCode.OTP_INVALID, e.awsErrorDetails().errorMessage());
        }
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request, HttpServletResponse response) {
        String secretHash = CognitoSecretHashUtil.calculateSecretHash(
                request.getPhone(), clientId, clientSecret);
        try {
            User user = userRepository.findByPhoneWithRolesAndProviders(request.getPhone())
                    .orElseThrow(() -> new AppException(AuthErrorCode.USER_NOT_FOUND));

            AdminInitiateAuthResponse authResponse = cognitoClient.adminInitiateAuth(
                    AdminInitiateAuthRequest.builder()
                            .userPoolId(userPoolId)
                            .clientId(clientId)
                            .authFlow(AuthFlowType.ADMIN_USER_PASSWORD_AUTH)
                            .authParameters(Map.of(
                                    "USERNAME", request.getPhone(),
                                    "PASSWORD", request.getPassword(),
                                    "SECRET_HASH", secretHash
                            ))
                            .build());

            AuthenticationResultType result = authResponse.authenticationResult();

            if (request.getPortalRole() != null) {
                boolean hasAccess = user.getRoles().stream()
                        .anyMatch(r -> r.getRoleName() == request.getPortalRole());
                if (!hasAccess) {
                    throw new AppException(AuthErrorCode.AUTH_FAILED,
                            "Tài khoản của bạn không có quyền đăng nhập vào portal " + request.getPortalRole());
                }
            }

            user.setLastLoginAt(OffsetDateTime.now());
            userRepository.save(user);

            String localSub = user.getProviderUid(AuthProvider.LOCAL)
                    .orElse("unknown");

            CookieUtils.addRefreshTokenCookie(response, result.refreshToken() + "::" + localSub);

            String avatarUrl = s3ServiceImpl.presign(user.getAvatarS3Key());
            UserProfileResponse profile = userMapper.toProfileResponse(user, avatarUrl);

            log.info("[Auth] Login ok: phone={} portal={}", request.getPhone(), request.getPortalRole());
            authMetrics.recordLoginSuccess(request.getPortalRole() != null ?
                    request.getPortalRole().name() : "UNKNOWN", "LOCAL");

            return AuthResponse.builder()
                    .accessToken(result.accessToken())
                    .refreshToken(result.refreshToken())
                    .expiresIn(result.expiresIn())
                    .user(profile)
                    .build();

        } catch (NotAuthorizedException e) {
            authMetrics.recordLoginFailure("INVALID_CREDENTIALS");
            throw new AppException(AuthErrorCode.INVALID_CREDENTIALS);
        } catch (UserNotConfirmedException e) {
            authMetrics.recordLoginFailure("NOT_CONFIRMED");
            throw new AppException(AuthErrorCode.USER_NOT_CONFIRMED);
        } catch (UserNotFoundException e) {
            authMetrics.recordLoginFailure("USER_NOT_FOUND");
            throw new AppException(AuthErrorCode.USER_NOT_FOUND);
        } catch (CognitoIdentityProviderException e) {
            authMetrics.recordLoginFailure("COGNITO_ERROR");
            log.error("[Auth] initiateAuth error: {}", e.awsErrorDetails().errorMessage(), e);
            throw new AppException(AuthErrorCode.INVALID_CREDENTIALS, e.awsErrorDetails().errorMessage());
        }
    }

    @Override
    @Transactional
    public AuthResponse socialSync(SocialSyncRequest request, HttpServletResponse response) {
        String tokenUrl = cognitoDomain + "/oauth2/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(clientId, clientSecret);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "authorization_code");
        body.add("client_id", clientId);
        body.add("code", request.getCode());
        body.add("redirect_uri", request.getRedirectUri());

        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> tokenResponse = restTemplate.postForEntity(tokenUrl, entity, Map.class);
            Map<String, Object> tokens = tokenResponse.getBody();
            if (tokens == null || !tokens.containsKey("id_token")) {
                throw new AppException(AuthErrorCode.INVALID_CREDENTIALS, "Failed to retrieve tokens from Cognito");
            }

            String idToken = (String)  tokens.get("id_token");
            String accessToken = (String)  tokens.get("access_token");
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
                saveRoleAssignment(user, motherRole);
                createProfileForRole(user, UserRole.MOTHER);
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
                    .refreshToken(refreshToken)
                    .expiresIn(expiresIn)
                    .user(profile)
                    .build();

        } catch (HttpClientErrorException e) {
            log.error("[Auth] Cognito token exchange failed: {}", e.getResponseBodyAsString());
            throw new AppException(AuthErrorCode.INVALID_CREDENTIALS, "Invalid code or redirect_uri");
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
                saveIdentityProvider(existing, provider, cognitoSub);
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

        saveIdentityProvider(newUser, provider, cognitoSub);

        log.info("[Auth] Social new user: sub={} provider={} email={}", cognitoSub, provider, email);
        return newUser;
    }

    @Override
    public AuthResponse refresh(HttpServletRequest request) {
        String cookieValue = CookieUtils.readRefreshTokenFromCookie(request);
        if (cookieValue == null || !cookieValue.contains("::")) {
            log.error("[Auth] Refresh token cookie is missing or invalid. Cookie value: {}", cookieValue);
            throw new AppException(AuthErrorCode.REFRESH_TOKEN_INVALID);
        }

        String[] parts = cookieValue.split("::");
        String refreshToken = parts[0];
        String cognitoSub   = parts[1];

        log.info("[Auth] Attempting refresh for sub: {}", cognitoSub);
        String secretHash = CognitoSecretHashUtil.calculateSecretHash(cognitoSub, clientId, clientSecret);

        try {
            InitiateAuthResponse authResponse = cognitoClient.initiateAuth(
                    InitiateAuthRequest.builder()
                            .authFlow(AuthFlowType.REFRESH_TOKEN_AUTH)
                            .clientId(clientId)
                            .authParameters(Map.of(
                                    "REFRESH_TOKEN", refreshToken,
                                    "SECRET_HASH",   secretHash
                            ))
                            .build());

            AuthenticationResultType result = authResponse.authenticationResult();
            log.info("[Auth] Token refreshed ok");

            return AuthResponse.builder()
                    .accessToken(result.accessToken())
                    .expiresIn(result.expiresIn())
                    .refreshToken(null)
                    .build();

        } catch (NotAuthorizedException e) {
            log.error("[Auth] refresh NotAuthorizedException: {}", e.awsErrorDetails().errorMessage());
            throw new AppException(AuthErrorCode.REFRESH_TOKEN_INVALID);
        } catch (CognitoIdentityProviderException e) {
            log.error("[Auth] refresh Cognito error: {}", e.awsErrorDetails().errorMessage(), e);
            throw new AppException(AuthErrorCode.REFRESH_TOKEN_INVALID);
        }
    }

    @Override
    public void logout(String accessToken, HttpServletRequest request, HttpServletResponse response) {

        String cookieValue = CookieUtils.readRefreshTokenFromCookie(request);

        if (cookieValue != null && cookieValue.contains("::")) {
            String refreshToken = cookieValue.split("::")[0];
            String identifier   = cookieValue.split("::")[1];

            boolean isSocial = identifier.startsWith("google_")
                    || identifier.startsWith("facebook_");

            if (isSocial) {
                try {
                    cognitoClient.revokeToken(RevokeTokenRequest.builder()
                            .token(refreshToken)
                            .clientId(clientId)
                            .clientSecret(clientSecret)
                            .build());
                    log.info("[Auth] Social refresh token revoked");
                } catch (Exception e) {
                    log.warn("[Auth] revokeToken failed (may already be invalid): {}", e.getMessage());
                }

            } else {
                try {
                    cognitoClient.globalSignOut(GlobalSignOutRequest.builder()
                            .accessToken(accessToken)
                            .build());
                    log.info("[Auth] GlobalSignOut ok");
                } catch (CognitoIdentityProviderException e) {
                    log.warn("[Auth] GlobalSignOut failed (may already be invalid): {}",
                            e.awsErrorDetails().errorMessage());
                }

            }
            tokenBlacklistService.blacklist(accessToken);
        }

        CookieUtils.clearRefreshTokenCookie(response);
        log.info("[Auth] Logout successful");
    }

    @Override
    public void forgotPassword(ForgotPasswordRequest request) {
        userRepository.findByPhone(request.getPhone())
                .orElseThrow(() -> new AppException(AuthErrorCode.USER_NOT_FOUND));

        String secretHash = CognitoSecretHashUtil.calculateSecretHash(
                request.getPhone(), clientId, clientSecret);
        try {
            cognitoClient.forgotPassword(
                    software.amazon.awssdk.services.cognitoidentityprovider.model.ForgotPasswordRequest.builder()
                            .clientId(clientId)
                            .secretHash(secretHash)
                            .username(request.getPhone())
                            .build());

            log.info("[Auth] ForgotPassword OTP sent: phone={}", request.getPhone());
            authMetrics.recordForgotPasswordRequested();

        } catch (UserNotFoundException e) {
            throw new AppException(AuthErrorCode.USER_NOT_FOUND);
        } catch (InvalidParameterException e) {
            throw new AppException(AuthErrorCode.SOCIAL_PROVIDER_MISMATCH,
                    "Tài khoản này đăng nhập qua mạng xã hội, không có mật khẩu để đặt lại.");
        } catch (LimitExceededException e) {
            throw new AppException(AuthErrorCode.RATE_LIMITED);
        } catch (CognitoIdentityProviderException e) {
            log.error("[Auth] ForgotPassword Cognito error: {}", e.awsErrorDetails().errorMessage(), e);
            throw new AppException(AuthErrorCode.INVALID_CREDENTIALS, e.awsErrorDetails().errorMessage());
        }
    }

    @Override
    public void resetPassword(ResetPasswordRequest request) {
        String secretHash = CognitoSecretHashUtil.calculateSecretHash(
                request.getPhone(), clientId, clientSecret);
        try {
            cognitoClient.confirmForgotPassword(ConfirmForgotPasswordRequest.builder()
                    .clientId(clientId)
                    .secretHash(secretHash)
                    .username(request.getPhone())
                    .confirmationCode(request.getOtpCode())
                    .password(request.getNewPassword())
                    .build());

            log.info("[Auth] ResetPassword ok: phone={}", request.getPhone());
            authMetrics.recordResetPasswordSuccess();

        } catch (CodeMismatchException e) {
            throw new AppException(AuthErrorCode.OTP_INVALID);
        } catch (ExpiredCodeException e) {
            throw new AppException(AuthErrorCode.OTP_EXPIRED);
        } catch (InvalidPasswordException e) {
            throw new AppException(AuthErrorCode.PASSWORD_POLICY_VIOLATED);
        } catch (UserNotFoundException e) {
            throw new AppException(AuthErrorCode.USER_NOT_FOUND);
        } catch (LimitExceededException e) {
            throw new AppException(AuthErrorCode.RATE_LIMITED);
        } catch (CognitoIdentityProviderException e) {
            log.error("[Auth] ConfirmForgotPassword Cognito error: {}", e.awsErrorDetails().errorMessage(), e);
            throw new AppException(AuthErrorCode.INVALID_CREDENTIALS, e.awsErrorDetails().errorMessage());
        }
    }

    private AuthProvider resolveProvider(SignedJWT jwt) throws ParseException {
        try {
            List<?> identities = (List<?>) jwt.getJWTClaimsSet().getClaim("identities");
            if (identities != null && !identities.isEmpty()) {
                Object first = identities.getFirst();
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
            if (username.toLowerCase().startsWith("google_"))   return AuthProvider.GOOGLE;
            if (username.toLowerCase().startsWith("facebook_")) return AuthProvider.FACEBOOK;
        }

        return AuthProvider.LOCAL;
    }

    private AuthProvider mapProvider(String providerName) {
        return switch (providerName.toLowerCase()) {
            case "google"   -> AuthProvider.GOOGLE;
            case "facebook" -> AuthProvider.FACEBOOK;
            default         -> AuthProvider.LOCAL;
        };
    }

    private void saveIdentityProvider(User user, AuthProvider provider, String providerUid) {
        UserIdentityProvider link = UserIdentityProvider.builder()
                .user(user)
                .provider(provider)
                .providerUid(providerUid)
                .build();
        identityProviderRepository.save(link);
    }

    private void saveRoleAssignment(User user, Role role) {
        UserRoleAssignment assignment = UserRoleAssignment.builder()
                .user(user)
                .role(role)
                .build();
        userRoleAssignmentRepository.save(assignment);
    }
}
