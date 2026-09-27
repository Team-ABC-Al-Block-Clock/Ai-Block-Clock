package com.aiblockclock.domain.execution;

import com.aiblockclock.domain.workflow.NodeKey;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

// 엔진에 보낸 단계마다 받을 값(단가)이다. 단가 = 그 단계 노드의 원가 × 놓인 자리의 배율(1 + 등급 할증).
// 엔진은 돈을 모르므로(계약 v2.0 8.1) 끝난 뒤 받을 값은 모두 여기서 나온다. 단계는 엔진이 돌려주는 단계 이름으로 찾는다.
// 접수할 때 정해 남겨 두므로, 실행 중에 등급 · 원가가 바뀌거나 워크플로우를 고쳐도 접수한 순간의 값으로 받는다.
public record StepPrices(Map<String, StepPrice> byStep) {
    public StepPrices {
        byStep = Map.copyOf(byStep);
    }

    public static StepPrices of(ExecutionPlan plan, CostEstimate estimate) {
        Map<NodeKey, BigDecimal> byPlaced = estimate.items().stream()
                .collect(Collectors.toMap(CostItem::nodeKey, CostItem::priceMultiplier));
        Map<String, StepPrice> byStep = new LinkedHashMap<>();
        for (PlannedPrompt prompt : plan.prompts()) {
            BigDecimal multiplier = byPlaced.get(prompt.placedKey());
            byStep.put(prompt.stepId(), new StepPrice(multiplier, prompt.baseCost().multiply(multiplier)));
        }

        return new StepPrices(byStep);
    }

    // 단가를 남기기 전에 접수한 실행은 기록이 없다. 그 단계에는 받을 값이 없다.
    public Optional<StepPrice> of(String stepId) {
        return Optional.ofNullable(byStep.get(stepId));
    }
}
