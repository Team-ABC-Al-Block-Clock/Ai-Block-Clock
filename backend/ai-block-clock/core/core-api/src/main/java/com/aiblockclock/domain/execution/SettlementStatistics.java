package com.aiblockclock.domain.execution;

import com.aiblockclock.domain.DatePeriod;
import com.aiblockclock.domain.agent.AgentId;
import com.aiblockclock.enums.execution.SettlementPeriodUnit;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

// 공급자가 받은 정산을 기간 묶음과 에이전트로 묶은 합계다. 합계 · 기간별 · 에이전트별이 같은 줄들을 나눠 담아 서로 합이 맞는다.
// 호출 수는 정산 줄 수가 아니라 실행 수다. 에이전트 하나가 한 실행에서 단계 여러 개를 돌면 줄이 여러 개 생긴다.
// 에이전트 없이 캔버스에 바로 놓인 노드로 받은 것은 agent 가 빈 묶음 하나로 모인다.
public record SettlementStatistics(
        BigDecimal totalAmount,
        BigDecimal selfUseAmount,
        int executionCount,
        List<PeriodRevenue> periods,
        List<AgentRevenue> agents
) {
    // execution_settlement.amount_abc 컬럼과 같은 자릿수다.
    private static final int DECIMAL_PLACES = 6;

    public SettlementStatistics {
        periods = List.copyOf(periods);
        agents = List.copyOf(agents);
    }

    // periodStart 는 묶음의 첫날이다. 일 단위면 그날, 월 단위면 그달 1일 (UTC).
    public record PeriodRevenue(LocalDate periodStart, BigDecimal amount, int executionCount) {
    }

    public record AgentRevenue(AgentId agent, BigDecimal amount, int executionCount) {
    }

    // 기간은 오래된 것부터, 에이전트는 많이 번 것부터 준다. 받은 것이 없는 묶음은 넣지 않는다.
    public static SettlementStatistics of(List<SettlementRecord> records, SettlementPeriodUnit unit) {
        Tally total = new Tally();
        Tally selfUse = new Tally();
        Map<LocalDate, Tally> byPeriod = new TreeMap<>();
        Map<AgentId, Tally> byAgent = new LinkedHashMap<>();

        for (SettlementRecord record : records) {
            LocalDate settledOn = LocalDate.ofInstant(record.settledAt(), DatePeriod.ZONE);

            total.add(record);
            byPeriod.computeIfAbsent(unit.startOf(settledOn), ignored -> new Tally()).add(record);
            byAgent.computeIfAbsent(record.settlement().agent(), ignored -> new Tally()).add(record);

            if (record.selfUse()) {
                selfUse.add(record);
            }
        }

        List<PeriodRevenue> periods = byPeriod.entrySet().stream()
                .map(entry -> new PeriodRevenue(entry.getKey(), entry.getValue().amount(),
                        entry.getValue().executionCount()))
                .toList();
        List<AgentRevenue> agents = byAgent.entrySet().stream()
                .map(entry -> new AgentRevenue(entry.getKey(), entry.getValue().amount(),
                        entry.getValue().executionCount()))
                .sorted(Comparator.comparing(AgentRevenue::amount).reversed())
                .toList();

        return new SettlementStatistics(total.amount(), selfUse.amount(), total.executionCount(), periods, agents);
    }

    // 한 묶음에 들어온 금액과 실행을 센다. 같은 실행의 줄이 여럿이어도 실행은 한 번만 센다.
    private static final class Tally {
        private BigDecimal amount = BigDecimal.ZERO;
        private final Set<ExecutionId> executions = new HashSet<>();

        private void add(SettlementRecord record) {
            amount = amount.add(record.settlement().amount().value());
            executions.add(record.executionId());
        }

        private BigDecimal amount() {
            return amount.setScale(DECIMAL_PLACES, RoundingMode.UNNECESSARY);
        }

        private int executionCount() {
            return executions.size();
        }
    }
}
