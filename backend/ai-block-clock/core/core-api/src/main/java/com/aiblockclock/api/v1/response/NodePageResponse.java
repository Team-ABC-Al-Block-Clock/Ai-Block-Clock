package com.aiblockclock.api.v1.response;

import com.aiblockclock.domain.node.NodeDefinitionSummary;
import com.aiblockclock.enums.node.AiModel;
import com.aiblockclock.support.paging.CursorPage;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(description = "내 작업 노드 목록. 프롬프트 원문은 담지 않는다")
public record NodePageResponse(
        @Schema(description = "이번 페이지의 노드")
        List<NodeItemResponse> content,

        @Schema(description = "다음 페이지 커서. 마지막 페이지면 null", example = "b2Zmc2V0PTEw", nullable = true)
        String nextCursor,

        @Schema(description = "더 불러올 것이 있는지", example = "true")
        boolean hasNext
) {
    public static NodePageResponse from(CursorPage<NodeDefinitionSummary> page) {
        List<NodeItemResponse> content = page.content().stream()
                .map(summary -> new NodeItemResponse(
                        summary.id().value().toString(),
                        summary.name(),
                        summary.model(),
                        summary.baseCost().value().toPlainString(),
                        AbcPricing.CURRENCY,
                        summary.createdAt(),
                        summary.updatedAt()))
                .toList();

        return new NodePageResponse(content, page.nextCursor(), page.hasNext());
    }

    @Schema(description = "목록 한 줄")
    public record NodeItemResponse(
            @Schema(description = "노드 ID", example = "9f1c3b7e-2a44-4d1e-9b77-0b2a6f4e5c13")
            String nodeId,

            @Schema(description = "노드 이름", example = "성적 분석")
            String name,

            @Schema(description = "쓰는 모델")
            AiModel model,

            @Schema(description = "1회 실행 원가", example = "3.000000")
            String baseCost,

            @Schema(description = "통화", example = "ABC")
            String currency,

            @Schema(description = "생성 시각")
            Instant createdAt,

            @Schema(description = "마지막 수정 시각")
            Instant updatedAt
    ) {
    }
}
