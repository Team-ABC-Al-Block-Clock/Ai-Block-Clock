package com.aiblockclock.domain.agent;

import com.aiblockclock.enums.agent.MyAgentSort;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

public record MyAgent(
        SellingAgent selling, BigDecimal revenue, long executionCount, boolean favorited, List<Integer> trend) {
    private static final Comparator<MyAgent> LATEST = Comparator
            .comparing((MyAgent agent) -> agent.selling().createdAt())
            .thenComparing(agent -> agent.selling().agent().id().value().toString())
            .reversed();

    public MyAgent {
        trend = List.copyOf(trend);
    }

    public static Comparator<MyAgent> orderOf(MyAgentSort sort) {
        return switch (sort) {
            case LATEST -> LATEST;
            case EXECUTIONS -> Comparator.comparingLong(MyAgent::executionCount).reversed().thenComparing(LATEST);
            case REVENUE -> Comparator.comparing(MyAgent::revenue).reversed().thenComparing(LATEST);
        };
    }
}
