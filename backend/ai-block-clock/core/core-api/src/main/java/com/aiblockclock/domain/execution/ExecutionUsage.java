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

// 끝난 실행에서 쓴 만큼과 돌려줄 만큼, 성공한 단계의 주인에게 줄 정산금이다.
// 떼어 둔 것 = 쓴 만큼 + 돌려줄 만큼이다. 닫힌 실행의 예치에는 아무것도 남지 않는다.
// 정산금은 보통 사용자가 쓴 만큼에서 나오고(쓴 만큼 = 정산금의 합), 플랫폼 탓이면 플랫폼이 대신 낸다(coveredByPlatform).
// 쓴 만큼은 성공한 단계마다 접수할 때 정한 단가(원가 × 배율)의 합이다. 엔진은 돈을 모르고 금액은 우리 단가에서 나온다
// (계약 v2.0 8.1 · 10장). 엔진이 잰 달러 원가는 여기서 쓰지 않고, 판매자 테스트 실행으로 원가를 새로 잴 때만 쓴다.
// 실패한 단계와 돌지 않은 단계는 받지 않는다. 결과물을 받지 못한 단계에 값을 받지 않는다.
// 주인을 가리킬 수 없는 단계와 단가를 남기지 않은 단계도 받지 않는다. 받을 사람이나 받을 값이 없다.
@Slf4j
public record ExecutionUsage(BigDecimal used, BigDecimal refund, List<ExecutionSettlement> settlements,
                             boolean coveredByPlatform) {
    private static final int DECIMAL_PLACES = 6;

    public ExecutionUsage {
        settlements = List.copyOf(settlements);
    }

    // failure 는 실패한 실행의 책임 판정이다. 성공한 실행은 비어 있다. mock 은 접수할 때 적은 값이다.
    public static ExecutionUsage of(AbcAmount reserved, RunStatus run, ExecutionFailure failure, StepPrices prices,
                                    boolean mock) {
        BigDecimal held = reserved.value();

        // 엔진이 기한 안에 끝을 알리지 않아 우리가 끊은 실행(run 이 비어 있음)은 어느 단계가 성공했는지 모른다.
        // mock 은 모델을 부르지 않아 결과물이 가짜다. 접수할 때 적은 값으로 보고, 엔진도 mock 이라 알리면 함께 믿는다.
        // 둘 다 받을 것도 줄 것도 없어 모두 돌려준다.
        if (run == null || mock || run.isMock()) {
            return nothingUsed(held);
        }

        List<Charge> charges = chargesOf(run, prices);
        BigDecimal priced = totalOf(charges);

        // 플랫폼 탓으로 실패하면 사용자에게 성공한 단계까지 모두 돌려주되, 그 단계의 주인은 제 단가를 받는다.
        // 플랫폼 잘못으로 공급자가 손해 보지 않게 하는 계약 8.5 의 규칙이고, 공급자 몫은 플랫폼이 대신 낸다.
        if (failure != null && failure.isPlatformFault()) {
            return new ExecutionUsage(zero(), held, divide(priced, charges), true);
        }

        // 단가의 합은 접수할 때 알린 견적(떼어 둔 것)을 넘지 않는다. 에이전트 판매가를 한 번에 반올림한 견적과
        // 단계마다 반올림한 단가가 끝자리에서 어긋나 넘더라도, 떼어 둔 것보다 더 받지 않는다.
        BigDecimal used = priced.min(held);

        return new ExecutionUsage(used, held.subtract(used), divide(used, charges), false);
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

    private static ExecutionUsage nothingUsed(BigDecimal held) {
        return new ExecutionUsage(zero(), held, List.of(), false);
    }

    private static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(DECIMAL_PLACES);
    }

    // 단계 번호가 비었거나 주인을 읽지 못한 성공 단계는 받지도 주지도 않는다. 엔진이 우리가 보낸 값을 잃은 것이다.
    // 그러면 사용자는 돌려받고 판매자 수익은 0 이 되는데 실행은 멀쩡히 닫혀 티가 나지 않으므로 로그로 남긴다.
    // 단가를 남기기 전에 접수한 실행의 단계는 받을 값이 없어 받지 않는다.
    private static List<Charge> chargesOf(RunStatus run, StepPrices prices) {
        if (run.nodes() == null) {
            return List.of();
        }

        List<Charge> charges = new ArrayList<>();

        for (RunStatus.Node node : run.nodes()) {
            if (!node.isSucceeded()) {
                continue;
            }

            StepOwner owner = StepOwner.of(node);

            if (node.id() == null || !owner.isKnown()) {
                log.warn("주인을 알 수 없는 성공 단계라 받지도 주지도 않는다: run={} step={} owner={}",
                        run.runId(), node.id(), node.ownerId());
                continue;
            }

            prices.of(node.id())
                    .ifPresent(price -> charges.add(new Charge(node, owner, price.price().value())));
        }

        return charges;
    }

    // 줄 만큼을 단계마다 단가에 비례해 나눈다. 상한에 걸리지 않으면 각자 제 단가이고, 걸리면 모두 같은 비율로 줄어든다.
    // 실행 순서로 나누면 뒤에 놓인 공급자만 덜 받는다.
    // 여섯째 자리 아래는 버리고, 버려서 남은 끝자리는 가장 큰 몫에 붙여 합을 쓴 만큼에 맞춘다.
    private static List<ExecutionSettlement> divide(BigDecimal paid, List<Charge> charges) {
        if (paid.signum() == 0) {
            return List.of();
        }

        BigDecimal priced = totalOf(charges);
        List<BigDecimal> shares = new ArrayList<>(charges.stream()
                .map(charge -> paid.multiply(charge.amount()).divide(priced, DECIMAL_PLACES, RoundingMode.DOWN))
                .toList());
        BigDecimal remainder = paid.subtract(shares.stream().reduce(BigDecimal.ZERO, BigDecimal::add));
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
                .reduce(BigDecimal.ZERO.setScale(DECIMAL_PLACES), BigDecimal::add);
    }

    // 성공한 단계 하나에 매긴 값이다. amount 는 접수할 때 정한 그 단계의 단가다.
    private record Charge(RunStatus.Node step, StepOwner owner, BigDecimal amount) {
        private ExecutionSettlement settle(BigDecimal share) {
            return new ExecutionSettlement(step.id(), step.name(), owner.supplier(), owner.agent(), AbcAmount.of(share));
        }
    }
}
