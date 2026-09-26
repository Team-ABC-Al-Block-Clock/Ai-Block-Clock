package com.aiblockclock.domain.node;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

// 노드 원가(Base Cost, NOD-007)를 정하는 조정값이다. 엔진은 달러 원가까지만 주고 ABC 원가는 우리가 정한다 (엔진 계약 v2.0 8.1).
// 실측이 쌓이며 바뀔 값이라 코드가 아니라 설정에 두고, 범위를 벗어나면 기동할 때 막는다.
//   defaultCost  잰 적이 없는 노드의 원가(ABC). 장부와 같은 소수 여섯째 자리까지다
//   abcPerUsd    1 달러가 몇 ABC 인가
//   margin       실측 달러 원가에 얹는 여유 배수. 1 보다 작으면 실측보다 싸게 팔게 된다
//   sampleSize   원가를 정할 때 보는 최근 테스트 실행 수
@Validated
@ConfigurationProperties(prefix = "node.base-cost")
public record NodeBaseCostProperties(
        @NotNull @Positive @Digits(integer = 35, fraction = 6) BigDecimal defaultCost,
        @NotNull @Positive BigDecimal abcPerUsd,
        @NotNull @DecimalMin("1") BigDecimal margin,
        @Min(1) int sampleSize
) {
}
