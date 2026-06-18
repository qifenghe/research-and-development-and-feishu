package com.lhr.rnd.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseTest {

    @Test
    void successWrapsPayloadWithStandardCodeAndMessage() {
        var response = ApiResponse.success("ok");

        assertThat(response.code()).isEqualTo("0");
        assertThat(response.message()).isEqualTo("success");
        assertThat(response.data()).isEqualTo("ok");
    }

    @Test
    void failureWrapsBusinessError() {
        var response = ApiResponse.failure("SAMPLE_STATUS_ILLEGAL", "非法样品状态流转");

        assertThat(response.code()).isEqualTo("SAMPLE_STATUS_ILLEGAL");
        assertThat(response.message()).isEqualTo("非法样品状态流转");
        assertThat(response.data()).isNull();
    }
}
