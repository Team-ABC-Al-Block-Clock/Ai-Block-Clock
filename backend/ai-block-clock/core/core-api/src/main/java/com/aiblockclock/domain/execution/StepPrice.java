package com.aiblockclock.domain.execution;

import com.aiblockclock.domain.AbcAmount;

import java.math.BigDecimal;

// 단계 하나의 배율과 단가다. 단가 = 원가 × 배율이고, 배율은 원가만 받는 자리(내 노드 · 내 에이전트)면 1 이다.
public record StepPrice(BigDecimal multiplier, AbcAmount price) {
}
