package com.minduc.happabi.service.user.impl;

import com.minduc.happabi.entity.User;
import com.minduc.happabi.entity.User;
import com.minduc.happabi.enums.UserRole;
import com.minduc.happabi.exception.AppException;
import com.minduc.happabi.exception.code.AuthErrorCode;
import com.minduc.happabi.repository.UserRepository;
import com.minduc.happabi.service.user.AuthenticatedUserIdentity;
import com.minduc.happabi.service.user.IUserIdentityService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserIdentityService implements IUserIdentityService {

    private final UserRepository userRepository;

    @Override
    public AuthenticatedUserIdentity getActiveUserIdentity(String sub) {
        User user = userRepository.findByCognitoSubWithRolesAndProviders(sub)
                .orElseThrow(() -> new AppException(AuthErrorCode.USER_NOT_FOUND));
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new AppException(AuthErrorCode.ACCOUNT_DISABLED);
        }

        Set<UserRole> roles = user.getRoles().stream()
                .filter(role -> Boolean.TRUE.equals(role.getIsActive()))
                .map(role -> role.getRoleName())
                .collect(Collectors.toUnmodifiableSet());
        if (roles.isEmpty()) {
            throw new AppException(AuthErrorCode.AUTH_FAILED, "No active role is assigned to this account.");
        }
        return new AuthenticatedUserIdentity(user.getId(), roles);
    }
}
