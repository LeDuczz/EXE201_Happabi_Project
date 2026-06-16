package com.minduc.happabi.exception;

import com.minduc.happabi.exception.code.AuthErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AppExceptionTest {

    @Test
    void constructorUsesErrorCodeMessageAndStoresCode() {
        AppException exception = new AppException(AuthErrorCode.AUTH_FAILED);

        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.AUTH_FAILED);
        assertThat(exception.getMessage()).isEqualTo(AuthErrorCode.AUTH_FAILED.getMessage());
    }

    @Test
    void detailConstructorAppendsContextToMessage() {
        AppException exception = new AppException(AuthErrorCode.AUTH_FAILED, "extra detail");

        assertThat(exception.getMessage()).contains(AuthErrorCode.AUTH_FAILED.getMessage(), "extra detail");
    }
}
