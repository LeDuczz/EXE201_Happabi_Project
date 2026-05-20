package com.minduc.happabi.mapper;

import com.minduc.happabi.dto.response.mother.MotherProfileResponse;
import com.minduc.happabi.dto.response.nurse.NurseProfileResponse;
import com.minduc.happabi.dto.response.user.UserProfileResponse;
import com.minduc.happabi.entity.MotherProfile;
import com.minduc.happabi.entity.NurseProfile;
import com.minduc.happabi.entity.Role;
import com.minduc.happabi.entity.User;
import com.minduc.happabi.enums.UserRole;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class UserMapper {

    public UserProfileResponse toProfileResponse(User user, String avatarUrl) {
        List<String> linkedProviders = user.getIdentityProviders().stream()
                .map(p -> p.getProvider().name())
                .sorted()
                .toList();

        List<UserRole> roles = user.getRoles().stream()
                .map(Role::getRoleName)
                .toList();

        return UserProfileResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .phoneVerified(user.getPhoneVerified())
                .email(user.getEmail())
                .emailVerified(user.getEmailVerified())
                .roles(roles)
                .linkedProviders(linkedProviders)
                .avatarUrl(avatarUrl)
                .isActive(user.getIsActive())
                .build();
    }

    public MotherProfileResponse toMotherProfileResponse(MotherProfile m, String avatarUrl) {
        return MotherProfileResponse.builder()
                .id(m.getUser().getId())
                .fullName(m.getUser().getFullName())
                .phone(m.getUser().getPhone())
                .email(m.getUser().getEmail())
                .avatarUrl(avatarUrl)
                .babyBirthDate(m.getBabyBirthDate())
                .dayOfBirth(m.getDayOfBirth())
                .address(m.getAddress())
                .city(m.getCity())
                .build();
    }

    public NurseProfileResponse toNurseProfileResponse(NurseProfile n, String avatarUrl) {
        return NurseProfileResponse.builder()
                .id(n.getUser().getId())
                .fullName(n.getUser().getFullName())
                .phone(n.getUser().getPhone())
                .email(n.getUser().getEmail())
                .dayOfBirth(n.getDateOfBirth() != null ? n.getDateOfBirth().toString() : null)
                .address(n.getAddress())
                .city(n.getCity())
                .avatarUrl(avatarUrl)
                .build();
    }
}
