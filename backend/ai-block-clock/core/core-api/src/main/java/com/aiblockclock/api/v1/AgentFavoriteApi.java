package com.aiblockclock.api.v1;

import com.aiblockclock.api.v1.response.AgentFavoriteResponse;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.support.response.ApiResult;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.UUID;

@Tag(name = "마켓", description = "공개된 에이전트를 찾는다.")
public interface AgentFavoriteApi {
    @Operation(summary = "즐겨찾기 등록",
            description = """
                    마켓에 공개된 에이전트를 즐겨찾기에 넣는다.

                    **여러 번 보내도 한 번 넣은 것과 같다.** 이미 넣은 것을 다시 보내도 오류가 아니고,
                    지금 상태와 즐겨찾기한 사람 수를 그대로 돌려준다. 화면은 상세를 다시 부르지 않고 이 값으로 덮으면 된다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "넣었거나 이미 들어 있다"),
            @ApiResponse(responseCode = "400", description = "에이전트 ID 가 UUID 모양이 아니다 (`COMMON_001`)",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "없는 에이전트이거나 공개되지 않았다 (`MARKET_001`)",
                    content = @Content)
    })
    ApiResult<AgentFavoriteResponse> add(
            UserId user,
            @Parameter(description = "에이전트 ID", required = true,
                    example = "33567ab2-e08b-461b-a0f2-51a857df5ec7") UUID agentId);

    @Operation(summary = "즐겨찾기 해제",
            description = """
                    마켓에 공개된 에이전트를 즐겨찾기에서 뺀다.

                    **여러 번 보내도 한 번 뺀 것과 같다.** 즐겨찾기하지 않은 것을 빼도 오류가 아니고,
                    지금 상태와 즐겨찾기한 사람 수를 그대로 돌려준다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "뺐거나 원래 없었다"),
            @ApiResponse(responseCode = "400", description = "에이전트 ID 가 UUID 모양이 아니다 (`COMMON_001`)",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "없는 에이전트이거나 공개되지 않았다 (`MARKET_001`)",
                    content = @Content)
    })
    ApiResult<AgentFavoriteResponse> remove(
            UserId user,
            @Parameter(description = "에이전트 ID", required = true,
                    example = "33567ab2-e08b-461b-a0f2-51a857df5ec7") UUID agentId);
}
