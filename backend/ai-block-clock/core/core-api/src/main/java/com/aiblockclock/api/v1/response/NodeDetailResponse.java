package com.aiblockclock.api.v1.response;

import com.aiblockclock.domain.node.NodeDefinition;
import com.aiblockclock.domain.node.NodeOutput;
import com.aiblockclock.domain.node.RefFile;
import com.aiblockclock.enums.node.AiModel;
import com.aiblockclock.enums.node.OutputKind;

import io.swagger.v3.oas.annotations.media.Schema;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

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

        @Schema(description = "결과를 내는 형식")
        OutputResponse output,

        @Schema(description = "1회 실행 원가", example = "3.000000")
        String baseCost,

        @Schema(description = "통화", example = "ABC")
        String currency,

        @Schema(description = "붙여 둔 참고 자료. 실행할 때 이 노드와 함께 나간다")
        List<RefFileResponse> refFiles
) {
    public static NodeDetailResponse from(NodeDefinition node, List<RefFile> refFiles) {
        return new NodeDetailResponse(
                node.getId().value().toString(),
                node.getSpec().name(),
                node.getPrompt().value(),
                node.getSpec().model(),
                OutputResponse.from(node.getSpec().output()),
                node.getSpec().baseCost().value().toPlainString(),
                AbcPricing.CURRENCY,
                RefFileResponse.from(refFiles));
    }

    @Schema(description = "결과를 내는 형식")
    public record OutputResponse(
            @Schema(description = "형식 종류", example = "JSON")
            OutputKind kind,

            @Schema(description = "결과 JSON 의 예시. `TEXT` 면 null",
                    example = "{ \"competition_level\": \"high\", \"competitors\": [ { \"name\": \"…\" } ] }",
                    nullable = true)
            JsonNode example
    ) {
        private static final JsonMapper MAPPER = JsonMapper.builder().build();

        public static OutputResponse from(NodeOutput output) {
            return new OutputResponse(
                    output.kind(), output.example() == null ? null : MAPPER.readTree(output.example()));
        }
    }
}
