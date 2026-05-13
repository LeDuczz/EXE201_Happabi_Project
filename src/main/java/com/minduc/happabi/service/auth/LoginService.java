package com.minduc.happabi.service.auth;

import com.minduc.happabi.common.utils.CookieUtils;
import com.minduc.happabi.dto.request.auth.LoginRequest;
import com.minduc.happabi.dto.response.auth.AuthResponse;
import com.minduc.happabi.dto.response.user.UserProfileResponse;
import com.minduc.happabi.entity.User;
import com.minduc.happabi.enums.AuthProvider;
import com.minduc.happabi.exception.AppException;
import com.minduc.happabi.exception.code.AuthErrorCode;
import com.minduc.happabi.mapper.UserMapper;
import com.minduc.happabi.repository.UserRepository;
import com.minduc.happabi.service.metrics.AuthMetricsService;
import com.minduc.happabi.service.s3.S3Service;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminInitiateAuthResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthenticationResultType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CognitoIdentityProviderException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InitiateAuthResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.NotAuthorizedException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotConfirmedException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException;

import java.time.OffsetDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoginService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final S3Service s3ServiceImpl;
    private final AuthMetricsService authMetrics;
    private final TokenBlacklistService tokenBlacklistService;
    private final CognitoService cognitoService;

    @Transactional
    public AuthResponse login(LoginRequest request, HttpServletResponse response) {
        try {
            User user = userRepository.findByPhoneWithRolesAndProviders(request.getPhone())
                    .orElseThrow(() -> new AppException(AuthErrorCode.USER_NOT_FOUND));

            AdminInitiateAuthResponse authResponse = cognitoService.adminInitiateAuth(
                    request.getPhone(), request.getPassword());

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

        try {
            InitiateAuthResponse authResponse = cognitoService.initiateRefreshAuth(refreshToken, cognitoSub);

            AuthenticationResultType result = authResponse.authenticationResult();
            log.info("[Auth] Token refreshed ok");

            return AuthResponse.builder()
                    .accessToken(result.accessToken())
                    .expiresIn(result.expiresIn())
                    .build();

        } catch (NotAuthorizedException e) {
            log.error("[Auth] refresh NotAuthorizedException: {}", e.awsErrorDetails().errorMessage());
            throw new AppException(AuthErrorCode.REFRESH_TOKEN_INVALID);
        } catch (CognitoIdentityProviderException e) {
            log.error("[Auth] refresh Cognito error: {}", e.awsErrorDetails().errorMessage(), e);
            throw new AppException(AuthErrorCode.REFRESH_TOKEN_INVALID);
        }
    }

    public void logout(String accessToken, HttpServletRequest request, HttpServletResponse response) {
        String cookieValue = CookieUtils.readRefreshTokenFromCookie(request);

        if (cookieValue != null && cookieValue.contains("::")) {
            String refreshToken = cookieValue.split("::")[0];
            String identifier = cookieValue.split("::")[1];

            boolean isSocial = identifier.startsWith("google_")
                    || identifier.startsWith("facebook_");

            if (isSocial) {
                try {
                    cognitoService.revokeToken(refreshToken);
                    log.info("[Auth] Social refresh token revoked");
                } catch (Exception e) {
                    log.warn("[Auth] revokeToken failed (may already be invalid): {}", e.getMessage());
                }

            } else {
                try {
                    cognitoService.globalSignOut(accessToken);
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
}
