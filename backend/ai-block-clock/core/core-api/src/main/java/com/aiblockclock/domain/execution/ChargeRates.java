package com.aiblockclock.domain.execution;

import com.aiblockclock.domain.workflow.NodeKey;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// 엔진에 보낸 단계마다 실측 원가에 곱할 배율이다. 단계는 RuntimeWorkflow 가 붙인 번호로 찾는다.
// 접수할 때 정해 남겨 두므로, 실행 중에 등급이 바뀌거나 워크플로우를 고쳐도 접수한 순간의 배율로 센다.
public record ChargeRates(Map<String, BigDecimal> byStep) {
    public ChargeRates {
        byStep = Map.copyOf(byStep);
    }

    public static ChargeRates of(ExecutionPlan plan, CostEstimate estimate) {
        Map<NodeKey, BigDecimal> byPlaced = estimate.items().stream()
                .collect(Collectors.toMap(CostItem::nodeKey, CostItem::priceMultiplier));
        List<PlannedPrompt> prompts = plan.prompts();
        Map<String, BigDecimal> byStep = new LinkedHashMap<>();

        for (int index = 0; index < prompts.size(); index++) {
            byStep.put(RuntimeWorkflow.stepIdOf(index), byPlaced.get(prompts.get(index).placedKey()));
        }

        return new ChargeRates(byStep);
    }

    // 배율을 남기기 전에 접수한 실행은 기록이 없다. 그때는 원가만 받는다.
    public BigDecimal of(String stepId) {
        return byStep.getOrDefault(stepId, BigDecimal.ONE);
    }
}
