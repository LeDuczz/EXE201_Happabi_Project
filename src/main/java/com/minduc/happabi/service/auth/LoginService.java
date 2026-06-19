package com.minduc.happabi.service.auth;

import com.minduc.happabi.common.utils.CookieUtils;
import com.minduc.happabi.dto.request.auth.LoginRequest;
import com.minduc.happabi.dto.response.auth.AuthResponse;
import com.minduc.happabi.dto.response.user.UserProfileResponse;
import com.minduc.happabi.entity.User;
import com.minduc.happabi.exception.AppException;
import com.minduc.happabi.exception.code.AuthErrorCode;
import com.minduc.happabi.integration.cognito.CognitoService;
import com.minduc.happabi.mapper.UserMapper;
import com.minduc.happabi.observability.annotation.AuditAction;
import com.minduc.happabi.observability.annotation.LogExecution;
import com.minduc.happabi.observability.annotation.TimedAction;
import com.minduc.happabi.repository.UserRepository;
import com.minduc.happabi.integration.s3.IS3Service;
import com.nimbusds.jwt.SignedJWT;
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
    private final IS3Service s3ServiceImpl;
    private final TokenBlacklistService tokenBlacklistService;
    private final CognitoService cognitoService;

    @LogExecution
    @TimedAction("LOGIN")
    @AuditAction(action = "LOGIN", resourceType = "USER_SESSION")
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
                            "Your account is not allowed to sign in to portal " + request.getPortalRole());
                }
            } else {
                throw new AppException(AuthErrorCode.AUTH_FAILED, "Portal role is required for sign-in.");
            }

            user.setLastLoginAt(OffsetDateTime.now());
            userRepository.save(user);

            String fallbackUsername = user.getCognitoUsername() != null
                    ? user.getCognitoUsername()
                    : request.getPhone();
            String refreshUsername = extractCognitoTokenUsername(result.accessToken(), fallbackUsername);
            CookieUtils.addRefreshTokenCookie(response, result.refreshToken() + "::" + refreshUsername);

            String avatarUrl = s3ServiceImpl.presign(user.getAvatarS3Key());
            UserProfileResponse profile = userMapper.toProfileResponse(user, avatarUrl);

            log.info("[Auth] Login ok: phone={} portal={}", request.getPhone(), request.getPortalRole());

            return AuthResponse.builder()
                    .accessToken(result.accessToken())
                    .expiresIn(result.expiresIn())
                    .user(profile)
                    .build();

        } catch (NotAuthorizedException e) {
            throw new AppException(AuthErrorCode.INVALID_CREDENTIALS);
        } catch (UserNotConfirmedException e) {
            throw new AppException(AuthErrorCode.USER_NOT_CONFIRMED);
        } catch (UserNotFoundException e) {
            throw new AppException(AuthErrorCode.USER_NOT_FOUND);
        } catch (CognitoIdentityProviderException e) {
            log.warn("[Auth] Cognito login rejected: status={} code={}",
                    e.statusCode(), e.awsErrorDetails().errorCode());
            throw new AppException(AuthErrorCode.INVALID_CREDENTIALS, e.awsErrorDetails().errorMessage());
        }
    }

    private String extractCognitoTokenUsername(String accessToken, String fallbackUsername) {
        try {
            String tokenUsername = SignedJWT.parse(accessToken)
                    .getJWTClaimsSet()
                    .getStringClaim("username");
            if (tokenUsername != null && !tokenUsername.isBlank()) {
                return tokenUsername;
            }
        } catch (Exception e) {
            log.warn("[Auth] Cannot extract Cognito token username, using fallback username");
        }
        return fallbackUsername;
    }

    @TimedAction("REFRESH_TOKEN")
    @AuditAction(action = "REFRESH_TOKEN", resourceType = "USER_SESSION")
    @LogExecution
    public AuthResponse refresh(HttpServletRequest request) {
        String cookieValue = CookieUtils.readRefreshTokenFromCookie(request);
        if (cookieValue == null || !cookieValue.contains("::")) {
            log.warn("[Auth] Refresh token cookie is missing or invalid.");
            throw new AppException(AuthErrorCode.REFRESH_TOKEN_INVALID);
        }

        String[] parts = cookieValue.split("::");
        String refreshToken = parts[0];
        String username = parts[1];

        log.info("[Auth] Attempting refresh for username: {}", username);

        try {
            InitiateAuthResponse authResponse = cognitoService.initiateRefreshAuth(refreshToken, username);

            AuthenticationResultType result = authResponse.authenticationResult();
            log.info("[Auth] Token refreshed ok");

            return AuthResponse.builder()
                    .accessToken(result.accessToken())
                    .expiresIn(result.expiresIn())
                    .build();

        } catch (NotAuthorizedException e) {
            log.warn("[Auth] Refresh token rejected by Cognito: code={}", e.awsErrorDetails().errorCode());
            throw new AppException(AuthErrorCode.REFRESH_TOKEN_INVALID);
        } catch (CognitoIdentityProviderException e) {
            log.warn("[Auth] Cognito refresh rejected: status={} code={}",
                    e.statusCode(), e.awsErrorDetails().errorCode());
            throw new AppException(AuthErrorCode.REFRESH_TOKEN_INVALID);
        }
    }

    @LogExecution
    @TimedAction("LOGOUT")
    @AuditAction(action = "LOGOUT", resourceType = "USER_SESSION")
    public void logout(String accessToken, HttpServletRequest request, HttpServletResponse response) {
        String cookieValue = CookieUtils.readRefreshTokenFromCookie(request);

        if (cookieValue != null && cookieValue.contains("::")) {
            String refreshToken = cookieValue.split("::")[0];

            try {
                cognitoService.globalSignOut(accessToken);
                log.info("[Auth] GlobalSignOut ok");
            } catch (CognitoIdentityProviderException e) {
                log.warn("[Auth] GlobalSignOut failed (may already be invalid): {}",
                        e.awsErrorDetails().errorMessage());
            }

            try {
                cognitoService.revokeToken(refreshToken);
                log.info("[Auth] Refresh token revoked");
            } catch (Exception e) {
                log.warn("[Auth] revokeToken failed (may already be invalid): {}", e.getMessage());
            }

            tokenBlacklistService.blacklist(accessToken);
        }

        CookieUtils.clearRefreshTokenCookie(response);
        log.info("[Auth] Logout successful");
    }
}
