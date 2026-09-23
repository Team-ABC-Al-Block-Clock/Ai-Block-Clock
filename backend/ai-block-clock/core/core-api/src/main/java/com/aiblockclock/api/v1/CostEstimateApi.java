package com.aiblockclock.api.v1;

import com.aiblockclock.api.v1.request.CostEstimateRequest;
import com.aiblockclock.api.v1.response.CostEstimateResponse;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.support.response.ApiResult;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

@Tag(name = "실행 견적", description = "실행하기 전에 드는 ABC 와 모자란 금액을 알려 준다.")
public interface CostEstimateApi {
    @Operation(summary = "실행 견적 조회",
            description = """
                    저장된 워크플로우를 돌리면 ABC 가 얼마 드는지, 지금 잔액으로 모자라지 않은지 알려 준다.
                    **아무것도 차감하거나 기록하지 않는다.**

                    노드는 원가를, 에이전트는 놓을 때 박힌 버전의 판매가(원가 × 등급 할증)를 더한다.
                    에이전트는 속을 펼치지 않고 한 줄로 적는다.

                    `executable` 이 `false` 면 `shortageAmount` 만큼 충전을 안내한다.
                    견적을 본 뒤 잔액이 바뀔 수 있어, 실제로 막는 것은 실행을 접수할 때다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "견적"),
            @ApiResponse(responseCode = "400", description = "요청 본문이 올바르지 않다 (`COMMON_001`)", content = @Content),
            @ApiResponse(responseCode = "401", description = "로그인하지 않았다", content = @Content),
            @ApiResponse(responseCode = "403", description = "남의 워크플로우다 (`WORKFLOW_003`)", content = @Content),
            @ApiResponse(responseCode = "404", description = "없는 워크플로우다 (`WORKFLOW_002`)", content = @Content)
    })
    ApiResult<CostEstimateResponse> estimate(UserId requester, @Valid CostEstimateRequest request);
}
