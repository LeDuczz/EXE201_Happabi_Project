package com.minduc.happabi.service.auth;

import com.minduc.happabi.common.utils.AuthUtils;
import com.minduc.happabi.dto.request.auth.CreateLocalPasswordRequest;
import com.minduc.happabi.dto.request.auth.RegisterRequest;
import com.minduc.happabi.dto.request.auth.ResendOtpRequest;
import com.minduc.happabi.dto.request.auth.VerifyOtpRequest;
import com.minduc.happabi.dto.response.auth.RegisterResponse;
import com.minduc.happabi.entity.Role;
import com.minduc.happabi.entity.User;
import com.minduc.happabi.enums.AuthProvider;
import com.minduc.happabi.enums.UserRole;
import com.minduc.happabi.exception.AppException;
import com.minduc.happabi.exception.code.AuthErrorCode;
import com.minduc.happabi.integration.cognito.CognitoService;
import com.minduc.happabi.observability.annotation.AuditAction;
import com.minduc.happabi.observability.annotation.LogExecution;
import com.minduc.happabi.observability.annotation.TimedAction;
import com.minduc.happabi.repository.RoleRepository;
import com.minduc.happabi.repository.UserIdentityProviderRepository;
import com.minduc.happabi.repository.UserRepository;
import com.minduc.happabi.service.user.UserCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
    private final UserIdentityProviderRepository identityProviderRepository;
    private final RoleRepository roleRepository;
    private final CognitoService cognitoService;
    private final UserProviderService userProviderService;
    private final UserCacheService userCacheService;

    @Value("${app.auth.auto-confirm-signup:false}")
    private boolean autoConfirmSignup;

    @TimedAction("REGISTER")
    @AuditAction(action = "REGISTER", resourceType = "USER")
    @LogExecution
    @Transactional
    public RegisterResponse register(RegisterRequest request) {
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
                return registrationConfirmed();
            }
            handleExistingSocialUserAsLocalRegistration();
            return registrationRequiresOtp();
        }

        String cognitoSub = signUpToCognito(request);
        User newUser = createNewLocalUser(request, userRole, cognitoSub);
        RegisterResponse response = autoConfirmSignup
                ? autoConfirmRegisteredUser(request, newUser)
                : registrationRequiresOtp();

        log.info("[Auth] User registered: phone={} role={} autoConfirmed={}",
                request.getPhone(), request.getRole(), response.isAutoConfirmed());
        return response;
    }

    private void handleExistingSocialUserAsLocalRegistration() {
        throw new AppException(AuthErrorCode.AUTH_FAILED,
                "This phone number belongs to a social account. Please sign in first, verify the phone on that " +
                        "account, then create a local password before registering the NURSE role.");
    }

    private void handleExistingLocalUser(RegisterRequest request, User existing,
                                         boolean hasRequestedRole, Role userRole) {
        if (hasRequestedRole) {
            throw new AppException(AuthErrorCode.PHONE_ALREADY_EXISTS,
                    "Role " + request.getRole().name() + " is already linked to this account.");
        }
        try {
            cognitoService.adminInitiateAuth(requireMasterUsername(existing), request.getPassword());
        } catch (NotAuthorizedException e) {
            throw new AppException(AuthErrorCode.INVALID_CREDENTIALS,
                    "Phone number already exists. Please enter the correct password to add a new role.");
        }
        String groupUsername = existing.getCognitoUsername() != null
                ? existing.getCognitoUsername()
                : request.getPhone();

        assignCognitoGroup(groupUsername, request.getRole().name());
        userProviderService.saveRoleAssignment(existing, userRole);
        userProviderService.createProfileForRole(existing, request.getRole());
        evictUserCache(existing);
        log.info("[Auth] Existing user {} assigned new role {} via phone verification",
                existing.getId(), request.getRole());
    }

    private String signUpToCognito(RegisterRequest request) {
        try {
            String cognitoSub = cognitoService.signUp(request);
            log.info("[Auth] Cognito signUp ok: phone={} sub={} role={}",
                    request.getPhone(), cognitoSub, request.getRole());
            return cognitoSub;
        } catch (UsernameExistsException e) {
            throw new AppException(AuthErrorCode.PHONE_ALREADY_EXISTS);
        } catch (InvalidPasswordException e) {
            throw new AppException(AuthErrorCode.PASSWORD_POLICY_VIOLATED);
        } catch (CognitoIdentityProviderException e) {
            log.error("[Auth] Cognito signUp error: {}", e.awsErrorDetails().errorMessage(), e);
            throw new AppException(AuthErrorCode.INVALID_CREDENTIALS, e.awsErrorDetails().errorMessage());
        }
    }

    private User createNewLocalUser(RegisterRequest request, Role userRole, String cognitoSub) {
        User newUser = User.builder()
                .fullName(request.getFullName())
                .cognitoUsername(cognitoSub)
                .cognitoSub(cognitoSub)
                .phone(request.getPhone())
                .isActive(true)
                .build();

        userRepository.save(newUser);
        userProviderService.saveIdentityProvider(newUser, AuthProvider.LOCAL, request.getPhone());
        userProviderService.saveRoleAssignment(newUser, userRole);
        assignCognitoGroup(request.getPhone(), request.getRole().name());
        userProviderService.createProfileForRole(newUser, request.getRole());
        return newUser;
    }

    private RegisterResponse autoConfirmRegisteredUser(RegisterRequest request, User user) {
        try {
            cognitoService.adminConfirmSignUp(request.getPhone());
            cognitoService.adminUpdatePhoneNumber(request.getPhone(), request.getPhone(), true);
            user.setPhoneVerified(true);
            userRepository.save(user);
            evictUserCache(user);
            log.warn("[Auth] Auto-confirmed signup for demo mode: phone={} role={}",
                    request.getPhone(), request.getRole());
            return registrationConfirmed();
        } catch (CognitoIdentityProviderException e) {
            log.error("[Auth] adminConfirmSignUp error: {}", e.awsErrorDetails().errorMessage(), e);
            throw new AppException(AuthErrorCode.AUTH_FAILED, e.awsErrorDetails().errorMessage());
        }
    }

    private RegisterResponse registrationConfirmed() {
        return RegisterResponse.builder()
                .autoConfirmed(true)
                .otpRequired(false)
                .build();
    }

    private RegisterResponse registrationRequiresOtp() {
        return RegisterResponse.builder()
                .autoConfirmed(false)
                .otpRequired(true)
                .build();
    }

    @TimedAction("VERIFY_OTP")
    @AuditAction(action = "VERIFY_OTP", resourceType = "USER")
    @LogExecution
    public void verifyOtp(VerifyOtpRequest request) {
        try {
            cognitoService.confirmSignUp(request.getPhone(), request.getOtpCode());
            userRepository.findByPhone(request.getPhone()).ifPresent(user -> {
                user.setPhoneVerified(true);
                userRepository.save(user);
                evictUserCache(user);
            });

            log.info("[Auth] OTP verified ok: phone={}", request.getPhone());

        } catch (CodeMismatchException e) {
            throw new AppException(AuthErrorCode.OTP_INVALID);
        } catch (ExpiredCodeException e) {
            throw new AppException(AuthErrorCode.OTP_EXPIRED);
        } catch (NotAuthorizedException e) {
            throw new AppException(AuthErrorCode.USER_ALREADY_CONFIRMED);
        } catch (CognitoIdentityProviderException e) {
            log.error("[Auth] confirmSignUp error: {}", e.awsErrorDetails().errorMessage(), e);
            throw new AppException(AuthErrorCode.OTP_INVALID, e.awsErrorDetails().errorMessage());
        }
    }

    @TimedAction("RESEND_OTP")
    @LogExecution
    @AuditAction(action = "RESEND_OTP", resourceType = "USER")
    public void resendOtp(ResendOtpRequest request) {
        try {
            cognitoService.resendConfirmationCode(request.getPhone());

            log.info("[Auth] OTP resent: phone={}", request.getPhone());

        } catch (UserNotFoundException e) {
            throw new AppException(AuthErrorCode.USER_NOT_FOUND);
        } catch (NotAuthorizedException e) {
            throw new AppException(AuthErrorCode.USER_ALREADY_CONFIRMED);
        } catch (CognitoIdentityProviderException e) {
            log.error("[Auth] resendConfirmationCode error: {}", e.awsErrorDetails().errorMessage(), e);
            throw new AppException(AuthErrorCode.OTP_INVALID, e.awsErrorDetails().errorMessage());
        }
    }

    @TimedAction("CREATE_LOCAL_PASSWORD")
    @Transactional
    @LogExecution
    @AuditAction(action = "CREATE_LOCAL_PASSWORD", resourceType = "USER")
    public void createLocalPassword(CreateLocalPasswordRequest request) {
        String cognitoSub = AuthUtils.getCurrentSub()
                .orElseThrow(() -> new AppException(AuthErrorCode.AUTH_FAILED));

        User user = userRepository.findByCognitoSubWithRolesAndProviders(cognitoSub)
                .or(() -> identityProviderRepository.findUserByProviderUidWithRolesAndProviders(cognitoSub))
                .orElseThrow(() -> new AppException(AuthErrorCode.USER_NOT_FOUND));

        if (user.hasProvider(AuthProvider.LOCAL)) {
            throw new AppException(AuthErrorCode.AUTH_FAILED, "Local password is already enabled.");
        }
        if (user.getPhone() == null || !Boolean.TRUE.equals(user.getPhoneVerified())) {
            throw new AppException(AuthErrorCode.AUTH_FAILED,
                    "Phone number must be verified before creating a local password.");
        }

        String masterUsername = requireMasterUsername(user);
        try {
            cognitoService.adminSetPermanentPassword(masterUsername, request.getPassword());
        } catch (InvalidPasswordException e) {
            throw new AppException(AuthErrorCode.PASSWORD_POLICY_VIOLATED);
        } catch (CognitoIdentityProviderException e) {
            log.error("[Auth] Failed to create local password for user {}: {}",
                    user.getId(), e.awsErrorDetails().errorMessage(), e);
            throw new AppException(AuthErrorCode.AUTH_FAILED, e.awsErrorDetails().errorMessage());
        }

        userProviderService.saveIdentityProvider(user, AuthProvider.LOCAL, masterUsername);
        userRepository.save(user);
        evictUserCache(user);
        log.info("[Auth] Local password enabled for user {}", user.getId());
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

    private String requireMasterUsername(User user) {
        if (user.getCognitoUsername() != null && !user.getCognitoUsername().isBlank()) {
            return user.getCognitoUsername();
        }
        if (user.getPhone() != null && !user.getPhone().isBlank()) {
            return user.getPhone();
        }
        throw new AppException(AuthErrorCode.AUTH_FAILED, "Missing Cognito master username for user " + user.getId());
    }

    private void evictUserCache(User user) {
        if (user.getCognitoSub() != null && !user.getCognitoSub().isBlank()) {
            userCacheService.evictProfiles(user.getCognitoSub());
        }
    }
}
