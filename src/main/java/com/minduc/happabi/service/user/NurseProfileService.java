package com.minduc.happabi.service.user;

import com.minduc.happabi.dto.response.nurse.NurseProfileResponse;
import com.minduc.happabi.entity.User;
import com.minduc.happabi.exception.AppException;
import com.minduc.happabi.exception.code.UserErrorCode;
import com.minduc.happabi.mapper.UserMapper;
import com.minduc.happabi.observability.annotation.LogExecution;
import com.minduc.happabi.observability.annotation.TimedAction;
import com.minduc.happabi.repository.NurseProfileRepository;
import com.minduc.happabi.integration.s3.IS3Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class NurseProfileService {

    private final NurseProfileRepository nurseProfileRepository;
    private final UserAccountLookupService userAccountLookupService;
    private final UserCacheService userCacheService;
    private final UserMapper userMapper;
    private final IS3Service s3Service;

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('NURSE:READ')")
    @LogExecution
    @TimedAction("GET_NURSE_PROFILE")
    public NurseProfileResponse getNurseProfile() {
        String cognitoSub = userAccountLookupService.getCurrentSubOrThrow();
        NurseProfileResponse cached = userCacheService.getNurseProfile(cognitoSub).orElse(null);
        if (cached != null) {
            return cached;
        }

        User user = userAccountLookupService.findBySub(cognitoSub);
        String avatarUrl = s3Service.presign(user.getAvatarS3Key());
        NurseProfileResponse response = nurseProfileRepository.findByUser(user)
                .map(n -> userMapper.toNurseProfileResponse(n, avatarUrl))
                .orElseThrow(() -> new AppException(UserErrorCode.NURSE_PROFILE_NOT_FOUND));
        userCacheService.putNurseProfile(cognitoSub, response);
        return response;
    }
}
