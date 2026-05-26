package com.minduc.happabi.service.user;

import java.util.UUID;

public interface IUserIdentityService {
    UUID getUserIdByCognitoSub(String sub);
}
