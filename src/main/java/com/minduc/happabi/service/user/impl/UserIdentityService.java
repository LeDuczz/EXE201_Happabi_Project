package com.minduc.happabi.service.user.impl;

import com.minduc.happabi.entity.User;
import com.minduc.happabi.repository.UserRepository;
import com.minduc.happabi.service.user.IUserIdentityService;
import com.minduc.happabi.service.user.UserCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserIdentityService implements IUserIdentityService {

    private final UserCacheService userCacheService;
    private final UserRepository userRepository;

    @Override
    public UUID getUserIdByCognitoSub(String sub) {
        return userCacheService.getUserId(sub).orElseGet(() -> {
            UUID userId = userRepository.findByCognitoSub(sub)
                    .map(User::getId)
                    .orElse(null);
            if (userId != null) {
                userCacheService.putUserId(sub, userId);
            }
            return userId;
        });
    }
}
