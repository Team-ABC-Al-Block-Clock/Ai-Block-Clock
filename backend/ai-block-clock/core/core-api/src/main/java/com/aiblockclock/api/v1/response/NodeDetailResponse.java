package com.aiblockclock.api.v1.response;

import com.aiblockclock.domain.node.NodeDefinition;
import com.aiblockclock.enums.node.AiModel;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "작업 노드 상세")
public record NodeDetailResponse(
        @Schema(description = "노드 ID", example = "9f1c3b7e-2a44-4d1e-9b77-0b2a6f4e5c13")
        String nodeId,

        @Schema(description = "노드 이름", example = "성적 분석")
        String name,

        @Schema(description = "프롬프트 원문")
        String prompt,

        @Schema(description = "쓰는 모델")
        AiModel model,

        @Schema(description = "1회 실행 원가", example = "3.000000")
        String baseCost,

        @Schema(description = "통화", example = "ABC")
        String currency
) {
    public static NodeDetailResponse from(NodeDefinition node) {
        return new NodeDetailResponse(
                node.getId().value().toString(),
                node.getName(),
                node.getPrompt().value(),
                node.getModel(),
                node.getBaseCost().value().toPlainString(),
                AbcPricing.CURRENCY);
    }
}
