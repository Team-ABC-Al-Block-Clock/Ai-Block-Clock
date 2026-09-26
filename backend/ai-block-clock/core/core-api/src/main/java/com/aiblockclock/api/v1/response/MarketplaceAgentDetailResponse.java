package com.aiblockclock.api.v1.response;

import com.aiblockclock.domain.agent.MarketplaceAgent;
import com.aiblockclock.domain.agent.MarketplaceAgentDetail;
import com.aiblockclock.domain.agent.MarketplaceListing;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Schema(description = "마켓 에이전트 상세")
public record MarketplaceAgentDetailResponse(
        @Schema(description = "에이전트 ID", example = "9f1c3b7e-2a44-4d1e-9b77-0b2a6f4e5c13")
        String agentId,

        @Schema(description = "이름", example = "성적 분석 에이전트")
        String name,

        @Schema(description = "한 줄 소개", example = "성적표를 받아 학생별 리포트를 쓴다")
        String summary,

        @Schema(description = "상세 설명", example = "학생별 점수 표를 받아 이번 기간과 지난 기간을 비교한다")
        String description,

        @Schema(description = "실행 예시. 적지 않았으면 null", nullable = true)
        String usageExample,

        @Schema(description = "아이콘 주소. 올린 적이 없으면 null", nullable = true)
        String iconUrl,

        @Schema(description = "스크린샷. 올리는 기능이 붙기 전까지 빈 목록")
        List<AgentImageResponse> images,

        @Schema(description = "실행 1회당 판매 가격. 소수점 오차를 막기 위해 문자열이다", example = "6.000000")
        String price,

        @Schema(description = "누적 실행 횟수. 성공과 공급자 탓 실패를 더한 값이다", example = "12")
        long executionCount,

        @Schema(description = "실행 성공률. 0 이상 1 이하이고, 실행이 없으면 0 이다", example = "0.9")
        double successRate,

        @Schema(description = "평점 평균. 평가가 없으면 null", example = "4.50", nullable = true)
        BigDecimal averageRating,

        @Schema(description = "평가 수", example = "3")
        int reviewCount,

        @Schema(description = "요청한 사람이 즐겨찾기했는지", example = "false")
        boolean favorited,

        @Schema(description = "즐겨찾기한 사람 수", example = "4")
        long favoriteCount,

        @Schema(description = "소유자")
        MarketplaceOwnerDetailResponse owner,

        @Schema(description = "등록 시각")
        Instant createdAt
) {
    public static MarketplaceAgentDetailResponse from(MarketplaceAgentDetail detail) {
        MarketplaceListing listing = detail.listing();
        MarketplaceAgent agent = listing.agent();

        return new MarketplaceAgentDetailResponse(
                agent.id().value().toString(),
                agent.profile().name(),
                agent.profile().summary(),
                agent.profile().description(),
                agent.profile().usageExample(),
                agent.profile().thumbnailUrl(),
                List.of(),
                agent.price().value().toPlainString(),
                listing.executionCount(),
                listing.successRate(),
                listing.metric().averageRating(),
                listing.metric().reviewCount(),
                listing.favorited(),
                detail.favoriteCount(),
                new MarketplaceOwnerDetailResponse(Long.toString(agent.owner().value()), listing.ownerNickname(), null),
                agent.createdAt());
    }

    @Schema(description = "스크린샷 한 장")
    public record AgentImageResponse(
            @Schema(description = "이미지 ID", example = "4c2e7a10-6b3f-4f0e-a1d2-8e9b0c7d6f5a")
            String imageId,

            @Schema(description = "입력 화면인지 출력 화면인지", allowableValues = {"INPUT", "OUTPUT"}, example = "INPUT")
            String type,

            @Schema(description = "이미지 주소")
            String url,

            @Schema(description = "같은 유형 안에서의 순서", example = "1")
            int order
    ) {
    }

    @Schema(description = "소유자")
    public record MarketplaceOwnerDetailResponse(
            @Schema(description = "사용자 ID", example = "12")
            String userId,

            @Schema(description = "닉네임", example = "학원장")
            String nickname,

            @Schema(description = "프로필 이미지 주소. 아직 저장하지 않아 늘 null", nullable = true)
            String profileImageUrl
    ) {
    }
}
