package com.minduc.happabi.common.utils;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class NetworkUtilsTest {

    @Test
    void resolveClientIpUsesFirstForwardedForValue() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", " 203.0.113.9, 10.0.0.2 ");
        request.setRemoteAddr("127.0.0.1");

        assertThat(NetworkUtils.resolveClientIp(request)).isEqualTo("203.0.113.9");
    }

    @Test
    void resolveClientIpFallsBackToRealIpThenRemoteAddr() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Real-IP", "198.51.100.7");

        assertThat(NetworkUtils.resolveClientIp(request)).isEqualTo("198.51.100.7");
    }

    @Test
    void resolveClientIpNormalizesIpv6Loopback() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("::1");

        assertThat(NetworkUtils.resolveClientIp(request)).isEqualTo("127.0.0.1");
    }

    @Test
    void redisKeyPartEncodesUnsafeCharactersAndHandlesBlankValues() {
        assertThat(NetworkUtils.redisKeyPart("a b+c")).isEqualTo("a+b%2Bc");
        assertThat(NetworkUtils.redisKeyPart(" ")).isEqualTo("unknown");
    }
}
