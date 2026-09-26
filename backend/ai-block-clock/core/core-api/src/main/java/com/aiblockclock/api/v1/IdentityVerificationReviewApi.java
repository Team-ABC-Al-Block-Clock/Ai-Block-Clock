package com.aiblockclock.api.v1;

import com.aiblockclock.api.v1.request.IdentityVerificationRejectRequest;
import com.aiblockclock.api.v1.response.IdentityVerificationListResponse;
import com.aiblockclock.api.v1.response.IdentityVerificationResponse;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.support.response.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(name = "관리자 · 본인 확인 심사", description = """
        관리자 지갑(`admin.wallet-addresses`)으로 로그인한 사람만 부른다. 다른 사용자는 403 을 받는다.
        Swagger 로 부를 때는 FE 에서 관리자 지갑으로 로그인한 같은 브라우저에서 열고,
        `GET /api/v1/auth/csrf` 의 `token` 을 Authorize 의 `X-CSRF-TOKEN` 에 넣는다.
        """)
public interface IdentityVerificationReviewApi {
    @Operation(summary = "심사 대기 본인 확인 목록", description = "심사를 기다리는 본인 확인을 오래 기다린 것부터 돌려준다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회됨"),
            @ApiResponse(responseCode = "401", description = "로그인하지 않음", content = @Content),
            @ApiResponse(responseCode = "403", description = "관리자가 아니다", content = @Content)
    })
    ApiResult<IdentityVerificationListResponse> getSubmitted();

    @Operation(summary = "본인 확인 승인",
            description = "승인하면 그 사용자가 출금을 신청할 수 있다. 이미 승인했으면 그대로 돌려준다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "승인됨 (`APPROVED`)"),
            @ApiResponse(responseCode = "401", description = "로그인하지 않음", content = @Content),
            @ApiResponse(responseCode = "403", description = "관리자가 아니다", content = @Content),
            @ApiResponse(responseCode = "404", description = "없는 본인 확인이다 (`USER_009`)", content = @Content),
            @ApiResponse(responseCode = "409", description = "심사 대기가 아니다 (`USER_010`)", content = @Content)
    })
    ApiResult<IdentityVerificationResponse> approve(UserId approver, Long verificationId);

    @Operation(summary = "본인 확인 반려",
            description = "사유를 남긴다. 사용자는 사유를 보고 고쳐 다시 낼 수 있다. 이미 반려했으면 그대로 돌려준다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "반려됨 (`REJECTED`)"),
            @ApiResponse(responseCode = "400", description = "사유가 비었거나 200자를 넘는다 (`COMMON_001`)",
                    content = @Content),
            @ApiResponse(responseCode = "401", description = "로그인하지 않음", content = @Content),
            @ApiResponse(responseCode = "403", description = "관리자가 아니다", content = @Content),
            @ApiResponse(responseCode = "404", description = "없는 본인 확인이다 (`USER_009`)", content = @Content),
            @ApiResponse(responseCode = "409", description = "심사 대기가 아니다 (`USER_010`)", content = @Content)
    })
    ApiResult<IdentityVerificationResponse> reject(
            UserId rejecter, Long verificationId, @Valid IdentityVerificationRejectRequest request);
}
