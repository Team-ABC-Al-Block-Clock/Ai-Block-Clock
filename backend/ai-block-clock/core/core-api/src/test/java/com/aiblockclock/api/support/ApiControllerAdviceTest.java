package com.aiblockclock.api.support;

import com.aiblockclock.support.response.ApiResult;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.assertj.core.api.Assertions.assertThat;

class ApiControllerAdviceTest {
    @Test
    @DisplayName("업로드 상한을 넘으면 어느 API 든 같은 파일 오류를 낸다")
    void answersUploadTooLargeWithCommonCode() {
        ResponseEntity<ApiResult<Void>> response =
                new ApiControllerAdvice().handleUploadTooLarge(new MaxUploadSizeExceededException(10L * 1024 * 1024));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getBody().getError().getCode()).isEqualTo("FILE_002");
    }
}
