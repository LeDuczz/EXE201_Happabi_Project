package com.minduc.happabi.common.utils;

import lombok.experimental.UtilityClass;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import com.minduc.happabi.config.security.UserContext;

import java.util.Optional;

@UtilityClass
public class AuthUtils {

    /**
     * Returns the Cognito {@code sub} (unique user identifier) from the
     * current JWT principal, or empty if not authenticated.
     */
    public Optional<String> getCurrentSub() {
        return getJwt().map(jwt -> jwt.getClaimAsString("sub"));
    }

    /**
     * Returns the Cognito {@code sub}, throwing {@link IllegalStateException}
     * if no authenticated principal is present.
     */
    public String requireCurrentSub() {
        return getCurrentSub()
                .orElseThrow(() -> new IllegalStateException("No authenticated principal in context"));
    }

    public String getCurrentUserId() {
        return SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString();
    }

    /**
     * Returns the {@code email} claim from the current JWT, if present.
     */
    public Optional<String> getCurrentEmail() {
        return getJwt().map(jwt -> jwt.getClaimAsString("email"));
    }

    /**
     * Returns the raw {@link Jwt} from the security context, or empty.
     */
    public Optional<Jwt> getJwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated())
            return Optional.empty();

        Object principal = auth.getPrincipal();
        if (principal instanceof Jwt jwt)
            return Optional.of(jwt);
        if (principal instanceof UserContext userContext)
            return Optional.of(userContext.jwt());

        return Optional.empty();
    }

    /**
     * Checks whether the current user has the given Cognito group role.
     * Role name should be the raw group name, e.g. "nurse", "admin".
     */
    public boolean hasRole(String role) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null)
            return false;
        String authority = "ROLE_" + role.toUpperCase();
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equalsIgnoreCase(authority));
    }
}
