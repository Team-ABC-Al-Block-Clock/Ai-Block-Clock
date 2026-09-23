package com.aiblockclock.api.v1.response;

import com.aiblockclock.domain.workflow.GraphNode;
import com.aiblockclock.domain.workflow.Workflow;
import com.aiblockclock.enums.workflow.InputSource;
import com.aiblockclock.enums.workflow.SourceType;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(description = "워크플로우 상세. 캔버스를 이 값으로 복원한다")
public record WorkflowDetailResponse(
        @Schema(description = "워크플로우 ID", example = "33567ab2-e08b-461b-a0f2-51a857df5ec7")
        String workflowId,

        @Schema(description = "워크플로우 이름", example = "성적 분석 리포트")
        String name,

        @Schema(description = "워크플로우 설명. 없으면 null",
                example = "학생별 성적을 모아 리포트로 만든다", nullable = true)
        String description,

        @Schema(description = "저장 회차. 수정 요청에 그대로 넣는다", example = "1")
        long revision,

        @Schema(description = "배치된 노드. `edges` 를 따라간 연결 순서대로다")
        List<NodeResponse> nodes,

        @Schema(description = "노드 연결선. 노드와 같은 연결 순서대로다")
        List<EdgeResponse> edges,

        @Schema(description = "마지막 실행 시각. 아직 실행하지 않았으면 null", nullable = true)
        Instant lastExecutedAt,

        @Schema(description = "마지막 수정 시각")
        Instant updatedAt
) {
    public static WorkflowDetailResponse from(Workflow workflow) {
        List<NodeResponse> nodes = workflow.getGraph().nodesInOrder().stream()
                .map(NodeResponse::from)
                .toList();
        List<EdgeResponse> edges = workflow.getGraph().edgesInOrder().stream()
                .map(edge -> new EdgeResponse(
                        edge.key().value(),
                        edge.sourceNodeKey().value(),
                        edge.targetNodeKey().value()))
                .toList();

        return new WorkflowDetailResponse(
                workflow.getId().value().toString(),
                workflow.getName(),
                workflow.getDescription(),
                workflow.getRevision().value(),
                nodes,
                edges,
                workflow.getLastExecutedAt(),
                workflow.getTimestamps().updatedAt());
    }

    @Schema(description = "배치된 노드")
    public record NodeResponse(
            @Schema(description = "이 워크플로우 안에서의 노드 키", example = "n1")
            String nodeKey,

            @Schema(description = "배치한 것의 종류")
            SourceType sourceType,

            @Schema(description = "원본 노드 ID 또는 에이전트 ID", example = "9f1c3b7e-2a44-4d1e-9b77-0b2a6f4e5c13")
            String sourceId,

            @Schema(description = "에이전트 노드가 고정한 버전 ID. 기본 노드면 null", nullable = true)
            String versionId,

            @Schema(description = "이 자리가 무엇을 입력으로 받나")
            InputSource inputSource
    ) {
        static NodeResponse from(GraphNode node) {
            return new NodeResponse(
                    node.key().value(),
                    node.sourceType(),
                    node.reference().referenceId().toString(),
                    node.reference().hasVersion() ? node.reference().versionId().toString() : null,
                    node.inputSource());
        }
    }

    @Schema(description = "노드 연결선")
    public record EdgeResponse(
            @Schema(description = "연결선 키", example = "e1")
            String edgeKey,

            @Schema(description = "시작 노드의 nodeKey", example = "n1")
            String sourceNodeKey,

            @Schema(description = "도착 노드의 nodeKey", example = "n2")
            String targetNodeKey
    ) {
    }
}
