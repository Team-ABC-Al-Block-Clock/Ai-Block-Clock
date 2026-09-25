package com.aiblockclock.api.v1.response;

import com.aiblockclock.domain.execution.ExecutionSettlement;
import com.aiblockclock.domain.execution.SettlementRecord;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "정산 내역 한 줄. 끝난 실행에서 성공한 단계 하나가 남긴 정산금이다")
public record SettlementResponse(
        @Schema(description = "정산이 생긴 실행 ID", example = "3f0f7c1e-2f1b-4c55-9a53-2b3f0a4b8f10")
        String executionId,

        @Schema(description = "그 실행에서 몇 번째 단계였는지 가리키는 단계 ID", example = "n2")
        String stepId,

        @Schema(description = "실행할 때의 노드 이름. 노드를 고치거나 지워도 그때 이름이 남는다", example = "시장 조사", nullable = true)
        String nodeName,

        @Schema(description = "에이전트 안의 단계면 그 에이전트 ID. 캔버스에 바로 놓인 노드면 null", nullable = true)
        String agentId,

        @Schema(description = "받은 정산금. 수수료가 없어 사용자가 그 단계에 낸 값 전부다", example = "0.750000")
        String amount,

        @Schema(description = "내 에이전트 · 노드를 내가 실행해 받은 것인지", example = "false")
        boolean selfUse,

        @Schema(description = "정산된 시각")
        Instant settledAt
) {
    public static SettlementResponse from(SettlementRecord record) {
        ExecutionSettlement settlement = record.settlement();

        return new SettlementResponse(
                record.executionId().value().toString(),
                settlement.stepId(),
                settlement.nodeName(),
                settlement.agent() == null ? null : settlement.agent().value().toString(),
                settlement.amount().value().toPlainString(),
                record.selfUse(),
                record.settledAt()
        );
    }
}
