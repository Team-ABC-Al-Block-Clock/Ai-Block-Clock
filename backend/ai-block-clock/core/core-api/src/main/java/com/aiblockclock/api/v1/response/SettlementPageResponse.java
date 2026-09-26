package com.aiblockclock.api.v1.response;

import com.aiblockclock.domain.execution.SettlementRecord;
import com.aiblockclock.support.paging.CursorPage;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "정산 내역 목록. 최신순")
public record SettlementPageResponse(
        @Schema(description = "이번 페이지의 내역")
        List<SettlementResponse> content,

        @Schema(description = "다음 페이지 커서. 마지막 페이지면 null", example = "MjA", nullable = true)
        String nextCursor,

        @Schema(description = "더 불러올 것이 있는지", example = "true")
        boolean hasNext
) {
    public static SettlementPageResponse from(CursorPage<SettlementRecord> page) {
        List<SettlementResponse> content = page.content().stream()
                .map(SettlementResponse::from)
                .toList();

        return new SettlementPageResponse(content, page.nextCursor(), page.hasNext());
    }
}
