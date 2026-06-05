package com.minduc.happabi.config.security;

import com.minduc.happabi.enums.NurseStatus;
import com.minduc.happabi.service.nurse.NurseAccessCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component("nurseAccessGuard")
@RequiredArgsConstructor
public class NurseAccessGuard {

    private final NurseAccessCacheService nurseAccessCacheService;

    public boolean isActive(Authentication authentication) {
        Optional<UUID> userId = extractUserId(authentication);
        if (userId.isEmpty()) {
            return false;
        }

        NurseStatus status = nurseAccessCacheService.getStatus(userId.get()).orElse(null);
        boolean active = status == NurseStatus.ACTIVE;
        if (!active) {
            log.debug("[NurseAccessGuard] Blocked nurse API access userId={} status={}",
                    userId.get(), status);
        }
        return active;
    }

    private Optional<UUID> extractUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof UserContext userContext && userContext.userId() != null) {
            return Optional.of(userContext.userId());
        }

        try {
            return Optional.of(UUID.fromString(authentication.getName()));
        } catch (RuntimeException e) {
            log.debug("[NurseAccessGuard] Cannot resolve userId from authentication name={}",
                    authentication.getName());
            return Optional.empty();
        }
    }
}
