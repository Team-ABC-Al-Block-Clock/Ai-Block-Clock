package com.aiblockclock.api.v1.response;

import com.aiblockclock.domain.agent.AgentFavorite;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "즐겨찾기 상태. 등록과 해제가 같은 모양으로 돌려준다")
public record AgentFavoriteResponse(
        @Schema(description = "에이전트 ID", example = "33567ab2-e08b-461b-a0f2-51a857df5ec7")
        String agentId,

        @Schema(description = "내가 즐겨찾기했는지", example = "true")
        boolean favorited,

        @Schema(description = "이 에이전트를 즐겨찾기한 사람 수", example = "12")
        long favoriteCount
) {
    public static AgentFavoriteResponse from(AgentFavorite favorite) {
        return new AgentFavoriteResponse(
                favorite.agentId().value().toString(),
                favorite.favorited(),
                favorite.favoriteCount());
    }
}
