package com.minduc.happabi.service.user;

import com.minduc.happabi.dto.request.mother.UpdateMotherProfileRequest;
import com.minduc.happabi.dto.response.mother.MotherProfileResponse;
import com.minduc.happabi.entity.MotherProfile;
import com.minduc.happabi.entity.User;
import com.minduc.happabi.exception.AppException;
import com.minduc.happabi.exception.code.UserErrorCode;
import com.minduc.happabi.mapper.UserMapper;
import com.minduc.happabi.observability.annotation.LogExecution;
import com.minduc.happabi.observability.annotation.TimedAction;
import com.minduc.happabi.observability.annotation.AuditAction;
import com.minduc.happabi.repository.MotherProfileRepository;
import com.minduc.happabi.repository.UserRepository;
import com.minduc.happabi.integration.s3.IS3Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class MotherProfileAccountService {

    private final UserRepository userRepository;
    private final MotherProfileRepository motherProfileRepository;
    private final UserAccountLookupService userAccountLookupService;
    private final UserCacheService userCacheService;
    private final UserMapper userMapper;
    private final IS3Service s3Service;

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('USER:READ')")
    @LogExecution
    @TimedAction("GET_MOTHER_PROFILE")
    public MotherProfileResponse getMotherProfile() {
        String cognitoSub = userAccountLookupService.getCurrentSubOrThrow();
        MotherProfileResponse cached = userCacheService.getMotherProfile(cognitoSub).orElse(null);
        if (cached != null) {
            return cached;
        }

        User user = userAccountLookupService.findBySub(cognitoSub);
        String avatarUrl = s3Service.presign(user.getAvatarS3Key());
        MotherProfileResponse response = motherProfileRepository.findByUser(user)
                .map(m -> userMapper.toMotherProfileResponse(m, avatarUrl))
                .orElseThrow(() -> new AppException(UserErrorCode.MOTHER_PROFILE_NOT_FOUND));
        userCacheService.putMotherProfile(cognitoSub, response);
        return response;
    }

    @Transactional
    @PreAuthorize("hasAuthority('USER:UPDATE')")
    @LogExecution
    @TimedAction("UPDATE_MOTHER_PROFILE")
    @AuditAction(action = "UPDATE_MOTHER_PROFILE", resourceType = "MOTHER_PROFILE")
    public MotherProfileResponse updateMotherProfile(UpdateMotherProfileRequest request) {
        String cognitoSub = userAccountLookupService.getCurrentSubOrThrow();
        User user = userAccountLookupService.findBySub(cognitoSub);
        MotherProfile profile = motherProfileRepository.findByUser(user)
                .orElseThrow(() -> new AppException(UserErrorCode.MOTHER_PROFILE_NOT_FOUND));

        OffsetDateTime now = OffsetDateTime.now();

        if (request.getFullName() != null) {
            user.setFullName(request.getFullName());
            user.setUpdatedAt(now);
        }
        rejectVerifiedAttributeUpdate(request.getPhone(), request.getEmail());

        if (request.getBabyBirthDate() != null) {
            profile.setBabyBirthDate(request.getBabyBirthDate());
        }
        if (request.getDayOfBirth() != null) {
            profile.setDayOfBirth(request.getDayOfBirth());
        }
        if (request.getAddress() != null) {
            profile.setAddress(request.getAddress());
        }
        if (request.getCity() != null) {
            profile.setCity(request.getCity());
        }
        profile.setUpdatedAt(now);

        userRepository.save(user);
        MotherProfile savedProfile = motherProfileRepository.save(profile);
        userCacheService.evictProfiles(cognitoSub);

        String avatarUrl = s3Service.presign(user.getAvatarS3Key());
        MotherProfileResponse response = userMapper.toMotherProfileResponse(savedProfile, avatarUrl);
        userCacheService.putMotherProfile(cognitoSub, response);
        return response;
    }

    private void rejectVerifiedAttributeUpdate(String phone, String email) {
        if (phone != null) {
            throw new AppException(UserErrorCode.PHONE_ALREADY_SET,
                    "Use /api/v1/users/me/phone/change and /confirm to update a verified phone.");
        }
        if (email != null) {
            throw new AppException(UserErrorCode.EMAIL_ALREADY_SET,
                    "Use /api/v1/users/me/email/change and /confirm to update a verified email.");
        }
    }
}
