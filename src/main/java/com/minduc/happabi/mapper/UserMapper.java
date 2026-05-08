package com.minduc.happabi.mapper;

import com.minduc.happabi.dto.response.UserProfileResponse;
import com.minduc.happabi.entity.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public UserProfileResponse toProfileResponse(User user, String avatarUrl) {
        return UserProfileResponse.builder()
                .id(user.getId())
                .cognitoSub(user.getCognitoSub())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .email(user.getEmail())
                .role(user.getRole().getRoleName())
                .authProvider(user.getAuthProvider())
                .avatarUrl(avatarUrl)
                .isActive(user.getIsActive())
                .build();
    }
}
