package com.aiblockclock.api.v1.response;

import com.aiblockclock.domain.agent.MarketplaceAgent;
import com.aiblockclock.domain.agent.MarketplaceListing;
import com.aiblockclock.support.paging.CursorPage;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Schema(description = "마켓 에이전트 목록")
public record MarketplaceAgentPageResponse(
        @Schema(description = "이번 페이지의 에이전트")
        List<MarketplaceAgentSummaryResponse> content,

        @Schema(description = "다음 페이지 커서. 다음 요청의 cursor 에 그대로 넣는다. 마지막 페이지면 null",
                example = "OQ", nullable = true)
        String nextCursor,

        @Schema(description = "더 불러올 것이 있는지", example = "true")
        boolean hasNext
) {
    public static MarketplaceAgentPageResponse from(CursorPage<MarketplaceListing> page) {
        List<MarketplaceAgentSummaryResponse> content = page.content().stream()
                .map(MarketplaceAgentSummaryResponse::from)
                .toList();

        return new MarketplaceAgentPageResponse(content, page.nextCursor(), page.hasNext());
    }

    @Schema(description = "목록에 놓이는 에이전트 하나")
    public record MarketplaceAgentSummaryResponse(
            @Schema(description = "에이전트 ID", example = "9f1c3b7e-2a44-4d1e-9b77-0b2a6f4e5c13")
            String agentId,

            @Schema(description = "이름", example = "성적 분석 에이전트")
            String name,

            @Schema(description = "한 줄 소개", example = "성적표를 받아 학생별 리포트를 쓴다")
            String summary,

            @Schema(description = "아이콘 주소. 올린 적이 없으면 null", nullable = true)
            String iconUrl,

            @Schema(description = "실행 1회당 판매 가격. 소수점 오차를 막기 위해 문자열이다", example = "6.000000")
            String price,

            @Schema(description = "누적 실행 횟수. 성공과 공급자 탓 실패를 더한 값이다", example = "12")
            long executionCount,

            @Schema(description = "평점 평균. 평가가 없으면 null", example = "4.50", nullable = true)
            BigDecimal averageRating,

            @Schema(description = "평가 수", example = "3")
            int reviewCount,

            @Schema(description = "즐겨찾기했는지. 즐겨찾기가 붙기 전까지 늘 false", example = "false")
            boolean favorited,

            @Schema(description = "소유자")
            MarketplaceOwnerResponse owner,

            @Schema(description = "등록 시각")
            Instant createdAt
    ) {
        static MarketplaceAgentSummaryResponse from(MarketplaceListing listing) {
            MarketplaceAgent agent = listing.agent();

            return new MarketplaceAgentSummaryResponse(
                    agent.id().value().toString(),
                    agent.profile().name(),
                    agent.profile().summary(),
                    agent.profile().thumbnailUrl(),
                    agent.price().value().toPlainString(),
                    listing.executionCount(),
                    listing.metric().averageRating(),
                    listing.metric().reviewCount(),
                    false,
                    new MarketplaceOwnerResponse(listing.ownerNickname()),
                    agent.createdAt());
        }
    }

    @Schema(description = "소유자")
    public record MarketplaceOwnerResponse(
            @Schema(description = "닉네임", example = "학원장")
            String nickname
    ) {
    }
}
