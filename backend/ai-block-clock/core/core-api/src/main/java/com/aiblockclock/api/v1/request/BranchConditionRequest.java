package com.aiblockclock.api.v1.request;

import com.aiblockclock.domain.workflow.BranchCondition;
import com.aiblockclock.domain.workflow.ConditionOperator;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "분기 조건. 앞 노드 결과의 값 하나를 리터럴과 비교한다")
public record BranchConditionRequest(
        @Schema(description = "`<nodeKey>.<결과 안의 경로>`. 이 분기보다 앞에서 실행된 노드를 가리킨다",
                example = "n1.competition_level")
        @NotBlank String left,

        @Schema(description = "비교 방법. `IN` 이면 `right` 가 배열이다")
        @NotNull ConditionOperator operator,

        @Schema(description = "비교할 값. 숫자 · 문자열 · 불리언, `IN` 이면 그 배열", example = "high")
        @NotNull Object right
) {
    public BranchCondition toCondition() {
        return new BranchCondition(left, operator, right);
    }
}
