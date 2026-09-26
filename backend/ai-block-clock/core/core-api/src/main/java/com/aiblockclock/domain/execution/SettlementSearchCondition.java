package com.aiblockclock.domain.execution;

import com.aiblockclock.domain.DatePeriod;
import com.aiblockclock.domain.agent.AgentId;

import java.time.LocalDate;
import java.util.UUID;

// 무엇을 볼지(기간 · 에이전트)만 담는다. 누구의 것인지는 로그인 사용자로 정해져 여기에 두지 않는다.
// agent 가 비어 있으면 에이전트를 가리지 않는다.
public record SettlementSearchCondition(DatePeriod period, AgentId agent) {

    public static SettlementSearchCondition of(LocalDate startDate, LocalDate endDate, UUID agentId) {
        return new SettlementSearchCondition(DatePeriod.of(startDate, endDate),
                agentId == null ? null : AgentId.of(agentId));
    }
}
