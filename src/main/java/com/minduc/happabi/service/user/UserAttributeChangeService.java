package com.minduc.happabi.service.user;

import com.minduc.happabi.dto.request.user.ConfirmUserAttributeRequest;
import com.minduc.happabi.dto.request.user.RequestEmailChangeRequest;
import com.minduc.happabi.dto.request.user.RequestPhoneChangeRequest;
import com.minduc.happabi.dto.response.user.UserAttributeChangeResponse;
import com.minduc.happabi.dto.response.user.UserProfileResponse;
import com.minduc.happabi.entity.User;
import com.minduc.happabi.exception.AppException;
import com.minduc.happabi.exception.code.AuthErrorCode;
import com.minduc.happabi.exception.code.UserErrorCode;
import com.minduc.happabi.mapper.UserMapper;
import com.minduc.happabi.observability.annotation.AuditAction;
import com.minduc.happabi.observability.annotation.LogExecution;
import com.minduc.happabi.observability.annotation.TimedAction;
import com.minduc.happabi.repository.UserRepository;
import com.minduc.happabi.integration.cognito.CognitoService;
import com.minduc.happabi.integration.s3.IS3Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CodeMismatchException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CognitoIdentityProviderException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ExpiredCodeException;

import java.time.OffsetDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserAttributeChangeService {

    private final UserRepository userRepository;
    private final UserAccountLookupService userAccountLookupService;
    private final UserCacheService userCacheService;
    private final UserMapper userMapper;
    private final IS3Service s3Service;
    private final CognitoService cognitoService;

    @Value("${app.auth.auto-confirm-phone-change:false}")
    private boolean autoConfirmPhoneChange;

    @Transactional
    @PreAuthorize("isAuthenticated()")
    @LogExecution
    @TimedAction("REQUEST_EMAIL_CHANGE")
    @AuditAction(action = "REQUEST_EMAIL_CHANGE", resourceType = "USER_PROFILE")
    public void requestEmailChange(RequestEmailChangeRequest request) {
        User user = userAccountLookupService.getCurrentUser();
        if (Boolean.TRUE.equals(user.getEmailVerified())) {
            throw new AppException(UserErrorCode.EMAIL_ALREADY_SET,
                    "Email is already verified and cannot be changed.");
        }
        String normalizedEmail = request.getEmail().trim().toLowerCase();
        if (user.getEmail() != null && !user.getEmail().equalsIgnoreCase(normalizedEmail)) {
            throw new AppException(UserErrorCode.EMAIL_ALREADY_SET);
        }
        userAccountLookupService.ensureEmailAvailableForUser(normalizedEmail, user);
        cognitoService.updateEmailWithVerification(userAccountLookupService.requireAccessToken(), normalizedEmail);
        cognitoService.resendAttributeVerificationCode(userAccountLookupService.requireAccessToken(), "email");
    }

    @Transactional
    @PreAuthorize("isAuthenticated()")
    @LogExecution
    @TimedAction("CONFIRM_EMAIL_CHANGE")
    public UserProfileResponse confirmEmailChange(ConfirmUserAttributeRequest request) {
        String cognitoSub = userAccountLookupService.getCurrentSubOrThrow();
        verifyUserAttribute("email", request.getCode());

        User user = userAccountLookupService.findBySub(cognitoSub);
        String verifiedEmail = cognitoService.getCurrentUserAttribute(userAccountLookupService.requireAccessToken(), "email");
        userAccountLookupService.ensureEmailAvailableForUser(verifiedEmail, user);
        user.setEmail(verifiedEmail);
        user.setEmailVerified(true);
        user.setUpdatedAt(OffsetDateTime.now());
        userRepository.save(user);
        userCacheService.evictProfiles(cognitoSub);
        return userMapper.toProfileResponse(user, s3Service.presign(user.getAvatarS3Key()));
    }

    @Transactional
    @PreAuthorize("isAuthenticated()")
    @LogExecution
    @TimedAction("REQUEST_PHONE_CHANGE")
    @AuditAction(action = "REQUEST_PHONE_CHANGE", resourceType = "USER_PROFILE")
    public UserAttributeChangeResponse requestPhoneChange(RequestPhoneChangeRequest request) {
        User user = userAccountLookupService.getCurrentUser();
        if (Boolean.TRUE.equals(user.getPhoneVerified())) {
            throw new AppException(UserErrorCode.PHONE_ALREADY_SET,
                    "Phone number is already verified and cannot be changed.");
        }
        if (user.getPhone() != null && !user.getPhone().equals(request.getPhone())) {
            throw new AppException(UserErrorCode.PHONE_ALREADY_SET);
        }
        userAccountLookupService.ensurePhoneAvailableForUser(request.getPhone(), user);
        if (autoConfirmPhoneChange) {
            autoConfirmPhoneChange(user, request.getPhone());
            return phoneAutoConfirmed();
        }
        cognitoService.updatePhoneWithVerification(userAccountLookupService.requireAccessToken(), request.getPhone());
        cognitoService.resendAttributeVerificationCode(userAccountLookupService.requireAccessToken(), "phone_number");
        return phoneVerificationRequired();
    }

    @Transactional
    @PreAuthorize("isAuthenticated()")
    @LogExecution
    @TimedAction("CONFIRM_PHONE_CHANGE")
    public UserProfileResponse confirmPhoneChange(ConfirmUserAttributeRequest request) {
        String cognitoSub = userAccountLookupService.getCurrentSubOrThrow();
        verifyUserAttribute("phone_number", request.getCode());

        User user = userAccountLookupService.findBySub(cognitoSub);
        String verifiedPhone = cognitoService.getCurrentUserAttribute(userAccountLookupService.requireAccessToken(), "phone_number");
        userAccountLookupService.ensurePhoneAvailableForUser(verifiedPhone, user);
        user.setPhone(verifiedPhone);
        user.setPhoneVerified(true);
        user.setUpdatedAt(OffsetDateTime.now());
        userRepository.save(user);
        userCacheService.evictProfiles(cognitoSub);
        return userMapper.toProfileResponse(user, s3Service.presign(user.getAvatarS3Key()));
    }
    private UserAttributeChangeResponse phoneAutoConfirmed() {
        return UserAttributeChangeResponse.builder()
                .autoConfirmed(true)
                .verificationRequired(false)
                .build();
    }

    private UserAttributeChangeResponse phoneVerificationRequired() {
        return UserAttributeChangeResponse.builder()
                .autoConfirmed(false)
                .verificationRequired(true)
                .build();
    }

    private void autoConfirmPhoneChange(User user, String phone) {
        String username = resolveCognitoAdminUsername(user);
        try {
            cognitoService.adminUpdatePhoneNumber(username, phone, true);
            user.setPhone(phone);
            user.setPhoneVerified(true);
            user.setUpdatedAt(OffsetDateTime.now());
            userRepository.save(user);
            if (user.getCognitoSub() != null && !user.getCognitoSub().isBlank()) {
                userCacheService.evictProfiles(user.getCognitoSub());
            }
            log.warn("[UserAttribute] Auto-confirmed phone change for demo mode: userId={} phone={}",
                    user.getId(), phone);
        } catch (CognitoIdentityProviderException e) {
            log.error("[UserAttribute] adminUpdatePhoneNumber error: {}", e.awsErrorDetails().errorMessage(), e);
            throw new AppException(AuthErrorCode.AUTH_FAILED, e.awsErrorDetails().errorMessage());
        }
    }

    private String resolveCognitoAdminUsername(User user) {
        if (user.getPhone() != null && !user.getPhone().isBlank()) {
            return user.getPhone();
        }
        if (user.getCognitoUsername() != null && !user.getCognitoUsername().isBlank()) {
            return user.getCognitoUsername();
        }
        if (user.getCognitoSub() != null && !user.getCognitoSub().isBlank()) {
            return user.getCognitoSub();
        }
        throw new AppException(AuthErrorCode.AUTH_FAILED, "Missing Cognito username for user " + user.getId());
    }
    private void verifyUserAttribute(String attributeName, String code) {
        try {
            cognitoService.verifyUserAttribute(userAccountLookupService.requireAccessToken(), attributeName, code);
        } catch (CodeMismatchException e) {
            throw new AppException(AuthErrorCode.OTP_INVALID);
        } catch (ExpiredCodeException e) {
            throw new AppException(AuthErrorCode.OTP_EXPIRED);
        } catch (CognitoIdentityProviderException e) {
            log.warn("[UserAttribute] Cognito rejected {} verification: code={} message={}",
                    attributeName, e.awsErrorDetails().errorCode(), e.awsErrorDetails().errorMessage());
            throw new AppException(AuthErrorCode.OTP_INVALID, e.awsErrorDetails().errorMessage());
        }
    }
}
