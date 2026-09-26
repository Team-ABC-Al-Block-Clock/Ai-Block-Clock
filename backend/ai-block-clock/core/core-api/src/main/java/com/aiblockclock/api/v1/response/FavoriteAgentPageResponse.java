package com.aiblockclock.api.v1.response;

import com.aiblockclock.domain.agent.FavoriteAgent;
import com.aiblockclock.support.paging.CursorPage;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(description = "내가 즐겨찾기한 에이전트 목록. 즐겨찾기한 시각의 역순이다")
public record FavoriteAgentPageResponse(
        @Schema(description = "이번 페이지의 에이전트")
        List<FavoriteAgentResponse> content,

        @Schema(description = "다음 페이지 커서. 마지막 페이지면 null", example = "OQ", nullable = true)
        String nextCursor,

        @Schema(description = "더 불러올 것이 있는지", example = "true")
        boolean hasNext
) {
    public static FavoriteAgentPageResponse from(CursorPage<FavoriteAgent> page) {
        List<FavoriteAgentResponse> content = page.content().stream()
                .map(FavoriteAgentResponse::from)
                .toList();

        return new FavoriteAgentPageResponse(content, page.nextCursor(), page.hasNext());
    }

    @Schema(description = "즐겨찾기한 에이전트 하나")
    public record FavoriteAgentResponse(
            @Schema(description = "에이전트 ID", example = "33567ab2-e08b-461b-a0f2-51a857df5ec7")
            String agentId,

            @Schema(description = "에이전트 이름", example = "성적 분석 에이전트")
            String name,

            @Schema(description = "한 줄 소개", example = "성적표를 받아 학생별 리포트를 쓴다")
            String summary,

            @Schema(description = "아이콘 주소. 올린 적이 없으면 null", nullable = true)
            String iconUrl,

            @Schema(description = "실행 1회당 판매 가격. 소수점 오차를 막기 위해 문자열이다", example = "6.000000")
            String price,

            @Schema(description = "지금까지 실행된 횟수", example = "125")
            long executionCount,

            @Schema(description = "소유자")
            OwnerResponse owner,

            @Schema(description = "즐겨찾기한 시각", example = "2026-09-27T01:02:03.456789Z")
            Instant favoritedAt
    ) {
        static FavoriteAgentResponse from(FavoriteAgent agent) {
            return new FavoriteAgentResponse(
                    agent.agentId().value().toString(),
                    agent.name(),
                    agent.summary(),
                    agent.iconUrl(),
                    agent.price().value().toPlainString(),
                    agent.executionCount(),
                    new OwnerResponse(agent.ownerNickname()),
                    agent.favoritedAt());
        }
    }

    @Schema(description = "소유자")
    public record OwnerResponse(
            @Schema(description = "닉네임", example = "학원장")
            String nickname
    ) {
    }
}
