package com.aiblockclock.api.v1;

import com.aiblockclock.api.v1.request.WithdrawalRejectRequest;
import com.aiblockclock.api.v1.response.PendingWithdrawalListResponse;
import com.aiblockclock.api.v1.response.WithdrawalResponse;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.support.response.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(name = "관리자 · 출금 심사", description = """
        관리자 지갑(`admin.wallet-addresses`)으로 로그인한 사람만 부른다. 다른 사용자는 403 을 받는다.
        승인한 신청만 abcToken 지급(PAY-025)으로 넘어간다. 이 API 는 체인에 아무것도 보내지 않는다.
        Swagger 로 부를 때는 FE 에서 관리자 지갑으로 로그인한 같은 브라우저에서 열고,
        `GET /api/v1/auth/csrf` 의 `token` 을 Authorize 의 `X-CSRF-TOKEN` 에 넣는다.
        """)
public interface WithdrawalReviewApi {
    @Operation(summary = "심사 대기 출금 목록",
            description = "심사를 기다리는 출금 신청(`REQUESTED`)을 오래된 것부터, 신청한 사람의 본인 확인과 함께 돌려준다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회됨"),
            @ApiResponse(responseCode = "401", description = "로그인하지 않음", content = @Content),
            @ApiResponse(responseCode = "403", description = "관리자가 아니다", content = @Content)
    })
    ApiResult<PendingWithdrawalListResponse> getPending();

    @Operation(summary = "출금 승인",
            description = """
                    승인하면 지급을 기다린다(`APPROVED`). 금액은 지급이 끝날 때까지 묶여 있고, 공급자는 더 이상 취소할 수 없다.
                    이미 승인했으면 그대로 돌려준다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "승인됨 (`APPROVED`)"),
            @ApiResponse(responseCode = "401", description = "로그인하지 않음", content = @Content),
            @ApiResponse(responseCode = "403", description = "관리자가 아니다", content = @Content),
            @ApiResponse(responseCode = "404", description = "없는 출금 신청이다 (`PAYMENT_015`)", content = @Content),
            @ApiResponse(responseCode = "409", description = "심사 대기가 아니다. 취소 · 반려됐다 (`PAYMENT_016`)",
                    content = @Content)
    })
    ApiResult<WithdrawalResponse> approve(UserId approver, Long withdrawalId);

    @Operation(summary = "출금 반려",
            description = """
                    사유를 남기고 묶은 금액을 정산금으로 돌려놓는다(`REJECTED`). 반려는 신청 주기에 세지 않아 공급자가 곧바로 다시 신청할 수 있다.
                    이미 반려했으면 그대로 돌려준다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "반려됨 (`REJECTED`)"),
            @ApiResponse(responseCode = "400", description = "사유가 비었거나 200자를 넘는다 (`COMMON_001`)",
                    content = @Content),
            @ApiResponse(responseCode = "401", description = "로그인하지 않음", content = @Content),
            @ApiResponse(responseCode = "403", description = "관리자가 아니다", content = @Content),
            @ApiResponse(responseCode = "404", description = "없는 출금 신청이다 (`PAYMENT_015`)", content = @Content),
            @ApiResponse(responseCode = "409", description = "심사 대기가 아니다. 취소 · 승인됐다 (`PAYMENT_016`)",
                    content = @Content)
    })
    ApiResult<WithdrawalResponse> reject(UserId rejecter, Long withdrawalId, @Valid WithdrawalRejectRequest request);
}
