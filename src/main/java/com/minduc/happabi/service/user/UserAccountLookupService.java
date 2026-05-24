package com.minduc.happabi.service.user;

import com.minduc.happabi.common.utils.AuthUtils;
import com.minduc.happabi.entity.User;
import com.minduc.happabi.exception.AppException;
import com.minduc.happabi.exception.code.AuthErrorCode;
import com.minduc.happabi.exception.code.UserErrorCode;
import com.minduc.happabi.repository.UserIdentityProviderRepository;
import com.minduc.happabi.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserAccountLookupService {

    private final UserRepository userRepository;
    private final UserIdentityProviderRepository identityProviderRepository;

    public String getCurrentSubOrThrow() {
        return AuthUtils.getCurrentSub()
                .orElseThrow(() -> new AppException(AuthErrorCode.USER_NOT_FOUND));
    }

    public User getCurrentUser() {
        return findBySub(getCurrentSubOrThrow());
    }

    public User findBySub(String cognitoSub) {
        return userRepository.findByCognitoSubWithRolesAndProviders(cognitoSub)
                .or(() -> identityProviderRepository.findUserByProviderUidWithRolesAndProviders(cognitoSub))
                .orElseThrow(() -> new AppException(AuthErrorCode.USER_NOT_FOUND));
    }

    public String requireAccessToken() {
        return AuthUtils.getJwt()
                .map(jwt -> jwt.getTokenValue())
                .orElseThrow(() -> new AppException(AuthErrorCode.AUTH_FAILED));
    }

    public void ensureEmailAvailableForUser(String email, User currentUser) {
        if (email == null || email.isBlank()) {
            return;
        }
        userRepository.findByEmail(email.trim().toLowerCase())
                .filter(existing -> !existing.getId().equals(currentUser.getId()))
                .ifPresent(existing -> {
                    throw new AppException(UserErrorCode.EMAIL_ALREADY_SET,
                            "Email is already verified by another account.");
                });
    }

    public void ensurePhoneAvailableForUser(String phone, User currentUser) {
        if (phone == null || phone.isBlank()) {
            return;
        }
        userRepository.findByPhone(phone)
                .filter(existing -> !existing.getId().equals(currentUser.getId()))
                .ifPresent(existing -> {
                    throw new AppException(UserErrorCode.PHONE_ALREADY_SET,
                            "Phone is already verified by another account.");
                });
    }
}
