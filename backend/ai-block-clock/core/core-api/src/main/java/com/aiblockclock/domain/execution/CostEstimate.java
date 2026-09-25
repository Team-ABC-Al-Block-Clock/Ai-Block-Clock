package com.aiblockclock.domain.execution;

import com.aiblockclock.domain.AbcAmount;

import java.math.BigDecimal;
import java.util.List;

// 빈 워크플로우는 저장되지 않고 놓인 것마다 값이 있으므로 합계는 늘 0 보다 크다.
public record CostEstimate(AbcAmount total, List<CostItem> items) {
    public CostEstimate {
        items = List.copyOf(items);
    }

    public static CostEstimate of(List<CostItem> items) {
        BigDecimal total = items.stream()
                .map(item -> item.price().value())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new CostEstimate(AbcAmount.of(total), items);
    }
}
