package com.aiblockclock.api.v1;

import com.aiblockclock.api.v1.request.PayoutConversionRequest;
import com.aiblockclock.api.v1.response.PayoutConversionResponse;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.support.response.ApiResult;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

@Tag(name = "정산금 옮기기", description = """
        공급자로 받은 정산금을 실행에 쓸 수 있는 ABC 로 옮긴다. 출금했다가 다시 충전하지 않아도 번 ABC 를 쓸 수 있다.
        서비스 장부 안에서만 옮기며 체인에는 아무것도 보내지 않는다. 한 번 옮긴 ABC 는 다시 정산금으로 돌아가지 않는다.
        """)
public interface PayoutConversionApi {
    @Operation(summary = "정산금을 ABC 로 옮기기",
            description = """
                    정산금(`GET /credits/balance` 의 `payoutAmount`)에서 신청한 만큼 빼 쓸 수 있는 ABC(`availableAmount`)에 더한다.
                    본인 확인 · 금액 범위 · 주기 제한이 없다. 돈이 서비스 밖으로 나가지 않기 때문이다.
                    출금을 신청해 묶인 금액은 이미 `payoutAmount` 에서 빠져 있어 옮길 수 없다.

                    같은 `requestId` 로 다시 보내면 또 옮기지 않고 앞서 옮긴 금액과 지금 잔액을 돌려준다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "옮겼다. 옮긴 금액과 옮긴 뒤 잔액"),
            @ApiResponse(responseCode = "400", description = "요청 본문이 올바르지 않다 (`COMMON_001`)", content = @Content),
            @ApiResponse(responseCode = "401", description = "로그인하지 않음", content = @Content),
            @ApiResponse(responseCode = "409", description = "정산금이 모자란다 (`PAYMENT_018`, `data` 에 `payoutAmount`)",
                    content = @Content)
    })
    ApiResult<PayoutConversionResponse> convert(UserId owner, @Valid PayoutConversionRequest request);
}
