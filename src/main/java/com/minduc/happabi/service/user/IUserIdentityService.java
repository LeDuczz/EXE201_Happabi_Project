package com.minduc.happabi.service.user;

public interface IUserIdentityService {
    AuthenticatedUserIdentity getActiveUserIdentity(String sub);
}
