package com.aiblockclock.api.v1;

import com.aiblockclock.api.v1.request.AgentRegisterRequest;
import com.aiblockclock.api.v1.request.AgentUpdateRequest;
import com.aiblockclock.api.v1.response.AgentRegisterResponse;
import com.aiblockclock.api.v1.response.AgentUpdateResponse;
import com.aiblockclock.api.v1.response.MyAgentPageResponse;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.enums.agent.MyAgentSort;
import com.aiblockclock.support.response.ApiResult;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;

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

    @Operation(summary = "판매 중인 내 에이전트 목록",
            description = """
                    내가 등록해 공개한 에이전트를 커서 페이지로 돌려준다. 남의 에이전트는 담지 않는다.

                    `LATEST` 는 최근에 등록한 것부터, `EXECUTIONS` 는 누적 실행 수가 많은 것부터,
                    `REVENUE` 는 받은 정산금이 많은 것부터 준다. 값이 같으면 최근에 등록한 것이 앞에 온다.
                    검색어는 이름과 한 줄 소개를 함께 본다.

                    `revenue` 는 이 에이전트로 받은 정산금을 모두 더한 값이라 수익 통계(`GET /api/v1/settlements/statistics`)의
                    에이전트별 금액과 같다. 내가 돌린 실행으로 받은 것도 들어간다.
                    `trend` 는 최근 7일 동안 날마다 정산을 남긴 실행을 센 값이라, 실패로 끝나 받은 것이 없는 실행은 세지 않는다.

                    `nextCursor` 를 다음 요청의 `cursor` 에 그대로 넣는다. 지금 커서는 오프셋을 감싼 값이라,
                    페이지를 넘기는 사이에 순서가 바뀌면 창이 밀릴 수 있다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 완료. 판매 중인 것이 없으면 빈 목록"),
            @ApiResponse(responseCode = "400", description = "정렬이나 개수, 커서가 올바르지 않다 (`COMMON_001`)",
                    content = @Content),
            @ApiResponse(responseCode = "401", description = "로그인하지 않았다", content = @Content)
    })
    ApiResult<MyAgentPageResponse> findMine(
            UserId requester,
            @Parameter(description = "이름이나 한 줄 소개의 부분 검색어. 비우면 전체", example = "성적") String keyword,
            @Parameter(description = "정렬 기준. 등록순(`LATEST`), 실행 수(`EXECUTIONS`), 정산금(`REVENUE`)",
                    example = "LATEST") MyAgentSort sort,
            @Parameter(description = "이전 응답의 `nextCursor`. 첫 페이지는 비운다", example = "OQ") String cursor,
            @Parameter(description = "한 번에 가져올 개수. 1 이상 30 이하", example = "9") @Positive @Max(30) int size);
}
