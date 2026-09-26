package com.aiblockclock.domain.node;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BaseCostCalculatorTest {
    private final BaseCostCalculator calculator = new BaseCostCalculator(new NodeBaseCostProperties(
            new BigDecimal("0.018"), BigDecimal.ONE, new BigDecimal("1.5"), 5));

    @Test
    void 잰_적이_없으면_기본_원가다() {
        assertThat(calculator.calculate(List.of()).value()).isEqualByComparingTo("0.018");
    }

    // 중앙값 0.004 × 1.5 = 0.006. 긴 입력으로 한 번 돌린 0.05 에 끌려가지 않는다.
    @Test
    void 잰_값이_있으면_중앙값에_여유_배수를_곱한다() {
        List<BigDecimal> measured = List.of(
                new BigDecimal("0.003"), new BigDecimal("0.05"), new BigDecimal("0.004"),
                new BigDecimal("0.0041"), new BigDecimal("0.0039"));

        assertThat(calculator.calculate(measured).value()).isEqualByComparingTo("0.006");
    }

    // 가운데 두 값 0.004 · 0.006 의 평균 0.005 × 1.5 = 0.0075
    @Test
    void 잰_횟수가_짝수면_가운데_두_값의_평균을_쓴다() {
        List<BigDecimal> measured = List.of(
                new BigDecimal("0.006"), new BigDecimal("0.002"), new BigDecimal("0.004"), new BigDecimal("0.009"));

        assertThat(calculator.calculate(measured).value()).isEqualByComparingTo("0.0075");
    }

    @Test
    void 달러를_ABC_로_바꿀_때_환산율을_곱한다() {
        BaseCostCalculator doubled = new BaseCostCalculator(new NodeBaseCostProperties(
                new BigDecimal("0.018"), new BigDecimal("2"), new BigDecimal("1.5"), 5));

        assertThat(doubled.calculate(List.of(new BigDecimal("0.004"))).value()).isEqualByComparingTo("0.012");
    }

    @Test
    void 원가는_장부의_소수_여섯째_자리에서_반올림하고_0_이_되지_않는다() {
        assertThat(calculator.calculate(List.of(new BigDecimal("0.0012345"))).value())
                .isEqualTo(new BigDecimal("0.001852"));
        assertThat(calculator.calculate(List.of(new BigDecimal("0.0000001"))).value())
                .isEqualTo(new BigDecimal("0.000001"));
    }
}
