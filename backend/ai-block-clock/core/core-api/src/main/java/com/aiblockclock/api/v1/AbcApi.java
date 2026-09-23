package com.aiblockclock.api.v1;

import com.aiblockclock.api.v1.response.AbcBalanceResponse;
import com.aiblockclock.api.v1.response.AbcTransactionPageResponse;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.enums.payment.LedgerEventType;
import com.aiblockclock.support.response.ApiResult;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

@Tag(name = "ABC 잔액·사용 내역", description = """
        로그인 사용자의 ABC 장부를 읽는다.
        잔액과 내역은 오프체인 장부에서만 읽으며 온체인을 조회하지 않는다.
        """)
public interface AbcApi {
    @Operation(summary = "내 ABC 잔액",
            description = "지금 쓸 수 있는 금액과 실행 시작 때 선차감되어 결과를 기다리는 금액을 함께 준다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회됨"),
            @ApiResponse(responseCode = "401", description = "로그인하지 않음", content = @io.swagger.v3.oas.annotations.media.Content)
    })
    ApiResult<AbcBalanceResponse> getBalance(UserId userId);

    @Operation(summary = "내 ABC 사용 내역",
            description = "충전·실행 선차감·환불을 최신순으로 준다. 기간은 시작·종료를 함께 주거나 둘 다 생략한다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회됨"),
            @ApiResponse(responseCode = "400", description = "기간이 한쪽만 있거나 순서가 뒤집혔거나 커서가 잘못됨", content = @io.swagger.v3.oas.annotations.media.Content),
            @ApiResponse(responseCode = "401", description = "로그인하지 않음", content = @io.swagger.v3.oas.annotations.media.Content)
    })
    ApiResult<AbcTransactionPageResponse> getTransactions(
            UserId userId,
            @Parameter(description = "CHARGE · RESERVE · REFUND 중 하나. 생략하면 전부") LedgerEventType transactionType,
            @Parameter(description = "조회 시작일 (YYYY-MM-DD, UTC)") LocalDate from,
            @Parameter(description = "조회 종료일 (YYYY-MM-DD, UTC, 포함)") LocalDate to,
            @Parameter(description = "이전 응답의 nextCursor") String cursor,
            @Parameter(description = "한 페이지 크기") @Positive @Max(50) int size);
}
