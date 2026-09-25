package com.aiblockclock.api.v1;

import com.aiblockclock.api.v1.request.ExecutionStartRequest;
import com.aiblockclock.api.v1.response.ExecutionProgressResponse;
import com.aiblockclock.api.v1.response.ExecutionStartResponse;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.enums.execution.ExecutionResultFormat;
import com.aiblockclock.support.response.ApiResult;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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

                    **멎었으면 `failure` 가 함께 온다.** 어느 단계에서 무슨 코드로 멎었는지,
                    그리고 누구 탓인지(`supplier` · `consumer` · `platform`)를 담는다.
                    실행 엔진이 매긴 코드를 그대로 넘기므로 값이 늘 수 있다 — 모르는 코드는 일반 문구로 받는다.

                    엔진에 넘기지 못한 실행은 엔진에 묻지 않고 우리 기록(`FAILED`)으로 답한다.
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

    @Operation(summary = "실행 결과 받기",
            description = """
                    끝난 실행의 결과를 파일로 내려준다. **봉투가 아니라 파일 자체를 돌려주므로
                    `ApiResult` 로 감싸지 않는다.**

                    결과는 실행이 끝나는 것을 본 순간 실행 엔진에서 받아 보관해 둔다. 엔진은 결과를 임시
                    저장소에 두고 재기동하면 잊기 때문이다. 받아 두지 못했으면 이 요청에서 한 번 더 시도한다.

                    `format` 을 비우면 결과 JSON 이다. PDF 는 렌더가 실패해도 실행은 성공이므로
                    없을 수 있고, 그때는 `404` 다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "결과 파일"),
            @ApiResponse(responseCode = "404", description = """
                    없는 실행이거나 내 실행이 아니다.
                    그 형식으로 받을 수 있는 결과가 없다 (`EXECUTION_001`)""", content = @Content),
            @ApiResponse(responseCode = "409", description = "아직 끝나지 않은 실행이다 (`EXECUTION_001`)",
                    content = @Content),
            @ApiResponse(responseCode = "502", description = "실행 엔진에 닿지 못했다 (`EXECUTION_002`)",
                    content = @Content)
    })
    ResponseEntity<Resource> download(
            UserId requester,
            @Parameter(description = "접수 때 받은 실행 ID", required = true,
                    example = "8a1c3b7e-2a44-4d1e-9b77-0b2a6f4e5c13") UUID executionId,
            @Parameter(description = "받을 형식. 비우면 `JSON`") ExecutionResultFormat format);

    @Operation(summary = "진행 상황 구독",
            description = """
                    `text/event-stream` 을 열어 두면 상태가 바뀔 때마다 밀어 준다. 물어볼 필요가 없다.

                    엔진을 두드리는 것은 백엔드 한 곳이다. 같은 실행을 여러 창에서 봐도 조회는 한 줄기로 돈다.

                    이벤트 이름은 `progress` 하나이고, 본문은 진행 상황 조회 응답과 같은 모양이다.
                    붙는 즉시 지금 상태가 한 번 오고, 그 뒤로는 **달라졌을 때만** 온다.

                    `status` 가 `SUCCEEDED` · `FAILED` 인 이벤트가 마지막이고 곧바로 연결을 닫는다.
                    이미 끝난 실행에 붙으면 그 상태 하나만 주고 닫는다.

                    끊겼을 때는 다시 붙으면 된다. 지금 상태부터 다시 받는다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "이벤트 스트림",
                    content = @Content(mediaType = MediaType.TEXT_EVENT_STREAM_VALUE,
                            schema = @Schema(implementation = ExecutionProgressResponse.class))),
            @ApiResponse(responseCode = "404", description = "없는 실행이거나 내 실행이 아니다 (`EXECUTION_001`)",
                    content = @Content),
            @ApiResponse(responseCode = "502", description = "실행 엔진에 닿지 못했다 (`EXECUTION_002`)", content = @Content)
    })
    SseEmitter stream(
            UserId requester,
            @Parameter(description = "접수 때 받은 실행 ID", required = true,
                    example = "8a1c3b7e-2a44-4d1e-9b77-0b2a6f4e5c13") UUID executionId);
}
