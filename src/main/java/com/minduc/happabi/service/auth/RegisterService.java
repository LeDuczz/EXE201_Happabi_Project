package com.minduc.happabi.service.auth;

import com.minduc.happabi.dto.request.auth.RegisterRequest;
import com.minduc.happabi.dto.request.auth.ResendOtpRequest;
import com.minduc.happabi.dto.request.auth.VerifyOtpRequest;
import com.minduc.happabi.entity.Role;
import com.minduc.happabi.entity.User;
import com.minduc.happabi.enums.AuthProvider;
import com.minduc.happabi.enums.UserRole;
import com.minduc.happabi.exception.AppException;
import com.minduc.happabi.exception.code.AuthErrorCode;
import com.minduc.happabi.repository.RoleRepository;
import com.minduc.happabi.repository.UserRepository;
import com.minduc.happabi.service.metrics.AuthMetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CodeMismatchException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CognitoIdentityProviderException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ExpiredCodeException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InvalidPasswordException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.NotAuthorizedException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UsernameExistsException;

import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class RegisterService {

    private static final Set<UserRole> LOCAL_ALLOWED_ROLES = Set.of(UserRole.MOTHER, UserRole.NURSE);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final CognitoService cognitoService;
    private final UserProviderService userProviderService;
    private final AuthMetricsService authMetrics;

    @Transactional
    public void register(RegisterRequest request) {
        if (!LOCAL_ALLOWED_ROLES.contains(request.getRole())) {
            throw new AppException(AuthErrorCode.INVALID_ROLE_FOR_REGISTRATION);
        }
        Role userRole = roleRepository.findByRoleName(request.getRole())
                .orElseThrow(() -> new AppException(AuthErrorCode.AUTH_FAILED, "Role not found"));

        Optional<User> existingByPhone = userRepository
                .findByPhoneWithRolesAndProviders(request.getPhone());

        if (existingByPhone.isPresent()) {
            User existing = existingByPhone.get();
            boolean hasRequestedRole = existing.getRoles().stream()
                    .anyMatch(r -> r.getRoleName() == request.getRole());

            if (existing.hasProvider(AuthProvider.LOCAL)) {
                handleExistingLocalUser(request, existing, hasRequestedRole, userRole);
                return;
            }
            log.info("[Auth] Existing user with social login found for phone {}. " +
                    "Will attempt to link LOCAL after OTP verification.", request.getPhone());
        }

        String cognitoSub = signUpToCognito(request);
        linkOrCreateUser(request, existingByPhone, userRole, cognitoSub);

        authMetrics.recordRegisterSuccess(request.getRole().name());
        log.info("[Auth] User registered: phone={} role={}", request.getPhone(), request.getRole());
    }

    private void handleExistingLocalUser(RegisterRequest request, User existing,
                                         boolean hasRequestedRole, Role userRole) {
        if (hasRequestedRole) {
            throw new AppException(AuthErrorCode.PHONE_ALREADY_EXISTS,
                    "Vai trò " + request.getRole().name() + " đã được liên kết với tài khoản này.");
        }
        try {
            cognitoService.adminInitiateAuth(request.getPhone(), request.getPassword());
        } catch (NotAuthorizedException e) {
            authMetrics.recordRegisterFailure("INVALID_CREDENTIALS_FOR_ROLE_ADD");
            throw new AppException(AuthErrorCode.INVALID_CREDENTIALS,
                    "Số điện thoại đã tồn tại. Vui lòng nhập đúng mật khẩu để thêm vai trò mới.");
        }
        assignCognitoGroup(request.getPhone(), request.getRole().name());
        userProviderService.saveRoleAssignment(existing, userRole);
        userProviderService.createProfileForRole(existing, request.getRole());
        log.info("[Auth] Existing user {} assigned new role {} via phone verification",
                existing.getId(), request.getRole());
        authMetrics.recordRegisterSuccess(request.getRole().name());
    }

    private String signUpToCognito(RegisterRequest request) {
        try {
            String cognitoSub = cognitoService.signUp(request);
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
            userProviderService.saveIdentityProvider(existing, AuthProvider.LOCAL, cognitoSub);
            boolean hasRequestedRole = existing.getRoles().stream()
                    .anyMatch(r -> r.getRoleName() == request.getRole());
            if (!hasRequestedRole) {
                userProviderService.saveRoleAssignment(existing, userRole);
                userProviderService.createProfileForRole(existing, request.getRole());
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
            userProviderService.saveIdentityProvider(newUser, AuthProvider.LOCAL, cognitoSub);
            userProviderService.saveRoleAssignment(newUser, userRole);
            assignCognitoGroup(request.getPhone(), request.getRole().name());
            userProviderService.createProfileForRole(newUser, request.getRole());
        }
    }

    public void verifyOtp(VerifyOtpRequest request) {
        try {
            cognitoService.confirmSignUp(request.getPhone(), request.getOtpCode());

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

    public void resendOtp(ResendOtpRequest request) {
        try {
            cognitoService.resendConfirmationCode(request.getPhone());

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
