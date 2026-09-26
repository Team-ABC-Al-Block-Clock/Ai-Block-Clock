package com.aiblockclock.api.v1.response;

import com.aiblockclock.domain.agent.MyAgent;
import com.aiblockclock.domain.agent.PublicAgent;
import com.aiblockclock.support.paging.CursorPage;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(description = "판매 중인 내 에이전트 목록")
public record MyAgentPageResponse(
        @Schema(description = "이번 페이지의 에이전트")
        List<MyAgentResponse> content,

        @Schema(description = "다음 페이지 커서. 마지막 페이지면 null", example = "OQ", nullable = true)
        String nextCursor,

        @Schema(description = "더 불러올 것이 있는지", example = "true")
        boolean hasNext
) {
    public static MyAgentPageResponse from(CursorPage<MyAgent> page) {
        List<MyAgentResponse> content = page.content().stream()
                .map(MyAgentResponse::from)
                .toList();

        return new MyAgentPageResponse(content, page.nextCursor(), page.hasNext());
    }

    @Schema(description = "판매 중인 내 에이전트 하나")
    public record MyAgentResponse(
            @Schema(description = "에이전트 ID", example = "9f1c3b7e-2a44-4d1e-9b77-0b2a6f4e5c13")
            String agentId,

            @Schema(description = "이름", example = "성적 분석 에이전트")
            String name,

            @Schema(description = "한 줄 소개", example = "성적을 분석한다")
            String summary,

            @Schema(description = "아이콘 주소. 올린 적이 없으면 null", nullable = true)
            String iconUrl,

            @Schema(description = "실행 1회당 판매가. 소수점 오차를 막기 위해 문자열이다", example = "6.000000")
            String price,

            @Schema(description = "이 에이전트로 지금까지 받은 정산금. 단가와 같은 이유로 문자열이다", example = "18.000000")
            String revenue,

            @Schema(description = "등급 집계에 들어간 누적 실행 수. 성공과 판매자 탓 실패를 더하고, 내가 돌린 실행은 세지 않는다",
                    example = "3")
            long executionCount,

            @Schema(description = "내가 즐겨찾기했는지", example = "false")
            boolean favorited,

            @Schema(description = "최근 7일 동안 하루마다 정산을 남긴 실행 수. 엿새 전부터 오늘까지 차례로 담는다 (UTC)",
                    example = "[0, 1, 0, 2, 0, 0, 3]")
            List<Integer> trend,

            @Schema(description = "등록한 시각")
            Instant createdAt
    ) {
        static MyAgentResponse from(MyAgent agent) {
            PublicAgent listing = agent.selling().agent();

            return new MyAgentResponse(
                    listing.id().value().toString(),
                    listing.name(),
                    listing.summary(),
                    agent.selling().thumbnailUrl(),
                    listing.price().value().toPlainString(),
                    agent.revenue().toPlainString(),
                    agent.executionCount(),
                    agent.favorited(),
                    agent.trend(),
                    agent.selling().createdAt());
        }
    }
}
