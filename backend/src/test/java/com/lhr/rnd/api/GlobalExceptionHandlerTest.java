package com.lhr.rnd.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    @Test
    void handlesBusinessExceptionAsStandardFailureResponse() {
        var handler = new GlobalExceptionHandler();
        var response = handler.handleBusinessException(
                new BusinessException("SAMPLE_STATUS_ILLEGAL", "非法样品状态流转")
        );

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("SAMPLE_STATUS_ILLEGAL");
        assertThat(response.getBody().message()).isEqualTo("非法样品状态流转");
    }
}
