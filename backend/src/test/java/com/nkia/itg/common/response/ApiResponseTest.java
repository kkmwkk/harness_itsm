package com.nkia.itg.common.response;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ApiResponseTest {

    @Test
    @DisplayName("ok(data) 는 success=true 와 null 에러코드를 가진다")
    void okWithDataOnly() {
        ApiResponse<String> response = ApiResponse.ok("payload");

        assertThat(response.success()).isTrue();
        assertThat(response.data()).isEqualTo("payload");
        assertThat(response.message()).isNull();
        assertThat(response.errorCode()).isNull();
    }

    @Test
    @DisplayName("ok(data, message) 는 메시지를 함께 담는다")
    void okWithDataAndMessage() {
        ApiResponse<Integer> response = ApiResponse.ok(42, "처리 완료");

        assertThat(response.success()).isTrue();
        assertThat(response.data()).isEqualTo(42);
        assertThat(response.message()).isEqualTo("처리 완료");
        assertThat(response.errorCode()).isNull();
    }

    @Test
    @DisplayName("fail(code, message) 는 success=false 와 data=null 을 가진다")
    void failProducesFailureResponse() {
        ApiResponse<Object> response = ApiResponse.fail("META_NOT_FOUND", "메타를 찾을 수 없습니다.");

        assertThat(response.success()).isFalse();
        assertThat(response.data()).isNull();
        assertThat(response.message()).isEqualTo("메타를 찾을 수 없습니다.");
        assertThat(response.errorCode()).isEqualTo("META_NOT_FOUND");
    }
}
