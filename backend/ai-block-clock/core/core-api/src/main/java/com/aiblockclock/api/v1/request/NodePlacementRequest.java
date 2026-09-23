package com.aiblockclock.api.v1.request;

import com.aiblockclock.domain.workflow.NodeDraft;
import com.aiblockclock.domain.workflow.NodeKey;
import com.aiblockclock.domain.workflow.NodePlacement;
import com.aiblockclock.enums.node.AiModel;
import com.aiblockclock.enums.node.NodeType;
import com.aiblockclock.enums.workflow.InputSource;
import com.aiblockclock.enums.workflow.SourceType;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

@Schema(description = """
        노드 배치. 종류에 따라 보내는 것이 다르다.
        `LLM` 은 그 자리에서 쓴 프롬프트라 `name` · `prompt` 를 실어 보내고 저장할 때 노드가 만들어진다.
        `TRIGGER` · `TOOL` 과 에이전트는 미리 정의된 것을 `sourceId` 로 가리킨다.
        """)
public record NodePlacementRequest(
        @Schema(description = "이 워크플로우 안에서 노드를 가리키는 키. 연결선이 이 값을 참조한다",
                example = "n1", maxLength = 64)
        @NotBlank @Size(max = 64) String nodeKey,

        @Schema(description = "배치한 것의 종류")
        @NotNull SourceType sourceType,

        @Schema(description = """
                노드 종류. `sourceType` 이 `NODE` 일 때만 보낸다.
                `sourceId` 와 함께 보내면 그 노드가 이 종류인지 대조하고, 생략하면 대조하지 않는다.
                """, nullable = true)
        NodeType nodeType,

        @Schema(description = "놓을 원본 노드 ID 또는 에이전트 ID. `LLM` 은 만들어 놓으므로 비운다",
                example = "9f1c3b7e-2a44-4d1e-9b77-0b2a6f4e5c13", nullable = true)
        UUID sourceId,

        @Schema(description = "새로 만들 노드의 이름. `LLM` 일 때만 쓴다",
                example = "가정통신문 작성", maxLength = 40, nullable = true)
        @Size(max = 40) String name,

        @Schema(description = "새로 만들 노드의 프롬프트 원문. `LLM` 일 때만 쓴다",
                example = "분석 결과로 가정통신문을 쓴다", nullable = true)
        String prompt,

        @Schema(description = "새로 만들 노드가 쓸 모델. 비우면 서버 기본 모델", nullable = true)
        AiModel model,

        @Schema(description = """
                이 자리가 무엇을 입력으로 받나.
                USER 는 사용자가 올린 원본, PREV 는 앞 노드 결과, PREV_AND_USER 는 둘 다.
                비우면 PREV 다. 맨 앞 노드는 앞이 없으므로 값과 무관하게 원본을 받는다.
                """, nullable = true)
        InputSource inputSource
) {
    public static List<NodePlacement> toPlacements(List<NodePlacementRequest> nodes) {
        return nodes.stream()
                .map(node -> new NodePlacement(NodeKey.of(node.nodeKey()), node.sourceType(), node.nodeType(),
                        node.sourceId(), node.inputSourceOrDefault(), node.toDraft()))
                .toList();
    }

    @Schema(hidden = true)
    @AssertTrue(message = "LLM 은 name 과 prompt 를 실어 보내고, 나머지 종류는 sourceId 로 가리켜야 합니다.")
    public boolean isPlaceable() {
        if (carriesBody()) {
            return sourceType == SourceType.NODE && nodeType == NodeType.LLM
                    && sourceId == null && hasText(name) && hasText(prompt);
        }

        return sourceId != null && (sourceType == SourceType.NODE || nodeType == null);
    }

    public InputSource inputSourceOrDefault() {
        return inputSource == null ? InputSource.defaultSource() : inputSource;
    }

    private NodeDraft toDraft() {
        return carriesBody() ? new NodeDraft(name, prompt, modelOrDefault()) : null;
    }

    private boolean carriesBody() {
        return name != null || prompt != null || model != null;
    }

    private AiModel modelOrDefault() {
        return model == null ? AiModel.defaultModel() : model;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
