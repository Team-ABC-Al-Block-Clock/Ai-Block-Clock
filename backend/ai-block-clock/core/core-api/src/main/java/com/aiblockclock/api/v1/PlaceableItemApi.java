package com.aiblockclock.api.v1;

import com.aiblockclock.api.v1.response.PlaceableItemPageResponse;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.enums.workflow.PlaceableScope;
import com.aiblockclock.support.response.ApiResult;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;

@Tag(name = "워크플로우", description = "캔버스에서 만든 워크플로우를 저장하고 다시 불러온다.")
public interface PlaceableItemApi {
    @Operation(summary = "캔버스에 놓을 수 있는 항목 목록",
            description = """
                    마켓에 공개된 에이전트를 한 목록으로 내려준다. 내 에이전트가 먼저 오고, 그 뒤에 다른 사람의
                    에이전트가 온다. 각각은 최신순이다. 기본 노드는 담지 않는다 — 배치 본문으로 저장할 때 만들어진다.

                    `MINE` 은 내 에이전트만, `ALL` 은 다른 사람의 에이전트까지 담는다.
                    검색어는 이름만 본다. 실행 이력이 아직 없어 `executionCount` 는 0 이다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 완료"),
            @ApiResponse(responseCode = "400", description = "범위나 개수, 커서가 올바르지 않다 (`COMMON_001`)",
                    content = @Content)
    })
    ApiResult<PlaceableItemPageResponse> find(
            UserId requester,
            @Parameter(description = "`MINE` 은 내 에이전트만, `ALL` 은 다른 사람의 에이전트까지", required = true,
                    example = "ALL") PlaceableScope scope,
            @Parameter(description = "이름 부분 검색어. 비우면 전체", example = "성적") String keyword,
            @Parameter(description = "이전 응답의 `nextCursor`. 첫 페이지는 비운다", example = "MjA") String cursor,
            @Parameter(description = "한 번에 가져올 개수. 1 이상 30 이하", example = "20") @Positive @Max(30) int size);
}
