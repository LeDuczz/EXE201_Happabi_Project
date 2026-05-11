package com.minduc.happabi.service.auth;

import com.minduc.happabi.common.utils.CookieUtils;
import com.minduc.happabi.config.CognitoSecretHashUtil;
import com.minduc.happabi.dto.request.*;
import com.minduc.happabi.dto.request.ForgotPasswordRequest;
import com.minduc.happabi.dto.response.AuthResponse;
import com.minduc.happabi.dto.response.UserProfileResponse;
import com.minduc.happabi.entity.MotherProfile;
import com.minduc.happabi.entity.User;
import com.minduc.happabi.enums.AuthProvider;
import com.minduc.happabi.enums.UserRole;
import com.minduc.happabi.exception.AppException;
import com.minduc.happabi.exception.AuthErrorCode;
import com.minduc.happabi.mapper.UserMapper;
import com.minduc.happabi.repository.MotherProfileRepository;
import com.minduc.happabi.repository.NurseProfileRepository;
import com.minduc.happabi.repository.RoleRepository;
import com.minduc.happabi.repository.UserRepository;
import com.minduc.happabi.entity.Role;
import com.minduc.happabi.service.metrics.AuthMetricsService;
import com.minduc.happabi.service.s3.S3Service;
import com.minduc.happabi.service.s3.S3ServiceImpl;
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
import org.springframework.security.oauth2.jwt.Jwt;
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
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final CognitoIdentityProviderClient cognitoClient;
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final S3Service s3ServiceImpl;
    private final MotherProfileRepository motherProfileRepository;
    private final NurseProfileRepository nurseProfileRepository;
    private final RoleRepository roleRepository;
    private final RestTemplate restTemplate;
    private final AuthMetricsService authMetrics;

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

        if (userRepository.existsByPhone(request.getPhone())) {
            throw new AppException(AuthErrorCode.PHONE_ALREADY_EXISTS);
        }

        String secretHash = CognitoSecretHashUtil.calculateSecretHash(
                request.getPhone(), clientId, clientSecret);

        try {
            SignUpResponse signUpResponse = cognitoClient.signUp(SignUpRequest.builder()
                    .clientId(clientId)
                    .secretHash(secretHash)
                    .username(request.getPhone())
                    .password(request.getPassword())
                    .userAttributes(
                            AttributeType.builder().name("phone_number").value(request.getPhone()).build(),
                            AttributeType.builder().name("name").value(request.getFullName()).build(),
                            AttributeType.builder().name("custom:role").value(request.getRole().name()).build()
                    )
                    .build());

            String cognitoSub = signUpResponse.userSub();
            log.info("[Auth] Cognito signUp ok: phone={} sub={} role={}", request.getPhone(), cognitoSub, request.getRole());

            Role userRole = roleRepository.findByRoleName(request.getRole())
                    .orElseThrow(() -> new AppException(AuthErrorCode.AUTH_FAILED, "Role not found"));

            // 1d. Persist user in local DB
            User user = User.builder()
                    .cognitoSub(cognitoSub)
                    .authProvider(AuthProvider.LOCAL)
                    .fullName(request.getFullName())
                    .phone(request.getPhone())
                    .role(userRole)
                    .isActive(true)
                    .createdAt(OffsetDateTime.now())
                    .updatedAt(OffsetDateTime.now())
                    .build();
            userRepository.save(user);

            if(UserRole.MOTHER.equals(request.getRole())){
                    motherProfileRepository.save(
                            MotherProfile.builder()
                                    .user(user)
                                    .build()
                    );
                log.info("[Auth] New mother registered: phone={} sub={}", request.getPhone(), cognitoSub);
            } else if(UserRole.NURSE.equals(request.getRole())){
                    nurseProfileRepository.save(
                            com.minduc.happabi.entity.NurseProfile.builder()
                                    .user(user)
                                    .build()
                    );
                log.info("[Auth] New nurse registered: phone={} sub={}", request.getPhone(), cognitoSub);
            }
            authMetrics.recordRegisterSuccess(request.getRole().name());
            // NOTE: Cognito Group assignment is handled by the Post-Confirmation Lambda trigger.

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
            log.info("[Auth] OTP verified: phone={}", request.getPhone());
            authMetrics.recordOtpVerifySuccess();

        } catch (CodeMismatchException e) {
            authMetrics.recordOtpVerifyFailure("CODE_MISMATCH");
            throw new AppException(AuthErrorCode.OTP_INVALID);
        } catch (ExpiredCodeException e) {
            authMetrics.recordOtpVerifyFailure("EXPIRED");
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
        } catch (InvalidParameterException e) {
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
            AdminInitiateAuthResponse authResponse = cognitoClient.adminInitiateAuth(
                    AdminInitiateAuthRequest.builder()
                            .userPoolId(userPoolId)
                            .clientId(clientId)
                            .authFlow(AuthFlowType.ADMIN_USER_PASSWORD_AUTH)
                            .authParameters(Map.of(
                                    "USERNAME",    request.getPhone(),
                                    "PASSWORD",    request.getPassword(),
                                    "SECRET_HASH", secretHash
                            ))
                            .build());

            AuthenticationResultType result = authResponse.authenticationResult();

            // Sync user's lastLoginAt in DB
            User user = userRepository.findByPhone(request.getPhone())
                    .orElseThrow(() -> new AppException(AuthErrorCode.USER_NOT_FOUND));
            user.setLastLoginAt(OffsetDateTime.now());
            userRepository.save(user);

            String avatarUrl = s3ServiceImpl.presign(user.getAvatarS3Key());
            UserProfileResponse profile = userMapper.toProfileResponse(user, avatarUrl);

            // For REFRESH_TOKEN_AUTH, Cognito REQUIRES the SECRET_HASH to be calculated using the user's UUID (sub), NOT the alias (phone number)
            CookieUtils.addRefreshTokenCookie(response, result.refreshToken() + "::" + user.getCognitoSub());

            log.info("[Auth] Login ok: phone={} role={}", request.getPhone(), user.getRole().getRoleName());
            authMetrics.recordLoginSuccess(user.getRole().getRoleName().name(), "LOCAL");

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
            
            String idToken = (String) tokens.get("id_token");
            String accessToken = (String) tokens.get("access_token");
            String refreshToken = (String) tokens.get("refresh_token");
            Integer expiresIn = (Integer) tokens.get("expires_in");

            SignedJWT signedJWT = SignedJWT.parse(idToken);
            String cognitoSub = signedJWT.getJWTClaimsSet().getSubject();
            String email = signedJWT.getJWTClaimsSet().getStringClaim("email");
            String name = signedJWT.getJWTClaimsSet().getStringClaim("name");

            // Determine provider from the Cognito identity source claim
            AuthProvider provider = resolveProvider(signedJWT);

            Role motherRole = roleRepository.findByRoleName(UserRole.MOTHER)
                    .orElseThrow(() -> new AppException(AuthErrorCode.AUTH_FAILED, "Role MOTHER not found"));

            User user = userRepository.findByCognitoSub(cognitoSub).orElseGet(() -> {
                User newUser = User.builder()
                        .cognitoSub(cognitoSub)
                        .authProvider(provider)
                        .fullName(name != null ? name : "")
                        .email(email)
                        .role(motherRole)
                        .isActive(true)
                        .build();
                userRepository.save(newUser);

                try {
                    cognitoClient.adminAddUserToGroup(AdminAddUserToGroupRequest.builder()
                            .userPoolId(userPoolId)
                            .username(cognitoSub)  // Social users dùng sub làm username
                            .groupName(UserRole.MOTHER.name())
                            .build());
                    log.info("[Auth] Social user added to MOTHER group: sub={}", cognitoSub);

                    for (String providerGroup : java.util.List.of(
                            userPoolId + "_Google",
                            userPoolId + "_Facebook"
                    )) {
                        try {
                            cognitoClient.adminRemoveUserFromGroup(AdminRemoveUserFromGroupRequest.builder()
                                            .userPoolId(userPoolId)
                                            .username(cognitoSub)
                                            .groupName(providerGroup)
                                            .build());
                            log.info("[Auth] Removed social user from provider group: {}", providerGroup);
                        } catch (Exception ignore) {
                            // Group có thể không tồn tại hoặc user không thuộc group đó, bỏ qua
                        }
                    }
                } catch (CognitoIdentityProviderException e) {
                    log.warn("[Auth] Failed to add social user to Cognito group: {}", e.awsErrorDetails().errorMessage());
                }

                log.info("[Auth] Social new user: sub={} provider={}", cognitoSub, provider);
                return newUser;
            });

            // Guard: prevent a LOCAL-registered user from logging in via social
            if (!user.getAuthProvider().equals(provider) && user.getAuthProvider() == AuthProvider.LOCAL) {
                throw new AppException(AuthErrorCode.SOCIAL_PROVIDER_MISMATCH);
            }

            // Guard: only MOTHER is allowed via social
            if (user.getRole().getRoleName() != UserRole.MOTHER) {
                throw new AppException(AuthErrorCode.SOCIAL_ROLE_NOT_ALLOWED);
            }

            user.setLastLoginAt(OffsetDateTime.now());
            userRepository.save(user);

            if (motherProfileRepository.findByUser(user).isEmpty()) {
                MotherProfile profile = MotherProfile.builder()
                        .user(user)
                        .build();

                motherProfileRepository.save(profile);
            }

            // Add refresh token cookie
            CookieUtils.addRefreshTokenCookie(response, refreshToken + "::" + user.getCognitoSub());

            String avatarUrl = s3ServiceImpl.presign(user.getAvatarS3Key());
            UserProfileResponse profile = userMapper.toProfileResponse(user, avatarUrl);

            log.info("[Auth] Social sync ok: sub={} provider={}", user.getCognitoSub(), user.getAuthProvider());
            authMetrics.recordSocialLoginSuccess(user.getAuthProvider().name());

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

    @Override
    public AuthResponse refresh(HttpServletRequest request) {
        String cookieValue = CookieUtils.readRefreshTokenFromCookie(request);
        if (cookieValue == null || !cookieValue.contains("::")) {
            log.error("[Auth] Refresh token cookie is missing or invalid. Cookie value: {}", cookieValue);
            throw new AppException(AuthErrorCode.REFRESH_TOKEN_INVALID);
        }

        String[] parts = cookieValue.split("::");
        String refreshToken = parts[0];
        String cognitoSub = parts[1];

        log.info("[Auth] Attempting refresh for sub: {}", cognitoSub);
        String secretHash = CognitoSecretHashUtil.calculateSecretHash(cognitoSub, clientId, clientSecret);

        try {
            InitiateAuthResponse authResponse = cognitoClient.initiateAuth(
                    InitiateAuthRequest.builder()
                            .authFlow(AuthFlowType.REFRESH_TOKEN_AUTH)
                            .clientId(clientId)
                            .authParameters(Map.of(
                                    "REFRESH_TOKEN", refreshToken,
                                    "SECRET_HASH", secretHash
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
    public void logout(String accessToken, HttpServletResponse response) {
        try {
            cognitoClient.globalSignOut(GlobalSignOutRequest.builder()
                    .accessToken(accessToken)
                    .build());

            CookieUtils.clearRefreshTokenCookie(response);

            log.info("[Auth] GlobalSignOut ok");
        } catch (CognitoIdentityProviderException e) {
            log.warn("[Auth] GlobalSignOut failed (token may already be invalid): {}", e.awsErrorDetails().errorMessage());
        }
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
            if (username.toLowerCase().startsWith("google_")) return AuthProvider.GOOGLE;
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
}

