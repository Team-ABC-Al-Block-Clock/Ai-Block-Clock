package com.aiblockclock.api.v1.response;

import com.aiblockclock.domain.payment.AbcTransaction;
import com.aiblockclock.support.paging.CursorPage;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "ABC 사용 내역 목록. 최신순")
public record AbcTransactionPageResponse(
        @Schema(description = "이번 페이지의 내역")
        List<AbcTransactionResponse> content,

        @Schema(description = "다음 페이지 커서. 마지막 페이지면 null", example = "MjA", nullable = true)
        String nextCursor,

        @Schema(description = "더 불러올 것이 있는지", example = "true")
        boolean hasNext
) {
    public static AbcTransactionPageResponse from(CursorPage<AbcTransaction> page) {
        List<AbcTransactionResponse> content = page.content().stream()
                .map(AbcTransactionResponse::from)
                .toList();

        return new AbcTransactionPageResponse(content, page.nextCursor(), page.hasNext());
    }
}
