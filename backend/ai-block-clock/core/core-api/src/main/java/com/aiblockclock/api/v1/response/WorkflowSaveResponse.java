package com.aiblockclock.api.v1.response;

import com.aiblockclock.domain.workflow.Workflow;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "저장·수정 결과")
public record WorkflowSaveResponse(
        @Schema(description = "워크플로우 ID", example = "33567ab2-e08b-461b-a0f2-51a857df5ec7")
        String workflowId,

        @Schema(description = "저장된 이름", example = "성적 분석 리포트")
        String name,

        @Schema(description = "저장 회차. 다음 수정 요청에 이 값을 넣는다", example = "1")
        long revision,

        @Schema(description = "실행 예상 차감액. 단가 산정 전이라 지금은 항상 0 이다", example = "0")
        String estimatedCost,

        @Schema(description = "통화", example = "ABC")
        String currency
) {
    public static WorkflowSaveResponse from(Workflow workflow) {
        return new WorkflowSaveResponse(
                workflow.getId().value().toString(),
                workflow.getName(),
                workflow.getRevision().value(),
                AbcPricing.UNPRICED,
                AbcPricing.CURRENCY);
    }
}
