package com.minduc.happabi.service.auth;

import com.minduc.happabi.entity.MotherProfile;
import com.minduc.happabi.entity.NurseProfile;
import com.minduc.happabi.entity.Role;
import com.minduc.happabi.entity.User;
import com.minduc.happabi.entity.UserIdentityProvider;
import com.minduc.happabi.entity.UserRoleAssignment;
import com.minduc.happabi.enums.AuthProvider;
import com.minduc.happabi.enums.UserRole;
import com.minduc.happabi.repository.MotherProfileRepository;
import com.minduc.happabi.repository.NurseProfileRepository;
import com.minduc.happabi.repository.UserIdentityProviderRepository;
import com.minduc.happabi.repository.UserRoleAssignmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserProviderService {

    private final UserIdentityProviderRepository identityProviderRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final MotherProfileRepository motherProfileRepository;
    private final NurseProfileRepository nurseProfileRepository;

    public void saveIdentityProvider(User user, AuthProvider provider, String providerUid) {
        if (providerUid == null || providerUid.isBlank()) {
            throw new IllegalArgumentException("providerUid is required");
        }

        identityProviderRepository.findByUserAndProvider(user, provider)
                .ifPresentOrElse(existing -> {
                    if (!providerUid.equals(existing.getProviderUid())) {
                        existing.setProviderUid(providerUid);
                        identityProviderRepository.save(existing);
                        user.addOrUpdateIdentityProvider(provider, providerUid);
                    }
                }, () -> {
                    UserIdentityProvider link = UserIdentityProvider.builder()
                            .user(user)
                            .provider(provider)
                            .providerUid(providerUid)
                            .build();
                    identityProviderRepository.save(link);
                    user.getIdentityProviders().add(link);
                });
    }

    public void saveRoleAssignment(User user, Role role) {
        if (user.hasRole(role.getRoleName())) {
            return;
        }
        user.addRoleAssignment(role);
        UserRoleAssignment assignment = user.getRoleAssignments().stream()
                .filter(item -> item.getRole().getRoleName() == role.getRoleName())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Role assignment was not created"));
        userRoleAssignmentRepository.save(assignment);
    }

    public void createProfileForRole(User user, UserRole roleName) {
        if (UserRole.MOTHER.equals(roleName)) {
            if (motherProfileRepository.findByUser(user).isPresent()) {
                return;
            }
            motherProfileRepository.save(MotherProfile.builder().user(user).build());
        } else if (UserRole.NURSE.equals(roleName)) {
            if (nurseProfileRepository.findByUser(user).isPresent()) {
                return;
            }
            nurseProfileRepository.save(NurseProfile.builder().user(user).build());
        }
    }
}
