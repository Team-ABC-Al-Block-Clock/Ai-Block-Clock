package com.aiblockclock.domain.node;

import com.aiblockclock.domain.AbcAmount;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

// 노드 원가(Base Cost, NOD-007)를 정한다. 판매자가 테스트 실행으로 잰 달러 원가가 있으면
// 최근 것들의 중앙값 × 여유 배수 × 환산율이고, 잰 적이 없으면 기본값이다.
// 최댓값이 아니라 중앙값인 까닭은, 판매가 전액이 판매자 몫이라 원가를 부풀리면 판매자 수입이 오르기 때문이다.
// 긴 입력으로 한 번만 돌려 원가를 끌어올리거나, 아주 짧은 입력으로 한 번만 돌려 끌어내리는 것에 흔들리지 않는다.
@Component
@RequiredArgsConstructor
public class BaseCostCalculator {
    // ledger_account.balance 컬럼과 같은 자릿수다. 잰 값이 아주 작아도 원가는 0 이 될 수 없어 이 값 아래로 내리지 않는다.
    private static final int DECIMAL_PLACES = 6;
    private static final BigDecimal SMALLEST = BigDecimal.ONE.movePointLeft(DECIMAL_PLACES);

    private final NodeBaseCostProperties properties;

    public AbcAmount calculate(List<BigDecimal> measuredUsd) {
        if (measuredUsd.isEmpty()) {
            return AbcAmount.of(properties.defaultCost());
        }

        BigDecimal cost = medianOf(measuredUsd)
                .multiply(properties.margin())
                .multiply(properties.abcPerUsd())
                .setScale(DECIMAL_PLACES, RoundingMode.HALF_UP);

        return AbcAmount.of(cost.max(SMALLEST));
    }

    public int sampleSize() {
        return properties.sampleSize();
    }

    private static BigDecimal medianOf(List<BigDecimal> values) {
        List<BigDecimal> sorted = values.stream().sorted().toList();
        int middle = sorted.size() / 2;

        if (sorted.size() % 2 == 1) {
            return sorted.get(middle);
        }

        return sorted.get(middle - 1).add(sorted.get(middle)).divide(BigDecimal.TWO);
    }
}
