package com.aiblockclock.api.v1;

import com.aiblockclock.api.v1.request.ChargeCreateRequest;
import com.aiblockclock.api.v1.response.ChargeStatusResponse;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.support.response.ApiResult;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

@Tag(name = "ABC 충전", description = """
        abcToken 을 보관 지갑으로 보낸 거래를 체인에서 확인하고 같은 수만큼 ABC 를 적립한다.
        abcToken 1개 = ABC 1 이다.
        """)
public interface ChargeApi {
    @Operation(summary = "충전 요청",
            description = """
                    MetaMask 로 abcToken 컨트랙트의 `transfer(보관 지갑, 금액 × 10^18)` 을 보낸 뒤,
                    돌려받은 거래 해시를 **바로** 보낸다. 거래가 블록에 적히기를 기다리지 않아도 된다.

                    받자마자 체인에 한 번 물어본다. 이미 블록에 적혔으면 그 자리에서 `CONFIRMED` 가 오고,
                    아니면 `PENDING` 이 온다. `PENDING` 이면 `GET /credits/charges/{chargeId}` 로 상태를 다시 묻는다.
                    서버도 10초마다 대기 중인 충전을 다시 확인한다. 30분이 지나도 블록에 적히지 않으면 `FAILED` 로 닫는다.

                    **보낸 지갑은 로그인한 지갑이어야 한다.** 같은 해시로 다시 보내면 새로 만들지 않고 앞의 요청을 돌려준다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "접수됨. `status` 로 확인 결과를 본다"),
            @ApiResponse(responseCode = "400", description = """
                    요청 본문이 올바르지 않다 (`COMMON_001`).
                    금액이 0 이하이거나 소수 6자리를 넘는다 (`PAYMENT_005`)""", content = @Content),
            @ApiResponse(responseCode = "401", description = "로그인하지 않음", content = @Content),
            @ApiResponse(responseCode = "403", description = "로그인한 지갑이 입금 보관 지갑이다 (`PAYMENT_009`)",
                    content = @Content),
            @ApiResponse(responseCode = "409", description = "다른 사용자가 이미 이 거래로 충전을 요청했다 (`PAYMENT_003`)",
                    content = @Content)
    })
    ApiResult<ChargeStatusResponse> create(UserId requester, @Valid ChargeCreateRequest request);

    @Operation(summary = "충전 상태 조회",
            description = "`status` 가 `CONFIRMED` 또는 `FAILED` 가 되면 더 묻지 않는다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회됨"),
            @ApiResponse(responseCode = "401", description = "로그인하지 않음", content = @Content),
            @ApiResponse(responseCode = "403", description = "남의 충전 요청이다 (`PAYMENT_002`)", content = @Content),
            @ApiResponse(responseCode = "404", description = "없는 충전 요청이다 (`PAYMENT_001`)", content = @Content)
    })
    ApiResult<ChargeStatusResponse> get(UserId requester, Long chargeId);
}
