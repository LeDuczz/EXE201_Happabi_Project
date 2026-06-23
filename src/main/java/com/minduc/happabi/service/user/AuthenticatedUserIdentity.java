package com.minduc.happabi.service.user;

import com.minduc.happabi.enums.UserRole;

import java.util.Set;
import java.util.UUID;

/**
 * Authorization snapshot resolved from Happabi's database for one authenticated account.
 */
public record AuthenticatedUserIdentity(UUID userId, Set<UserRole> roles) {
}
