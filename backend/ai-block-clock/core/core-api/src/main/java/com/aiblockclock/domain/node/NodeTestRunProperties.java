package com.aiblockclock.domain.node;

import jakarta.validation.constraints.Min;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

// 판매자 테스트 실행의 한도다. 테스트는 무료라 모델 요금을 플랫폼이 내므로 노드마다 하루(UTC) 횟수를 막는다.
// 엔진은 테스트 횟수를 제한하지 않고 백엔드가 접수에서 건다 (엔진 계약 v2.0 7.1).
@Validated
@ConfigurationProperties(prefix = "node.test-run")
public record NodeTestRunProperties(
        @Min(1) int dailyLimit
) {
}
