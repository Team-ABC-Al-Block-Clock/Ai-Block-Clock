package com.aiblockclock.api.v1.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Size;

@Schema(description = "노드 테스트 실행 요청. 본문을 비워 보내도 된다")
public record NodeTestRunRequest(
        @Schema(description = "노드에 넘길 입력. 실제 실행에서 사용자가 쓸 법한 한 줄을 넣는다",
                example = "2학기 중간고사 점수를 지난 학기와 비교해 주세요", maxLength = 500, nullable = true)
        @Size(max = 500) String comment,

        @Schema(description = """
                true 면 모델을 부르지 않고 모양만 본다. 결과물이 가짜라 원가를 재지 않는다. 비우면 false""",
                example = "false", nullable = true)
        Boolean mock
) {
    public boolean mockOrDefault() {
        return Boolean.TRUE.equals(mock);
    }
}
