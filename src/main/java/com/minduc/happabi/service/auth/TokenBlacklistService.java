package com.minduc.happabi.service.auth;

import com.nimbusds.jwt.SignedJWT;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.util.Date;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private static final String KEY_PREFIX = "blacklist:token:";
    private static final String REVOKED_VALUE = "revoked";

    private final StringRedisTemplate stringRedisTemplate;

    public void blacklist(String accessToken) {
        try {
            SignedJWT jwt = parse(accessToken);
            String jti = jwt.getJWTClaimsSet().getJWTID();
            long ttlSeconds = ttlSeconds(jwt);

            if (ttlSeconds <= 0) {
                log.debug("[Blacklist] Token already expired, skipping blacklist: jti={}", jti);
                return;
            }

            stringRedisTemplate.opsForValue()
                    .set(cacheKey(accessToken, jti), REVOKED_VALUE, ttlSeconds, TimeUnit.SECONDS);
            log.info("[Blacklist] Access token blacklisted: jti={} ttl={}s", jti, ttlSeconds);
        } catch (ParseException e) {
            log.warn("[Blacklist] Could not parse access token - skipping blacklist: {}", e.getMessage());
        } catch (RuntimeException e) {
            log.warn("[Blacklist] Redis unavailable while blacklisting token - fail-open: {}", e.getMessage());
        }
    }

    public boolean isBlacklisted(String accessToken) {
        try {
            SignedJWT jwt = parse(accessToken);
            String jti = jwt.getJWTClaimsSet().getJWTID();
            return Boolean.TRUE.equals(stringRedisTemplate.hasKey(cacheKey(accessToken, jti)));
        } catch (ParseException e) {
            log.warn("[Blacklist] Could not parse access token for blacklist check: {}", e.getMessage());
            return false;
        } catch (RuntimeException e) {
            log.warn("[Blacklist] Redis unavailable while checking token blacklist - fail-open: {}", e.getMessage());
            return false;
        }
    }

    private SignedJWT parse(String accessToken) throws ParseException {
        return SignedJWT.parse(accessToken);
    }

    private String cacheKey(String accessToken, String jti) {
        return (jti != null && !jti.isBlank())
                ? KEY_PREFIX + jti
                : KEY_PREFIX + Integer.toHexString(accessToken.hashCode());
    }

    private long ttlSeconds(SignedJWT jwt) throws ParseException {
        Date expiration = jwt.getJWTClaimsSet().getExpirationTime();
        if (expiration == null) {
            return 0;
        }
        return (expiration.getTime() - System.currentTimeMillis()) / 1000;
    }
}
