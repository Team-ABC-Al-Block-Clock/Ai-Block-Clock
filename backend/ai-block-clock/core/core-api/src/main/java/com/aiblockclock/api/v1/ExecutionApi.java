package com.aiblockclock.api.v1;

import com.aiblockclock.api.v1.request.ExecutionStartRequest;
import com.aiblockclock.api.v1.response.ExecutionProgressResponse;
import com.aiblockclock.api.v1.response.ExecutionStartResponse;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.support.response.ApiResult;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import java.util.UUID;

@Tag(name = "실행", description = "워크플로우를 실행하고 진행 상황을 따라간다.")
public interface ExecutionApi {
    @Operation(summary = "실행 시작",
            description = """
                    저장된 워크플로우를 실행 엔진에 맡긴다. **에이전트 자리는 그 버전의 단계들로 펼쳐** 보낸다.

                    단계 하나가 25초쯤 걸려 기다리지 않는다. 접수 번호만 즉시 돌려주고,
                    진행 상황은 `GET /runs/{executionId}` 를 2초 간격으로 물어 가져간다.

                    `mock` 을 켜면 모델을 부르지 않고 파이프라인만 돈다. 형식을 맞춰 볼 때 쓴다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "접수됨. 실행은 뒤에서 돈다"),
            @ApiResponse(responseCode = "400", description = "요청 본문이 올바르지 않다 (`COMMON_001`)", content = @Content),
            @ApiResponse(responseCode = "403", description = "남의 워크플로우다 (`WORKFLOW_003`)", content = @Content),
            @ApiResponse(responseCode = "404", description = "없는 워크플로우다 (`WORKFLOW_002`)", content = @Content),
            @ApiResponse(responseCode = "502", description = "실행 엔진에 맡기지 못했다 (`EXECUTION_002`)", content = @Content)
    })
    ApiResult<ExecutionStartResponse> start(UserId requester, @Valid ExecutionStartRequest request);

    @Operation(summary = "진행 상황 조회",
            description = """
                    2초 간격으로 부른다. `status` 가 `SUCCEEDED` 또는 `FAILED` 가 되면 멈춘다.

                    실행 엔진은 진행 상황을 메모리에만 들고 있어 재기동하면 잊는다.
                    우리 쪽 상태는 조회할 때마다 최신으로 맞춰 둔다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "진행 상황"),
            @ApiResponse(responseCode = "404", description = "없는 실행이거나 내 실행이 아니다 (`EXECUTION_001`)",
                    content = @Content),
            @ApiResponse(responseCode = "502", description = "실행 엔진에 닿지 못했다 (`EXECUTION_002`)", content = @Content)
    })
    ApiResult<ExecutionProgressResponse> follow(
            UserId requester,
            @Parameter(description = "접수 때 받은 실행 ID", required = true,
                    example = "8a1c3b7e-2a44-4d1e-9b77-0b2a6f4e5c13") UUID executionId);
}
