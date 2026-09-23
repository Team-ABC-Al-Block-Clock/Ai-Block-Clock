package com.aiblockclock.api.v1;

import com.aiblockclock.api.v1.request.AgentRegisterRequest;
import com.aiblockclock.api.v1.request.AgentUpdateRequest;
import com.aiblockclock.api.v1.response.AgentRegisterResponse;
import com.aiblockclock.api.v1.response.AgentUpdateResponse;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.support.response.ApiResult;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import java.util.UUID;

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
            @ApiResponse(responseCode = "400", description = "요청 본문이 올바르지 않다 (`COMMON_001`)", content = @Content),
            @ApiResponse(responseCode = "403", description = "남의 워크플로우이거나 남의 노드·에이전트가 섞였다 (`WORKFLOW_003`, `AGENT_002`)",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "없는 워크플로우이거나 배치된 노드가 사라졌다 (`WORKFLOW_002`, `WORKFLOW_015`)",
                    content = @Content),
            @ApiResponse(responseCode = "409", description = "회차가 어긋났다 (`WORKFLOW_016`)", content = @Content)
    })
    ApiResult<AgentRegisterResponse> register(UserId owner, @Valid AgentRegisterRequest request);

    @Operation(summary = "에이전트 수정",
            description = """
                    공개된 에이전트를 다시 굳힌다. 내 에이전트여야 하고, 내 노드로만 이루어진 워크플로우여야 한다.

                    **앞 버전이 굳힌 워크플로우와 회차가 그대로면 버전은 오르지 않는다.** 이름·소개만 바뀐다.
                    원본을 고쳐 회차가 올랐거나 다른 워크플로우를 실어 보내면 그때 새 버전을 굳힌다.

                    `revision` 은 사용자가 보던 회차다. 그 사이 워크플로우가 수정됐으면 `WORKFLOW_016` 으로 거부한다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정됨"),
            @ApiResponse(responseCode = "400", description = "요청 본문이 올바르지 않다 (`COMMON_001`)", content = @Content),
            @ApiResponse(responseCode = "403", description = "남의 에이전트·워크플로우이거나 남의 노드·에이전트가 섞였다 (`AGENT_006`, `WORKFLOW_003`, `AGENT_002`)",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "없는 에이전트이거나 없는 워크플로우, 배치된 노드가 사라졌다 (`AGENT_003`, `WORKFLOW_002`, `WORKFLOW_015`)",
                    content = @Content),
            @ApiResponse(responseCode = "409", description = "회차가 어긋났다 (`WORKFLOW_016`)", content = @Content)
    })
    ApiResult<AgentUpdateResponse> update(UserId owner, UUID agentId, @Valid AgentUpdateRequest request);
}
