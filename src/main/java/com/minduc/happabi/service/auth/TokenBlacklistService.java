package com.minduc.happabi.service.auth;

import com.nimbusds.jwt.SignedJWT;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private static final String KEY_PREFIX = "blacklist:token:";

    private final StringRedisTemplate stringRedisTemplate;

    public void blacklist(String accessToken) {
        try {
            SignedJWT jwt = SignedJWT.parse(accessToken);
            String jti = jwt.getJWTClaimsSet().getJWTID();

            String key = (jti != null && !jti.isBlank())
                    ? KEY_PREFIX + jti
                    : KEY_PREFIX + Integer.toHexString(accessToken.hashCode());

            long exp = jwt.getJWTClaimsSet().getExpirationTime().getTime();
            long ttlSeconds = (exp - System.currentTimeMillis()) / 1000;

            if (ttlSeconds > 0) {
                stringRedisTemplate.opsForValue().set(key, "revoked", ttlSeconds, TimeUnit.SECONDS);
                log.info("[Blacklist] Access token blacklisted: jti={} ttl={}s", jti, ttlSeconds);
            } else {
                log.debug("[Blacklist] Token already expired, skipping blacklist: jti={}", jti);
            }
        } catch (ParseException e) {
            log.warn("[Blacklist] Could not parse access token — skipping blacklist: {}", e.getMessage());
        }
    }

    public boolean isBlacklisted(String accessToken) {
        try {
            SignedJWT jwt = SignedJWT.parse(accessToken);
            String jti = jwt.getJWTClaimsSet().getJWTID();

            String key = (jti != null && !jti.isBlank())
                    ? KEY_PREFIX + jti
                    : KEY_PREFIX + Integer.toHexString(accessToken.hashCode());

            return Boolean.TRUE.equals(stringRedisTemplate.hasKey(key));
        } catch (ParseException e) {
            log.warn("[Blacklist] Could not parse access token for blacklist check: {}", e.getMessage());
            return false;
        }
    }
}
