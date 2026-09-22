package com.aiblockclock.api.v1;

import com.aiblockclock.api.v1.request.AgentRegisterRequest;
import com.aiblockclock.api.v1.response.AgentRegisterResponse;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.support.response.ApiResult;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

@Tag(name = "에이전트", description = """
        워크플로우를 굳혀 파는 단위로 만든다. 등록하면 곧바로 공개된다.
        굳힌 그래프는 복사본이라 원본 워크플로우를 고치거나 지워도 바뀌지 않는다.
        """)
public interface AgentApi {
    @Operation(summary = "에이전트 등록",
            description = """
                    워크플로우를 첫 버전으로 굳혀 공개한다. 내 노드로만 이루어진 워크플로우여야 한다.

                    `revision` 은 사용자가 보던 회차다. 그 사이 워크플로우가 수정됐으면 `WORKFLOW_016` 으로 거부한다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "등록됨"),
            @ApiResponse(responseCode = "403", description = "남의 워크플로우이거나 남의 노드·에이전트가 섞였다 (`WORKFLOW_003`, `AGENT_002`)",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "없는 워크플로우 (`WORKFLOW_002`)", content = @Content),
            @ApiResponse(responseCode = "409", description = "회차가 어긋났다 (`WORKFLOW_016`)", content = @Content)
    })
    ApiResult<AgentRegisterResponse> register(UserId owner, @Valid AgentRegisterRequest request);
}
