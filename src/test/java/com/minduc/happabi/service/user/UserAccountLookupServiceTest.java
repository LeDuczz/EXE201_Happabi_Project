package com.minduc.happabi.service.user;

import com.minduc.happabi.dto.UserDTO;
import com.minduc.happabi.entity.Role;
import com.minduc.happabi.entity.User;
import com.minduc.happabi.entity.UserRoleAssignment;
import com.minduc.happabi.enums.UserRole;
import com.minduc.happabi.repository.UserIdentityProviderRepository;
import com.minduc.happabi.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAccountLookupServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserIdentityProviderRepository identityProviderRepository;

    @Mock
    private AdminUserListCacheService adminUserListCacheService;

    @InjectMocks
    private UserAccountLookupService service;

    @Test
    void getAllUsersReturnsCachedPageWithoutQueryingDatabase() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<UserDTO> cached = new PageImpl<>(List.of(UserDTO.builder()
                .id(UUID.randomUUID())
                .fullName("Cached Mother")
                .roles(List.of("MOTHER"))
                .build()), pageable, 1);
        when(adminUserListCacheService.get("mother", pageable)).thenReturn(Optional.of(cached));

        Page<UserDTO> result = service.getAllUsers("mother", pageable);

        assertThat(result).isSameAs(cached);
        verify(userRepository, never()).findAll(pageable);
    }

    @Test
    void getAllUsersCachesDatabaseResultWhenCacheMisses() {
        Pageable pageable = PageRequest.of(0, 20);
        User user = user("Nguyen Mother", UserRole.MOTHER);
        when(adminUserListCacheService.get(null, pageable)).thenReturn(Optional.empty());
        when(userRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(user), pageable, 1));

        Page<UserDTO> result = service.getAllUsers(null, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().getFullName()).isEqualTo("Nguyen Mother");
        assertThat(result.getContent().getFirst().getRoles()).containsExactly("MOTHER");
        verify(adminUserListCacheService).put(null, pageable, result);
    }

    @Test
    void toggleUserStatusSavesUserAndEvictsAdminListCache() {
        UUID userId = UUID.randomUUID();
        User user = user("Active User", UserRole.NURSE);
        user.setId(userId);
        user.setIsActive(true);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        service.toggleUserStatus(userId);

        assertThat(user.getIsActive()).isFalse();
        verify(userRepository).save(user);
        verify(adminUserListCacheService).evictAll();
    }

    private User user(String fullName, UserRole roleName) {
        User user = User.builder()
                .id(UUID.randomUUID())
                .fullName(fullName)
                .phone("0912345678")
                .email("user@happabi.local")
                .isActive(true)
                .createdAt(OffsetDateTime.parse("2026-08-04T09:00:00+07:00"))
                .roleAssignments(new HashSet<>())
                .build();
        Role role = Role.builder()
                .id(UUID.randomUUID())
                .roleName(roleName)
                .build();
        user.getRoleAssignments().add(UserRoleAssignment.builder()
                .id(UUID.randomUUID())
                .user(user)
                .role(role)
                .build());
        return user;
    }
}
