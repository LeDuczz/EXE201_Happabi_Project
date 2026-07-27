package com.minduc.happabi.controller.auth;

import com.minduc.happabi.service.auth.IAuthService;
import org.junit.jupiter.api.Test;
import org.springframework.security.web.csrf.CsrfToken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AuthControllerTest {

    @Test
    void csrfReturnsCurrentToken() {
        AuthController controller = new AuthController(mock(IAuthService.class));

        var response = controller.csrf(new CsrfToken() {
            @Override
            public String getHeaderName() {
                return "X-XSRF-TOKEN";
            }

            @Override
            public String getParameterName() {
                return "_csrf";
            }

            @Override
            public String getToken() {
                return "csrf-token";
            }
        });

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).isEqualTo("csrf-token");
    }
}
