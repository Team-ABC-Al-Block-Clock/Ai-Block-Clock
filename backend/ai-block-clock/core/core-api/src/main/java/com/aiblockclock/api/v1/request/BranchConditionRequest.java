package com.aiblockclock.api.v1.request;

import com.aiblockclock.domain.workflow.BranchCondition;
import com.aiblockclock.domain.workflow.ConditionOperator;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

@Schema(description = "분기 조건. 앞 노드 결과의 값 하나를 리터럴과 비교한다")
public record BranchConditionRequest(
        @Schema(description = "`<nodeKey>.<결과 안의 경로>`. 이 분기까지 어느 갈래로 와도 거치는 작업 노드나 에이전트를 가리킨다",
                example = "n1.competition_level")
        @NotBlank String left,

        @Schema(description = "비교 방법. `IN` 이면 `right` 가 배열이다")
        @NotNull ConditionOperator operator,

        @Schema(description = "비교할 값. 숫자 · 문자열 · 불리언, `IN` 이면 그 배열", example = "high")
        @NotNull Object right
) {
    @Schema(hidden = true)
    @AssertTrue(message = "IN 은 right 에 값의 배열을, 나머지 비교 방법은 문자열 · 숫자 · 불리언 하나를 보내야 합니다.")
    public boolean isComparable() {
        if (operator == null || right == null) {
            return true;
        }

        if (operator == ConditionOperator.IN) {
            return right instanceof List<?> values && values.stream().allMatch(BranchConditionRequest::isLiteral);
        }

        return isLiteral(right);
    }

    private static boolean isLiteral(Object value) {
        return value instanceof String || value instanceof Number || value instanceof Boolean;
    }

    public BranchCondition toCondition() {
        return new BranchCondition(left, operator, right);
    }
}
