package com.aiblockclock.client.ai;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 런타임 주소와 기한은 기동할 때 검사한다. 잘못 둔 값으로 뜬 뒤 실행마다 실패하는 것보다 뜨지 않는 편이 낫다.
class RuntimePropertiesTest {
    private static final URI BASE_URL = URI.create("http://ai-block-clock-runtime:8000");
    private static final Duration TIMEOUT = Duration.ofSeconds(2);
    private static final Path RESULTS_ROOT = Path.of("results");

    @Test
    void HTTP_가_아닌_주소면_설정_오류다() {
        assertThatThrownBy(() -> new RuntimeProperties(URI.create("ftp://ai-block-clock-runtime:8000"), TIMEOUT,
                TIMEOUT, RESULTS_ROOT))
                .isInstanceOfSatisfying(AiClientException.class, exception ->
                        assertInvalid(exception, "ai.runtime.base-url"));
    }

    @Test
    void 접속_기한이_0_이면_설정_오류다() {
        assertThatThrownBy(() -> new RuntimeProperties(BASE_URL, Duration.ZERO, TIMEOUT, RESULTS_ROOT))
                .isInstanceOfSatisfying(AiClientException.class, exception ->
                        assertInvalid(exception, "ai.runtime.connect-timeout"));
    }

    @Test
    void 응답_기한이_0_이면_설정_오류다() {
        assertThatThrownBy(() -> new RuntimeProperties(BASE_URL, TIMEOUT, Duration.ZERO, RESULTS_ROOT))
                .isInstanceOfSatisfying(AiClientException.class, exception ->
                        assertInvalid(exception, "ai.runtime.read-timeout"));
    }

    // 어느 설정이 잘못됐는지 메시지에 남아야 기동 로그만 보고 고칠 수 있다.
    private static void assertInvalid(AiClientException exception, String propertyName) {
        assertThat(exception.getError()).isEqualTo(AiClientError.INVALID_CONFIGURATION);
        assertThat(exception.getMessage()).startsWith(propertyName + " ");
    }
}
