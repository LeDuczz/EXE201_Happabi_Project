package com.minduc.happabi.service.user;

import com.minduc.happabi.dto.response.user.UserProfileResponse;
import com.minduc.happabi.entity.User;
import com.minduc.happabi.mapper.UserMapper;
import com.minduc.happabi.observability.annotation.LogExecution;
import com.minduc.happabi.observability.annotation.TimedAction;
import com.minduc.happabi.integration.s3.IS3Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserAccountLookupService userAccountLookupService;
    private final UserCacheService userCacheService;
    private final UserMapper userMapper;
    private final IS3Service s3Service;

    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    @LogExecution
    @TimedAction("GET_CURRENT_USER_PROFILE")
    public UserProfileResponse getMe() {
        String cognitoSub = userAccountLookupService.getCurrentSubOrThrow();
        UserProfileResponse cached = userCacheService.getUserProfile(cognitoSub).orElse(null);
        if (cached != null) {
            return cached;
        }

        User user = userAccountLookupService.findBySub(cognitoSub);
        String avatarUrl = s3Service.presign(user.getAvatarS3Key());
        UserProfileResponse response = userMapper.toProfileResponse(user, avatarUrl);
        userCacheService.putUserProfile(cognitoSub, response);
        return response;
    }
}
