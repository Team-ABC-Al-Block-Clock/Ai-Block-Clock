package com.aiblockclock.api.v1;

import com.aiblockclock.api.v1.request.WithdrawalCreateRequest;
import com.aiblockclock.api.v1.response.WithdrawalResponse;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.support.response.ApiResult;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

@Tag(name = "정산금 출금", description = """
        공급자로 받은 정산금을 로그인한 지갑으로 abcToken 으로 보내 달라고 신청한다. ABC 1 = abcToken 1개다.
        신청하면 그 금액을 정산금에서 빼 묶어 두고, 관리자 심사를 거쳐 지급한다.
        취소하면 묶은 금액이 정산금으로 돌아온다.
        """)
public interface WithdrawalApi {
    @Operation(summary = "출금 신청",
            description = """
                    정산금(`GET /credits/balance` 의 `payoutAmount`)에서 신청한 금액을 묶는다. 묶인 금액은 `payoutAmount` 에서 빠진다.
                    받을 지갑은 요청 본문이 아니라 **로그인한 지갑**이다.

                    - 한 번에 1 ~ 1,000 ABC 까지 신청한다.
                    - 처리 중인 출금이 있으면 새로 신청할 수 없다.
                    - 마지막 신청에서 24시간이 지나야 다시 신청할 수 있다. 취소한 신청은 세지 않는다.

                    같은 `requestId` 로 다시 보내면 새로 묶지 않고 앞의 신청을 돌려준다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "접수됨. 심사를 기다린다 (`REQUESTED`)"),
            @ApiResponse(responseCode = "400", description = """
                    요청 본문이 올바르지 않다 (`COMMON_001`).
                    금액이 한 번에 신청할 수 있는 범위를 벗어났다 (`PAYMENT_010`, `data` 에 `minimumAmount` · `maximumAmount`)""",
                    content = @Content),
            @ApiResponse(responseCode = "401", description = "로그인하지 않음", content = @Content),
            @ApiResponse(responseCode = "409", description = """
                    정산금이 모자란다 (`PAYMENT_011`, `data` 에 `payoutAmount`).
                    처리 중인 출금이 있다 (`PAYMENT_012`).
                    신청 주기가 지나지 않았다 (`PAYMENT_013`, `data` 에 다시 신청할 수 있는 `availableAt`).
                    같은 `requestId` 의 신청이 동시에 들어와 처리 중이다 (`PAYMENT_014`)""",
                    content = @Content)
    })
    ApiResult<WithdrawalResponse> create(UserId requester, @Valid WithdrawalCreateRequest request);

    @Operation(summary = "출금 신청 취소",
            description = """
                    심사를 기다리는 신청(`REQUESTED`)만 취소할 수 있다. 묶은 금액이 정산금으로 돌아온다.
                    이미 취소한 신청이면 그대로 돌려준다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "취소됨 (`CANCELED`)"),
            @ApiResponse(responseCode = "401", description = "로그인하지 않음", content = @Content),
            @ApiResponse(responseCode = "404", description = "없거나 남의 출금 신청이다 (`PAYMENT_015`)", content = @Content),
            @ApiResponse(responseCode = "409", description = "심사가 끝나 취소할 수 없다 (`PAYMENT_016`)", content = @Content)
    })
    ApiResult<WithdrawalResponse> cancel(UserId requester, Long withdrawalId);
}
