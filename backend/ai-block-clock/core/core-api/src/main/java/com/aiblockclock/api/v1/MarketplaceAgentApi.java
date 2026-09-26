package com.aiblockclock.api.v1;

import com.aiblockclock.api.v1.response.MarketplaceAgentDetailResponse;
import com.aiblockclock.api.v1.response.MarketplaceAgentPageResponse;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.support.response.ApiResult;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

@Tag(name = "마켓", description = "공개된 에이전트를 찾는다.")
public interface MarketplaceAgentApi {
    @Operation(summary = "마켓 에이전트 목록",
            description = """
                    공개된 에이전트만 담는다. 순서는 최근에 등록한 것부터이고 바꿀 수 없다.

                    검색어는 이름과 한 줄 소개에서 찾는다. 그 밖의 필터는 없다.
                    `favorited` 는 요청한 사람이 즐겨찾기했는지다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 완료"),
            @ApiResponse(responseCode = "400", description = "개수나 커서가 올바르지 않다 (`COMMON_001`)",
                    content = @Content)
    })
    ApiResult<MarketplaceAgentPageResponse> find(
            UserId requester,
            @Parameter(description = "이름이나 한 줄 소개에서 찾을 말. 비우면 전체", example = "성적") String keyword,
            @Parameter(description = "이전 응답의 `nextCursor`. 첫 페이지는 비운다", example = "OQ") String cursor,
            @Parameter(description = "한 번에 가져올 개수. 1 이상 30 이하", example = "9") @Positive @Max(30) int size);

    @Operation(summary = "마켓 에이전트 상세",
            description = """
                    공개된 에이전트 하나를 자세히 보여준다. 공개 버전이 없는 에이전트는 없는 것으로 본다.

                    `successRate` 는 성공한 실행을 센 실행으로 나눈 값이다. 실행이 한 번도 없으면 0 이므로
                    화면은 `executionCount` 를 보고 가른다. `favorited` 는 요청한 사람이 즐겨찾기했는지,
                    `favoriteCount` 는 즐겨찾기한 사람 수다. 스크린샷은 아직 붙지 않아 `images` 는 빈 목록이다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 완료"),
            @ApiResponse(responseCode = "404", description = "없거나 공개되지 않은 에이전트다 (`MARKET_001`)",
                    content = @Content)
    })
    ApiResult<MarketplaceAgentDetailResponse> get(
            UserId requester,
            @Parameter(description = "에이전트 ID", required = true,
                    example = "9f1c3b7e-2a44-4d1e-9b77-0b2a6f4e5c13") UUID agentId);
}
