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

                    **`multipart/form-data` 로 보낸다.** 첫 단계가 읽을 파일을 함께 싣기 때문이다.
                    파일은 엑셀(`.xlsx` · `.xls`) 또는 CSV 이고 10MB 까지다. 없이 보내도 된다.
                    올린 원본은 우리가 보관해 재실행에 다시 쓴다.

                    단계 하나가 25초쯤 걸려 기다리지 않는다. 접수 번호만 즉시 돌려주고,
                    진행 상황은 `GET /runs/{executionId}` 를 2초 간격으로 물어 가져간다.

                    **엔진에 넘기기 전에 예상 차감액을 ABC 잔액에서 떼어 둔다(선차감).** 금액은
                    `POST /executions/cost-estimates` 의 `estimatedAmount` 와 같은 규칙으로 센다.
                    잔액이 모자라면 엔진을 부르지 않고 402 로 막는다. 엔진에 맡기지 못하면(502) 떼어 둔 금액을 돌려준다.
                    `mock` 실행도 똑같이 뗀다.

                    `requestId` 는 누름 한 번에 하나다. 같은 값으로 다시 보내면 새로 떼지 않고 앞서 접수한 실행을 돌려준다.

                    `mock` 을 켜면 모델을 부르지 않고 파이프라인만 돈다. 형식을 맞춰 볼 때 쓴다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "접수됨. 실행은 뒤에서 돈다. 같은 `requestId` 로 다시 보내도 같은 실행이 나온다"),
            @ApiResponse(responseCode = "400", description = """
                    요청 본문이 올바르지 않다 (`COMMON_001`).
                    빈 파일이거나 엑셀 · CSV 가 아니다 (`EXECUTION_003`).
                    분기가 있어 아직 실행할 수 없다 (`EXECUTION_005`)""", content = @Content),
            @ApiResponse(responseCode = "402", description = """
                    ABC 잔액이 모자란다 (`PAYMENT_008`). `error.data` 에 `estimatedAmount` · `availableAmount` ·
                    `shortageAmount` 가 온다""", content = @Content),
            @ApiResponse(responseCode = "403", description = "남의 워크플로우다 (`WORKFLOW_003`)", content = @Content),
            @ApiResponse(responseCode = "404", description = "없는 워크플로우다 (`WORKFLOW_002`)", content = @Content),
            @ApiResponse(responseCode = "409", description = "같은 `requestId` 가 아직 처리 중이다 (`EXECUTION_006`)",
                    content = @Content),
            @ApiResponse(responseCode = "413", description = "파일이 10MB 를 넘는다 (`FILE_002`)", content = @Content),
            @ApiResponse(responseCode = "502", description = "실행 엔진에 맡기지 못했다. 떼어 둔 금액은 돌려준다 (`EXECUTION_002`)",
                    content = @Content)
    })
    ApiResult<ExecutionStartResponse> start(UserId requester, @Valid ExecutionStartRequest request);

    @Operation(summary = "진행 상황 조회",
            description = """
                    2초 간격으로 부른다. `status` 가 `SUCCEEDED` 또는 `FAILED` 가 되면 멈춘다.

                    실행 엔진은 진행 상황을 메모리에만 들고 있어 재기동하면 잊는다.
                    우리 쪽 상태는 조회할 때마다 최신으로 맞춰 둔다.

                    엔진에 넘기지 못한 실행은 엔진에 묻지 않고 우리 기록(`FAILED`)으로 답한다.

                    **끝난 것을 처음 본 조회에서 떼어 둔 금액 중 쓰지 않은 만큼을 ABC 잔액으로 돌려준다.**
                    쓴 만큼은 성공한 단계의 실측 원가에 등급 할증을 곱해 더한 값이고, 떼어 둔 금액을 넘지 않는다.
                    실패한 단계와 돌지 않은 단계는 세지 않는다. 플랫폼 탓으로 실패하면 전부 돌려준다.
                    `mock` 실행은 실측 원가가 0 이라 전부 돌아온다. 여러 번 조회해도 한 번만 돌려준다.
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
