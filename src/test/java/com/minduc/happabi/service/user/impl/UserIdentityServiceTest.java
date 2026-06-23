package com.minduc.happabi.service.user.impl;

import com.minduc.happabi.entity.Role;
import com.minduc.happabi.entity.User;
import com.minduc.happabi.entity.UserRoleAssignment;
import com.minduc.happabi.enums.UserRole;
import com.minduc.happabi.exception.AppException;
import com.minduc.happabi.exception.code.AuthErrorCode;
import com.minduc.happabi.repository.UserRepository;
import com.minduc.happabi.service.user.AuthenticatedUserIdentity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserIdentityServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserIdentityService service;

    @Test
    void resolvesOnlyActiveDatabaseRoles() {
        UUID userId = UUID.randomUUID();
        User user = user(userId, true, true, UserRole.MOTHER);
        when(userRepository.findByCognitoSubWithRolesAndProviders("subject"))
                .thenReturn(Optional.of(user));

        AuthenticatedUserIdentity identity = service.getActiveUserIdentity("subject");

        assertThat(identity.userId()).isEqualTo(userId);
        assertThat(identity.roles()).containsExactly(UserRole.MOTHER);
    }

    @Test
    void rejectsDisabledAccountEvenWhenItHasRoles() {
        when(userRepository.findByCognitoSubWithRolesAndProviders("subject"))
                .thenReturn(Optional.of(user(UUID.randomUUID(), false, true, UserRole.ADMIN)));

        assertThatThrownBy(() -> service.getActiveUserIdentity("subject"))
                .isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).getErrorCode())
                .isEqualTo(AuthErrorCode.ACCOUNT_DISABLED);
    }

    @Test
    void rejectsAccountWithoutAnActiveRole() {
        when(userRepository.findByCognitoSubWithRolesAndProviders("subject"))
                .thenReturn(Optional.of(user(UUID.randomUUID(), true, false, UserRole.MOTHER)));

        assertThatThrownBy(() -> service.getActiveUserIdentity("subject"))
                .isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).getErrorCode())
                .isEqualTo(AuthErrorCode.AUTH_FAILED);
    }

    private User user(UUID userId, boolean active, boolean roleActive, UserRole roleName) {
        Role role = Role.builder().roleName(roleName).isActive(roleActive).build();
        User user = User.builder().id(userId).isActive(active).build();
        UserRoleAssignment assignment = UserRoleAssignment.builder().user(user).role(role).build();
        user.setRoleAssignments(Set.of(assignment));
        return user;
    }
}
