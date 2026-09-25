package com.aiblockclock.domain.execution;

import com.aiblockclock.client.ai.RunStatus;
import com.aiblockclock.domain.AbcAmount;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

// 끝난 실행에서 쓴 만큼과 돌려줄 만큼이다. 떼어 둔 것 = 쓴 만큼 + 돌려줄 만큼.
// 쓴 만큼은 성공한 단계의 실측 원가(cost_abc)에 접수할 때 정한 배율을 곱해 더한다.
// 실패한 단계와 돌지 않은 단계는 세지 않는다. 결과물을 받지 못한 단계에 값을 받지 않는다.
public record ExecutionUsage(BigDecimal used, BigDecimal refund) {
    private static final int DECIMAL_PLACES = 6;

    public static ExecutionUsage of(AbcAmount reserved, RunStatus run, ChargeRates rates) {
        BigDecimal held = reserved.value();

        // 플랫폼 탓으로 실패하면 성공한 단계까지 모두 돌려준다 (계약 8.5).
        // 엔진이 기한 안에 끝을 알리지 않아 우리가 끊은 실행(run 이 비어 있음)도 플랫폼 탓으로 본다.
        if (run == null || run.failure() != null && run.failure().isPlatformFault()) {
            return new ExecutionUsage(BigDecimal.ZERO.setScale(DECIMAL_PLACES), held);
        }

        // 실측이 견적을 넘어도 떼어 둔 것보다 더 받지 않는다. 접수할 때 알린 금액이 상한이다.
        BigDecimal used = measuredUse(run.nodes(), rates).min(held);

        return new ExecutionUsage(used, held.subtract(used));
    }

    public boolean hasRefund() {
        return refund.signum() > 0;
    }

    private static BigDecimal measuredUse(List<RunStatus.Node> nodes, ChargeRates rates) {
        if (nodes == null) {
            return BigDecimal.ZERO.setScale(DECIMAL_PLACES);
        }

        return nodes.stream()
                .filter(RunStatus.Node::isSucceeded)
                .filter(node -> node.costAbc() != null)
                .map(node -> node.costAbc().multiply(rates.of(node.id())))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(DECIMAL_PLACES, RoundingMode.HALF_UP);
    }
}
