package com.minduc.happabi.service.user;

import com.minduc.happabi.dto.event.S3ObjectDeleteRequestedEvent;
import com.minduc.happabi.entity.User;
import com.minduc.happabi.observability.annotation.AuditAction;
import com.minduc.happabi.observability.annotation.LogExecution;
import com.minduc.happabi.observability.annotation.TimedAction;
import com.minduc.happabi.repository.UserRepository;
import com.minduc.happabi.integration.s3.IS3Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserAvatarService {

    private final UserRepository userRepository;
    private final UserAccountLookupService userAccountLookupService;
    private final UserCacheService userCacheService;
    private final IS3Service s3Service;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    @PreAuthorize("isAuthenticated()")
    @LogExecution
    @TimedAction("UPLOAD_AVATAR")
    @AuditAction(action = "UPLOAD_AVATAR", resourceType = "USER_PROFILE")
    public String uploadAvatar(MultipartFile file) {
        String cognitoSub = userAccountLookupService.getCurrentSubOrThrow();
        User user = userAccountLookupService.findBySub(cognitoSub);

        String oldKey = user.getAvatarS3Key();
        String newKey = s3Service.upload("avatars", user.getId().toString(), file);

        user.setAvatarS3Key(newKey);
        userRepository.save(user);
        userCacheService.evictProfiles(cognitoSub);
        log.info("[Avatar] Uploaded new avatar: userId={} key={}", user.getId(), newKey);

        if (oldKey != null && !oldKey.isBlank() && !oldKey.equals(newKey)) {
            eventPublisher.publishEvent(new S3ObjectDeleteRequestedEvent(oldKey, "AVATAR_REPLACED"));
        }

        return s3Service.presign(newKey);
    }
}
