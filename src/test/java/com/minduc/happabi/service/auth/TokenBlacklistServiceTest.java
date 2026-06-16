package com.minduc.happabi.service.auth;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenBlacklistServiceTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Test
    void blacklistStoresTokenByJtiUntilExpiration() throws Exception {
        TokenBlacklistService service = new TokenBlacklistService(stringRedisTemplate);
        String token = signedToken("jti-123", Instant.now().plusSeconds(300));
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        service.blacklist(token);

        verify(valueOperations).set(eq("blacklist:token:jti-123"), eq("revoked"), anyLong(), eq(TimeUnit.SECONDS));
    }

    @Test
    void blacklistSkipsExpiredTokens() throws Exception {
        TokenBlacklistService service = new TokenBlacklistService(stringRedisTemplate);

        service.blacklist(signedToken("expired", Instant.now().minusSeconds(30)));

        verify(stringRedisTemplate, never()).opsForValue();
    }

    @Test
    void isBlacklistedReturnsTrueWhenRedisKeyExists() throws Exception {
        TokenBlacklistService service = new TokenBlacklistService(stringRedisTemplate);
        String token = signedToken("jti-456", Instant.now().plusSeconds(300));
        when(stringRedisTemplate.hasKey("blacklist:token:jti-456")).thenReturn(true);

        assertThat(service.isBlacklisted(token)).isTrue();
    }

    @Test
    void invalidTokenIsNeverConsideredBlacklisted() {
        TokenBlacklistService service = new TokenBlacklistService(stringRedisTemplate);

        assertThat(service.isBlacklisted("not-a-jwt")).isFalse();
    }

    private String signedToken(String jti, Instant expiresAt) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .jwtID(jti)
                .expirationTime(Date.from(expiresAt))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner("01234567890123456789012345678901"));
        return jwt.serialize();
    }
}
