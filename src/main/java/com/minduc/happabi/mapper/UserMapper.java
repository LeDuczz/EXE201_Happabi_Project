package com.minduc.happabi.mapper;

import com.minduc.happabi.dto.response.mother.MotherProfileResponse;
import com.minduc.happabi.dto.response.nurse.NurseProfileResponse;
import com.minduc.happabi.dto.response.user.UserProfileResponse;
import com.minduc.happabi.entity.MotherProfile;
import com.minduc.happabi.entity.NurseProfile;
import com.minduc.happabi.entity.Role;
import com.minduc.happabi.entity.User;
import com.minduc.happabi.entity.UserIdentityProvider;
import com.minduc.happabi.enums.UserRole;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.LocalDate;
import java.util.List;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "roles", expression = "java(toRoleNames(user.getRoles()))")
    @Mapping(target = "linkedProviders", expression = "java(toProviderNames(user.getIdentityProviders()))")
    @Mapping(target = "avatarUrl", source = "avatarUrl")
    UserProfileResponse toProfileResponse(User user, String avatarUrl);

    @Mapping(target = "id", source = "profile.user.id")
    @Mapping(target = "fullName", source = "profile.user.fullName")
    @Mapping(target = "phone", source = "profile.user.phone")
    @Mapping(target = "email", source = "profile.user.email")
    @Mapping(target = "avatarUrl", source = "avatarUrl")
    MotherProfileResponse toMotherProfileResponse(MotherProfile profile, String avatarUrl);

    @Mapping(target = "id", source = "profile.user.id")
    @Mapping(target = "fullName", source = "profile.user.fullName")
    @Mapping(target = "phone", source = "profile.user.phone")
    @Mapping(target = "email", source = "profile.user.email")
    @Mapping(target = "dayOfBirth", expression = "java(toDateString(profile.getDateOfBirth()))")
    @Mapping(target = "avatarUrl", source = "avatarUrl")
    NurseProfileResponse toNurseProfileResponse(NurseProfile profile, String avatarUrl);

    default List<UserRole> toRoleNames(List<Role> roles) {
        if (roles == null) {
            return List.of();
        }
        return roles.stream()
                .map(Role::getRoleName)
                .toList();
    }

    default List<String> toProviderNames(Iterable<UserIdentityProvider> providers) {
        if (providers == null) {
            return List.of();
        }
        List<String> names = new java.util.ArrayList<>();
        providers.forEach(provider -> names.add(provider.getProvider().name()));
        return names.stream()
                .sorted()
                .toList();
    }

    default String toDateString(LocalDate value) {
        return value == null ? null : value.toString();
    }
}
