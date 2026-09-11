package com.aiblockclock.api.v1.response;

import com.aiblockclock.domain.workflow.WorkflowSummary;
import com.aiblockclock.support.paging.CursorPage;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(description = "내 워크플로우 목록. 그래프는 담지 않는다")
public record WorkflowPageResponse(
        @Schema(description = "이번 페이지의 워크플로우")
        List<WorkflowItemResponse> content,

        @Schema(description = "다음 페이지 커서. 다음 요청의 cursor 에 그대로 넣는다. 마지막 페이지면 null",
                example = "b2Zmc2V0PTEw", nullable = true)
        String nextCursor,

        @Schema(description = "더 불러올 것이 있는지", example = "true")
        boolean hasNext
) {
    public static WorkflowPageResponse from(CursorPage<WorkflowSummary> page) {
        List<WorkflowItemResponse> content = page.content().stream()
                .map(summary -> new WorkflowItemResponse(
                        summary.id().value().toString(),
                        summary.name(),
                        summary.lastExecutedAt(),
                        summary.updatedAt()))
                .toList();
        return new WorkflowPageResponse(content, page.nextCursor(), page.hasNext());
    }

    @Schema(description = "목록 한 줄")
    public record WorkflowItemResponse(
            @Schema(description = "워크플로우 ID", example = "33567ab2-e08b-461b-a0f2-51a857df5ec7")
            String workflowId,

            @Schema(description = "워크플로우 이름", example = "성적 분석 리포트")
            String name,

            @Schema(description = "마지막 실행 시각. 아직 실행하지 않았으면 null", nullable = true)
            Instant lastExecutedAt,

            @Schema(description = "마지막 수정 시각")
            Instant updatedAt
    ) {
    }
}
