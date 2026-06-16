package com.minduc.happabi.common.base;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BaseResponseTest {

    @Test
    void okBuildsSuccessfulResponseWithDefaultMessageAndTimestamp() {
        BaseResponse<Integer> response = BaseResponse.ok(42);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMessage()).isEqualTo("Operation completed successfully");
        assertThat(response.getData()).isEqualTo(42);
        assertThat(response.getTimestamp()).isNotNull();
    }

    @Test
    void createdUsesCreatedMessage() {
        BaseResponse<Integer> response = BaseResponse.created(1);

        assertThat(response.getMessage()).isEqualTo("Resource created successfully");
        assertThat(response.getData()).isEqualTo(1);
    }
}
