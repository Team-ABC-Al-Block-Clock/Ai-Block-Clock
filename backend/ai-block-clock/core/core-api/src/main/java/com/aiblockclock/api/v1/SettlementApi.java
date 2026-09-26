package com.aiblockclock.api.v1;

import com.aiblockclock.api.v1.response.SettlementPageResponse;
import com.aiblockclock.api.v1.response.SettlementStatisticsResponse;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.enums.execution.SettlementPeriodUnit;
import com.aiblockclock.support.response.ApiResult;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;
import java.util.UUID;

@Tag(name = "정산 내역·수익 통계", description = """
        공급자가 자기 에이전트 · 노드로 받은 정산을 읽는다. 늘 로그인 사용자 것만 준다.
        정산금 잔액은 ABC 잔액(GET /api/v1/credits/balance)의 payoutAmount 로 준다.
        """)
public interface SettlementApi {
    @Operation(summary = "내 정산 내역",
            description = "끝난 실행에서 성공한 단계마다 받은 정산금을 최신순으로 준다. 기간은 시작 · 종료를 함께 주거나 둘 다 생략한다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회됨. 받은 것이 없으면 빈 목록"),
            @ApiResponse(responseCode = "400", description = "기간이 한쪽만 있거나 순서가 뒤집혔거나 커서가 잘못됨", content = @Content),
            @ApiResponse(responseCode = "401", description = "로그인하지 않음", content = @Content)
    })
    ApiResult<SettlementPageResponse> getSettlements(
            UserId userId,
            @Parameter(description = "조회 시작일 (YYYY-MM-DD, UTC)") LocalDate from,
            @Parameter(description = "조회 종료일 (YYYY-MM-DD, UTC, 포함)") LocalDate to,
            @Parameter(description = "이 에이전트로 받은 것만. 생략하면 전부") UUID agentId,
            @Parameter(description = "이전 응답의 nextCursor") String cursor,
            @Parameter(description = "한 페이지 크기") @Positive @Max(50) int size);

    @Operation(summary = "내 수익 통계",
            description = "정산 내역과 같은 조건의 줄을 합계 · 기간별 · 에이전트별로 묶어 준다. 조건이 같으면 합계와 내역의 합이 같다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회됨. 받은 것이 없으면 합계 0 과 빈 목록"),
            @ApiResponse(responseCode = "400", description = "기간이 한쪽만 있거나 순서가 뒤집혔거나 단위가 잘못됨", content = @Content),
            @ApiResponse(responseCode = "401", description = "로그인하지 않음", content = @Content)
    })
    ApiResult<SettlementStatisticsResponse> getStatistics(
            UserId userId,
            @Parameter(description = "조회 시작일 (YYYY-MM-DD, UTC)") LocalDate from,
            @Parameter(description = "조회 종료일 (YYYY-MM-DD, UTC, 포함)") LocalDate to,
            @Parameter(description = "이 에이전트로 받은 것만. 생략하면 전부") UUID agentId,
            @Parameter(description = "기간을 묶는 단위. DAY · MONTH") SettlementPeriodUnit unit);
}
