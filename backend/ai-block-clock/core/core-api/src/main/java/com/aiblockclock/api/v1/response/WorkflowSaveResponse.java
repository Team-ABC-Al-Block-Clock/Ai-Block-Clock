package com.aiblockclock.api.v1.response;

import com.aiblockclock.domain.workflow.GraphNode;
import com.aiblockclock.domain.workflow.Workflow;
import com.aiblockclock.enums.workflow.SourceType;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "저장·수정 결과")
public record WorkflowSaveResponse(
        @Schema(description = "워크플로우 ID", example = "33567ab2-e08b-461b-a0f2-51a857df5ec7")
        String workflowId,

        @Schema(description = "저장된 이름", example = "성적 분석 리포트")
        String name,

        @Schema(description = "저장 회차. 다음 수정 요청에 이 값을 넣는다", example = "1")
        long revision,

        @Schema(description = """
                놓인 노드. 본문으로 실어 보낸 새 단계는 여기서 `sourceId` 를 받는다.
                다음 수정 요청에는 그 ID 를 그대로 보내야 노드가 또 만들어지지 않는다.
                """)
        List<PlacedNodeResponse> nodes,

        @Schema(description = "실행 예상 차감액. 단가 산정 전이라 지금은 항상 0 이다", example = "0")
        String estimatedCost,

        @Schema(description = "통화", example = "ABC")
        String currency
) {
    public static WorkflowSaveResponse from(Workflow workflow) {
        List<PlacedNodeResponse> nodes = workflow.getGraph().nodesInOrder().stream()
                .map(PlacedNodeResponse::from)
                .toList();

        return new WorkflowSaveResponse(
                workflow.getId().value().toString(),
                workflow.getName(),
                workflow.getRevision().value(),
                nodes,
                AbcPricing.UNPRICED,
                AbcPricing.CURRENCY);
    }

    @Schema(description = "놓인 노드")
    public record PlacedNodeResponse(
            @Schema(description = "요청에 보낸 노드 키", example = "n1")
            String nodeKey,

            @Schema(description = "배치한 것의 종류")
            SourceType sourceType,

            @Schema(description = "놓인 원본 노드 ID 또는 에이전트 ID. 제어 노드면 null",
                    example = "9f1c3b7e-2a44-4d1e-9b77-0b2a6f4e5c13", nullable = true)
            String sourceId
    ) {
        static PlacedNodeResponse from(GraphNode node) {
            return new PlacedNodeResponse(
                    node.key().value(), node.sourceType(),
                    node.reference().referenceId() == null ? null : node.reference().referenceId().toString());
        }
    }
}
