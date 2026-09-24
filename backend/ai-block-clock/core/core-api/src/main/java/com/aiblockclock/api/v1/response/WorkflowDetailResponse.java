package com.aiblockclock.api.v1.response;

import com.aiblockclock.domain.workflow.ConditionOperator;
import com.aiblockclock.domain.workflow.ControlNode;
import com.aiblockclock.domain.workflow.GraphNode;
import com.aiblockclock.domain.workflow.InputField;
import com.aiblockclock.domain.workflow.IoNode;
import com.aiblockclock.domain.workflow.Workflow;
import com.aiblockclock.enums.workflow.ControlType;
import com.aiblockclock.enums.workflow.InputFieldType;
import com.aiblockclock.enums.workflow.InputSource;
import com.aiblockclock.enums.workflow.IoType;
import com.aiblockclock.enums.workflow.ResultFormat;
import com.aiblockclock.enums.workflow.ResultShape;
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

        @Schema(description = "배치된 노드. 시작 노드부터 `edges` 를 따라간 순서대로다. 분기에서는 참 갈래를 먼저 끝까지 간다")
        List<NodeResponse> nodes,

        @Schema(description = "노드 연결선. 노드와 같은 순서로, 각 노드에서 나가는 선을 참 갈래부터 둔다")
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
                        edge.targetNodeKey().value(),
                        edge.when()))
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

            @Schema(description = "원본 노드 ID 또는 에이전트 ID. 제어 노드면 null",
                    example = "9f1c3b7e-2a44-4d1e-9b77-0b2a6f4e5c13", nullable = true)
            String sourceId,

            @Schema(description = "에이전트 노드가 고정한 버전 ID. 기본 노드면 null", nullable = true)
            String versionId,

            @Schema(description = "이 자리가 무엇을 입력으로 받나")
            InputSource inputSource,

            @Schema(description = "제어 노드 종류. 제어 노드가 아니면 null", nullable = true)
            ControlType controlType,

            @Schema(description = "분기 조건. 분기 노드가 아니면 null", nullable = true)
            ConditionResponse condition,

            @Schema(description = "입출력 노드 종류. 입출력 노드가 아니면 null", nullable = true)
            IoType ioType,

            @Schema(description = "실행할 때 사용자가 채울 입력 항목. 시작 노드가 아니면 빈 배열")
            List<InputFieldResponse> fields,

            @Schema(description = "결과를 어떤 구조로 낼지. 끝 노드가 아니면 null", nullable = true)
            ResultShape resultShape,

            @Schema(description = "결과를 어떤 형식으로 받을지. 끝 노드가 아니면 null", nullable = true)
            ResultFormat resultFormat
    ) {
        static NodeResponse from(GraphNode node) {
            ControlNode control = node instanceof ControlNode placed ? placed : null;
            IoNode io = node instanceof IoNode placed ? placed : null;

            return new NodeResponse(
                    node.key().value(),
                    node.sourceType(),
                    node.reference().referenceId() == null ? null : node.reference().referenceId().toString(),
                    node.reference().hasVersion() ? node.reference().versionId().toString() : null,
                    node.inputSource(),
                    control == null ? null : control.controlType(),
                    control == null ? null : new ConditionResponse(
                            control.condition().left(), control.condition().operator(), control.condition().right()),
                    io == null ? null : io.ioType(),
                    io == null ? List.of() : io.fields().stream().map(InputFieldResponse::from).toList(),
                    io == null ? null : io.resultShape(),
                    io == null ? null : io.resultFormat());
        }
    }

    @Schema(description = "실행할 때 사용자가 채울 입력 항목")
    public record InputFieldResponse(
            @Schema(description = "채운 값을 담을 키", example = "brand_name")
            String key,

            @Schema(description = "입력란에 보일 이름", example = "브랜드명")
            String label,

            @Schema(description = "입력란의 종류")
            InputFieldType type,

            @Schema(description = "반드시 채워야 하나")
            boolean required,

            @Schema(description = "고를 수 있는 값. `CHOICE` 가 아니면 빈 배열")
            List<String> choices
    ) {
        static InputFieldResponse from(InputField field) {
            return new InputFieldResponse(
                    field.key(), field.label(), field.type(), field.required(), field.choices());
        }
    }

    @Schema(description = "분기 조건")
    public record ConditionResponse(
            @Schema(description = "`<nodeKey>.<결과 안의 경로>`", example = "n1.competition_level")
            String left,

            @Schema(description = "비교 방법")
            ConditionOperator operator,

            @Schema(description = "비교할 값", example = "high")
            Object right
    ) {
    }

    @Schema(description = "노드 연결선")
    public record EdgeResponse(
            @Schema(description = "연결선 키", example = "e1")
            String edgeKey,

            @Schema(description = "시작 노드의 nodeKey", example = "n1")
            String sourceNodeKey,

            @Schema(description = "도착 노드의 nodeKey", example = "n2")
            String targetNodeKey,

            @Schema(description = "분기 노드에서 나가는 선의 갈래. 나머지 선은 null", nullable = true)
            Boolean when
    ) {
    }
}
