package com.aiblockclock.client.ai;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

// 엔진 조회 응답과 이벤트 snapshot 이 같은 모양이다(B49). mock 은 정산이, skipped 는 등급 재료가 읽는다.
class RunStatusTest {
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    // 런타임이 mock 칸을 빠뜨려도 가짜 실행으로 보지 않는다. 가짜인지는 접수할 때 우리가 적은 값으로 본다.
    @Test
    void mock_칸이_없으면_mock_실행이_아니다() {
        assertThat(read("{\"run_id\":\"r1\",\"status\":\"succeeded\"}").isMock()).isFalse();
    }

    @Test
    void mock_이_false_면_mock_실행이_아니다() {
        assertThat(read("{\"run_id\":\"r1\",\"status\":\"succeeded\",\"mock\":false}").isMock()).isFalse();
    }

    @Test
    void mock_이_true_면_mock_실행이다() {
        assertThat(read("{\"run_id\":\"r1\",\"status\":\"succeeded\",\"mock\":true}").isMock()).isTrue();
    }

    @Test
    void 분기에서_고르지_않아_건너뛴_단계는_건너뛴_단계다() {
        assertThat(node("skipped").isSkipped()).isTrue();
    }

    @Test
    void 성공한_단계는_건너뛴_단계가_아니다() {
        assertThat(node("succeeded").isSkipped()).isFalse();
    }

    private static RunStatus read(String json) {
        return MAPPER.readValue(json, RunStatus.class);
    }

    private static RunStatus.Node node(String status) {
        return new RunStatus.Node("n1", "성적 분석", "llm", status, null, "8001", null, null);
    }
}
