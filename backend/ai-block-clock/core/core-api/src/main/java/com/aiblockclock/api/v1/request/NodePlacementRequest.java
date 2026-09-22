package com.aiblockclock.api.v1.request;

import com.aiblockclock.domain.workflow.NodeKey;
import com.aiblockclock.domain.workflow.NodePlacement;
import com.aiblockclock.enums.workflow.InputSource;
import com.aiblockclock.enums.workflow.SourceType;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

@Schema(description = "노드 배치. 노드의 실체가 아니라 '무엇을 어디에 놓았는가' 만 보낸다")
public record NodePlacementRequest(
        @Schema(description = "이 워크플로우 안에서 노드를 가리키는 키. 연결선이 이 값을 참조한다",
                example = "n1", maxLength = 64)
        @NotBlank @Size(max = 64) String nodeKey,

        @Schema(description = "배치한 것의 종류")
        @NotNull SourceType sourceType,

        @Schema(description = "원본 노드 ID 또는 에이전트 ID", example = "9f1c3b7e-2a44-4d1e-9b77-0b2a6f4e5c13")
        @NotNull UUID sourceId,

        @Schema(description = """
                이 자리가 무엇을 입력으로 받나.
                USER 는 사용자가 올린 원본, PREV 는 앞 노드 결과, PREV_AND_USER 는 둘 다.
                비우면 PREV 다. 맨 앞 노드는 앞이 없으므로 값과 무관하게 원본을 받는다.
                """, nullable = true)
        InputSource inputSource
) {
    public static List<NodePlacement> toPlacements(List<NodePlacementRequest> nodes) {
        return nodes.stream()
                .map(node -> new NodePlacement(NodeKey.of(node.nodeKey()), node.sourceType(),
                        node.sourceId(), node.inputSourceOrDefault()))
                .toList();
    }

    public InputSource inputSourceOrDefault() {
        return inputSource == null ? InputSource.defaultSource() : inputSource;
    }
}
