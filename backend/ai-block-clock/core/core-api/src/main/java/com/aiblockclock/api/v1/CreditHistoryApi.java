package com.aiblockclock.api.v1;

import com.aiblockclock.api.v1.response.CreditHistoryPageResponse;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.enums.payment.CreditHistoryType;
import com.aiblockclock.support.response.ApiResult;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;

@Tag(name = "충전 · 출금 내역", description = """
        내 지갑과 서비스 사이에서 abcToken 이 오간 기록을 한 목록으로 준다.
        충전(내 지갑 → 서비스)과 출금(서비스 → 내 지갑)의 상태와 체인 거래 해시를 함께 준다.
        체인에 직접 묻지 않는다. 상태는 서버가 체인을 확인하며 적어 둔 값이다.
        """)
public interface CreditHistoryApi {
    @Operation(summary = "내 충전 · 출금 내역",
            description = """
                    충전 요청과 출금 신청을 요청 시각의 최신순으로 섞어서 준다. 취소 · 반려 · 실패한 것도 모두 나온다.
                    처리 중인 출금(`PAYING` · `ON_HOLD`)은 이 목록을 다시 불러 상태를 확인한다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회됨"),
            @ApiResponse(responseCode = "400", description = "종류 값이나 커서가 잘못됨", content = @Content),
            @ApiResponse(responseCode = "401", description = "로그인하지 않음", content = @Content)
    })
    ApiResult<CreditHistoryPageResponse> getHistory(
            UserId owner,
            @Parameter(description = "CHARGE · WITHDRAWAL 중 하나. 생략하면 둘 다") CreditHistoryType type,
            @Parameter(description = "이전 응답의 nextCursor") String cursor,
            @Parameter(description = "한 페이지 크기") @Positive @Max(50) int size);
}
