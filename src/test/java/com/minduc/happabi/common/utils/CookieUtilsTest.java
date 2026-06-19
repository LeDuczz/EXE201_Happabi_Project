package com.minduc.happabi.common.utils;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class CookieUtilsTest {

    @Test
    void addRefreshTokenCookieWritesHttpOnlyCookieScopedToAuthPath() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        CookieUtils.addRefreshTokenCookie(response, "refresh-value");

        String header = response.getHeader("Set-Cookie");
        assertThat(header).contains("refreshToken=refresh-value");
        assertThat(header).contains("HttpOnly");
        assertThat(header).contains("Path=/api/v1/auth");
        assertThat(header).contains("SameSite=Lax");
        assertThat(header).contains("Max-Age=604800");
    }

    @Test
    void clearRefreshTokenCookieExpiresCookie() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        CookieUtils.clearRefreshTokenCookie(response);

        assertThat(response.getHeader("Set-Cookie")).contains("refreshToken=", "Max-Age=0");
    }

    @Test
    void readRefreshTokenFromCookieReturnsValueWhenPresent() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("other", "x"), new Cookie("refreshToken", "refresh-value"));

        assertThat(CookieUtils.readRefreshTokenFromCookie(request)).isEqualTo("refresh-value");
    }

    @Test
    void readRefreshTokenFromCookieReturnsNullWhenMissing() {
        assertThat(CookieUtils.readRefreshTokenFromCookie(new MockHttpServletRequest())).isNull();
    }
}
