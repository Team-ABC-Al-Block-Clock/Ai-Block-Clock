package com.aiblockclock.client.ai;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

// 실행은 브로커로만 맡긴다(ADR-0001). 엔진 HTTP 로 맡기는 길이 남아 있으면 누군가 다시 쓴다.
class RemovedRuntimeHttpSubmissionTest {

    @Test
    void 엔진_HTTP_로_실행을_맡기는_start_가_없다() {
        assertThat(AiRuntimeClient.class.getDeclaredMethods()).extracting(Method::getName).doesNotContain("start");
    }

    @Test
    void 엔진이_꽉_찼다는_BUSY_사유가_없다() {
        assertThat(AiClientError.values()).extracting(Enum::name).doesNotContain("BUSY");
    }
}
