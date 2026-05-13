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
        UserIdentityProvider link = UserIdentityProvider.builder()
                .user(user)
                .provider(provider)
                .providerUid(providerUid)
                .build();
        identityProviderRepository.save(link);
    }

    public void saveRoleAssignment(User user, Role role) {
        UserRoleAssignment assignment = UserRoleAssignment.builder()
                .user(user)
                .role(role)
                .build();
        userRoleAssignmentRepository.save(assignment);
    }

    public void createProfileForRole(User user, UserRole roleName) {
        if (UserRole.MOTHER.equals(roleName)) {
            motherProfileRepository.save(MotherProfile.builder().user(user).build());
        } else if (UserRole.NURSE.equals(roleName)) {
            nurseProfileRepository.save(NurseProfile.builder().user(user).build());
        }
    }
}
