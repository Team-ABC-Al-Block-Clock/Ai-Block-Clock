package com.aiblockclock.api.v1.response;

import com.aiblockclock.domain.execution.CostItem;
import com.aiblockclock.domain.execution.CostQuote;
import com.aiblockclock.enums.workflow.SourceType;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "실행 견적. 실행 전에 보여주는 안내이며, 실제로 막고 차감하는 것은 실행을 접수할 때다")
public record CostEstimateResponse(
        @Schema(description = "예상 차감액. 놓인 노드의 원가와 에이전트의 판매가(내 에이전트는 원가)를 더한 값이다", example = "9.000000")
        String estimatedAmount,

        @Schema(description = "지금 쓸 수 있는 ABC. 다른 실행에 잠긴 금액은 빠져 있다", example = "5.000000")
        String availableAmount,

        @Schema(description = "모자란 ABC. 넉넉하면 0 이다", example = "4.000000")
        String shortageAmount,

        @Schema(description = "모자란 금액이 없으면 true", example = "false")
        boolean executable,

        @Schema(description = "놓인 자리마다 매겨진 값. 연결 순서대로다")
        List<Item> items
) {
    public static CostEstimateResponse from(CostQuote quote) {
        return new CostEstimateResponse(
                quote.estimate().total().value().toPlainString(),
                quote.available().toPlainString(),
                quote.shortage().toPlainString(),
                quote.executable(),
                quote.estimate().items().stream().map(Item::from).toList());
    }

    @Schema(description = "견적의 한 줄. 에이전트는 속을 펼치지 않고 판매가 하나로 적는다")
    public record Item(
            @Schema(description = "캔버스에서 이 자리를 가리키는 키", example = "n2")
            String nodeKey,

            @Schema(description = "놓인 것이 노드인지 에이전트인지")
            SourceType sourceType,

            @Schema(description = "노드 이름 또는 에이전트 이름", example = "분석 에이전트")
            String name,

            @Schema(description = "이 자리의 값. 노드는 원가, 에이전트는 판매가다. 내 에이전트는 할증 없이 원가다", example = "6.000000")
            String price
    ) {
        static Item from(CostItem item) {
            return new Item(item.nodeKey().value(), item.sourceType(), item.name(),
                    item.price().value().toPlainString());
        }
    }
}
