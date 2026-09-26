package com.aiblockclock.api.v1.response;

import com.aiblockclock.domain.execution.SettlementStatistics;
import com.aiblockclock.enums.execution.SettlementPeriodUnit;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

@Schema(description = "수익 통계. 합계 · 기간별 · 에이전트별이 같은 정산 줄을 나눠 담아 서로 합이 맞는다")
public record SettlementStatisticsResponse(
        @Schema(description = "기간을 묶은 단위", example = "DAY")
        SettlementPeriodUnit unit,

        @Schema(description = "조건 안에서 받은 정산금 합계", example = "12.500000")
        String totalAmount,

        @Schema(description = "그중 내 에이전트 · 노드를 내가 실행해 받은 금액", example = "1.000000")
        String selfUseAmount,

        @Schema(description = "정산이 생긴 실행 수. 한 실행에서 단계 여러 개로 받아도 한 번이다", example = "8")
        int executionCount,

        @Schema(description = "기간별 수익. 오래된 것부터, 받은 것이 없는 기간은 빠진다")
        List<PeriodRevenueResponse> periods,

        @Schema(description = "에이전트별 수익과 호출 수. 많이 번 것부터")
        List<AgentRevenueResponse> agents
) {
    @Schema(description = "기간 하나의 수익")
    public record PeriodRevenueResponse(
            @Schema(description = "묶음의 첫날 (UTC). 일 단위면 그날, 월 단위면 그달 1일", example = "2026-09-25")
            LocalDate periodStart,

            @Schema(description = "그 기간에 받은 정산금", example = "3.250000")
            String amount,

            @Schema(description = "그 기간에 정산이 생긴 실행 수", example = "2")
            int executionCount
    ) {
        private static PeriodRevenueResponse from(SettlementStatistics.PeriodRevenue revenue) {
            return new PeriodRevenueResponse(revenue.periodStart(), revenue.amount().toPlainString(),
                    revenue.executionCount());
        }
    }

    @Schema(description = "에이전트 하나의 수익")
    public record AgentRevenueResponse(
            @Schema(description = "에이전트 ID. 캔버스에 바로 놓인 노드로 받은 것은 null 한 줄로 모인다", nullable = true)
            String agentId,

            @Schema(description = "그 에이전트로 받은 정산금", example = "9.000000")
            String amount,

            @Schema(description = "그 에이전트가 쓰인 실행 수 (호출 수)", example = "6")
            int executionCount
    ) {
        private static AgentRevenueResponse from(SettlementStatistics.AgentRevenue revenue) {
            return new AgentRevenueResponse(
                    revenue.agent() == null ? null : revenue.agent().value().toString(),
                    revenue.amount().toPlainString(),
                    revenue.executionCount());
        }
    }

    public static SettlementStatisticsResponse from(SettlementPeriodUnit unit, SettlementStatistics statistics) {
        return new SettlementStatisticsResponse(
                unit,
                statistics.totalAmount().toPlainString(),
                statistics.selfUseAmount().toPlainString(),
                statistics.executionCount(),
                statistics.periods().stream().map(PeriodRevenueResponse::from).toList(),
                statistics.agents().stream().map(AgentRevenueResponse::from).toList()
        );
    }
}
