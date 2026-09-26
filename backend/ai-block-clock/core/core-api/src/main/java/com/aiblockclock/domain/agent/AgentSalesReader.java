package com.aiblockclock.domain.agent;

import com.aiblockclock.domain.DatePeriod;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.storage.execution.AgentSettlementSum;
import com.aiblockclock.storage.execution.ExecutionSettlementEntity;
import com.aiblockclock.storage.execution.ExecutionSettlementRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AgentSalesReader {
    private static final int DECIMAL_PLACES = 6;

    private final ExecutionSettlementRepository executionSettlementRepository;

    public Map<AgentId, BigDecimal> readRevenues(UserId seller, Collection<AgentId> agents) {
        if (agents.isEmpty()) {
            return Map.of();
        }

        Map<AgentId, BigDecimal> summed = executionSettlementRepository
                .sumBySupplierAndAgents(seller.value(), valuesOf(agents)).stream()
                .collect(Collectors.toMap(sum -> AgentId.of(sum.agentId()), AgentSettlementSum::amount));

        return agents.stream()
                .distinct()
                .collect(Collectors.toMap(Function.identity(),
                        agent -> summed.getOrDefault(agent, BigDecimal.ZERO).setScale(DECIMAL_PLACES)));
    }

    public Map<AgentId, List<Integer>> readDailyExecutions(
            UserId seller, Collection<AgentId> agents, LocalDate firstDay, LocalDate lastDay) {
        if (agents.isEmpty()) {
            return Map.of();
        }

        DatePeriod period = DatePeriod.betweenDates(firstDay, lastDay);
        List<ExecutionSettlementEntity> settlements = executionSettlementRepository.findBySupplierAndAgentsBetween(
                seller.value(), valuesOf(agents), period.startAtInclusive(), period.endAtExclusive());

        Map<AgentId, Map<LocalDate, Set<UUID>>> executions = new HashMap<>();

        for (ExecutionSettlementEntity settlement : settlements) {
            executions.computeIfAbsent(AgentId.of(settlement.getAgentId()), ignored -> new HashMap<>())
                    .computeIfAbsent(LocalDate.ofInstant(settlement.getCreatedAt(), DatePeriod.ZONE),
                            ignored -> new HashSet<>())
                    .add(settlement.getExecutionId());
        }

        return agents.stream()
                .distinct()
                .collect(Collectors.toMap(Function.identity(),
                        agent -> countByDay(executions.getOrDefault(agent, Map.of()), firstDay, lastDay)));
    }

    private static List<Integer> countByDay(Map<LocalDate, Set<UUID>> executions, LocalDate firstDay, LocalDate lastDay) {
        return firstDay.datesUntil(lastDay.plusDays(1))
                .map(day -> executions.getOrDefault(day, Set.of()).size())
                .toList();
    }

    private static List<UUID> valuesOf(Collection<AgentId> agents) {
        return agents.stream()
                .map(AgentId::value)
                .toList();
    }
}
