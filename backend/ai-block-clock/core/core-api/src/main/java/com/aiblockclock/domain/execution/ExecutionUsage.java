package com.aiblockclock.domain.execution;

import com.aiblockclock.client.ai.RunStatus;
import com.aiblockclock.domain.AbcAmount;
import com.aiblockclock.domain.user.UserId;

import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

// 끝난 실행에서 쓴 만큼과 돌려줄 만큼, 쓴 만큼을 누구에게 주는지다.
// 떼어 둔 것 = 쓴 만큼 + 돌려줄 만큼이고, 쓴 만큼 = 정산금의 합이다. 닫힌 실행의 예치에는 아무것도 남지 않는다.
// 쓴 만큼은 성공한 단계의 실측 원가(cost_abc)에 접수할 때 정한 배율을 곱해 더한다.
// 실패한 단계와 돌지 않은 단계는 세지 않는다. 결과물을 받지 못한 단계에 값을 받지 않는다.
// 주인을 가리킬 수 없는 단계도 세지 않는다. 받을 사람이 없는 값을 사용자에게 받지 않는다.
@Slf4j
public record ExecutionUsage(BigDecimal used, BigDecimal refund, List<ExecutionSettlement> settlements) {
    private static final int DECIMAL_PLACES = 6;

    public ExecutionUsage {
        settlements = List.copyOf(settlements);
    }

    // failure 는 실패한 실행의 책임 판정이다. 성공한 실행은 비어 있다.
    public static ExecutionUsage of(AbcAmount reserved, RunStatus run, ExecutionFailure failure, ChargeRates rates) {
        BigDecimal held = reserved.value();

        // 플랫폼 탓으로 실패하면 성공한 단계까지 모두 돌려주고 공급자에게도 주지 않는다 (계약 8.5 와 다르다).
        // 계약은 공급자 몫을 플랫폼이 대신 내라고 하지만 플랫폼 계정을 두지 않아 낼 곳이 없다.
        // 엔진이 기한 안에 끝을 알리지 않아 우리가 끊은 실행(run 이 비어 있음)도 플랫폼 탓으로 판정된다.
        if (run == null || failure != null && failure.isPlatformFault()) {
            return new ExecutionUsage(BigDecimal.ZERO.setScale(DECIMAL_PLACES), held, List.of());
        }

        List<Charge> charges = chargesOf(run, rates);
        BigDecimal measured = totalOf(charges).setScale(DECIMAL_PLACES, RoundingMode.HALF_UP);

        // 실측이 견적을 넘어도 떼어 둔 것보다 더 받지 않는다. 접수할 때 알린 금액이 상한이다.
        BigDecimal used = measured.min(held);

        return new ExecutionUsage(used, held.subtract(used), divide(used, charges));
    }

    public boolean hasRefund() {
        return refund.signum() > 0;
    }

    public boolean hasSettlement() {
        return !settlements.isEmpty();
    }

    // 장부에는 주인마다 한 줄로 옮긴다. 한 주인의 단계가 여럿이면 합친다.
    public Map<UserId, AbcAmount> payouts() {
        return settlements.stream()
                .collect(Collectors.toMap(ExecutionSettlement::supplier, ExecutionSettlement::amount,
                        AbcAmount::add, LinkedHashMap::new));
    }

    // 단계 번호가 비었거나 주인을 읽지 못한 성공 단계는 받지도 주지도 않는다. 엔진이 우리가 보낸 값을 잃은 것이다.
    // 그러면 사용자는 돌려받고 판매자 수익은 0 이 되는데 실행은 멀쩡히 닫혀 티가 나지 않으므로 로그로 남긴다.
    private static List<Charge> chargesOf(RunStatus run, ChargeRates rates) {
        if (run.nodes() == null) {
            return List.of();
        }

        List<Charge> charges = new ArrayList<>();

        for (RunStatus.Node node : run.nodes()) {
            if (!node.isSucceeded() || node.costAbc() == null || node.costAbc().signum() <= 0) {
                continue;
            }

            StepOwner owner = StepOwner.of(node);

            if (node.id() == null || !owner.isKnown()) {
                log.warn("주인을 알 수 없는 성공 단계라 받지도 주지도 않는다: run={} step={} owner={}",
                        run.runId(), node.id(), node.ownerId());
                continue;
            }

            charges.add(new Charge(node, owner, node.costAbc().multiply(rates.of(node.id()))));
        }

        return charges;
    }

    // 쓴 만큼을 단계마다 실측 몫에 비례해 나눈다. 상한에 걸리지 않으면 각자 제 몫이고, 걸리면 모두 같은 비율로 줄어든다.
    // 실행 순서로 나누면 뒤에 놓인 공급자만 덜 받는다.
    // 여섯째 자리 아래는 버리고, 버려서 남은 끝자리는 가장 큰 몫에 붙여 합을 쓴 만큼에 맞춘다.
    private static List<ExecutionSettlement> divide(BigDecimal used, List<Charge> charges) {
        if (used.signum() == 0) {
            return List.of();
        }

        BigDecimal measured = totalOf(charges);
        List<BigDecimal> shares = new ArrayList<>(charges.stream()
                .map(charge -> used.multiply(charge.amount()).divide(measured, DECIMAL_PLACES, RoundingMode.DOWN))
                .toList());
        BigDecimal remainder = used.subtract(shares.stream().reduce(BigDecimal.ZERO, BigDecimal::add));
        int largest = IntStream.range(0, charges.size())
                .boxed()
                .max(Comparator.comparing(index -> charges.get(index).amount()))
                .orElseThrow();

        shares.set(largest, shares.get(largest).add(remainder));

        return IntStream.range(0, charges.size())
                .filter(index -> shares.get(index).signum() > 0)
                .mapToObj(index -> charges.get(index).settle(shares.get(index)))
                .toList();
    }

    private static BigDecimal totalOf(List<Charge> charges) {
        return charges.stream()
                .map(Charge::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // 성공한 단계 하나에 매긴 값이다. amount 는 반올림하기 전의 실측 × 배율이다.
    private record Charge(RunStatus.Node step, StepOwner owner, BigDecimal amount) {
        private ExecutionSettlement settle(BigDecimal share) {
            return new ExecutionSettlement(step.id(), step.name(), owner.supplier(), owner.agent(), AbcAmount.of(share));
        }
    }
}
