package com.aiblockclock.domain.execution;

import com.aiblockclock.domain.AbcAmount;
import com.aiblockclock.domain.agent.AgentId;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.enums.execution.SettlementPeriodUnit;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class SettlementStatisticsTest {
    private static final UserId SUPPLIER = UserId.of(7L);
    private static final AgentId RESEARCH = AgentId.newId();
    private static final AgentId WRITER = AgentId.newId();

    @Test
    void 합계와_기간별_에이전트별_합이_서로_같다() {
        ExecutionId first = ExecutionId.newId();
        ExecutionId second = ExecutionId.newId();
        List<SettlementRecord> records = List.of(
                record(first, RESEARCH, "1.5", "2026-09-24T10:00:00Z", false),
                record(second, WRITER, "0.25", "2026-09-25T09:00:00Z", false),
                record(second, RESEARCH, "0.75", "2026-09-25T09:00:00Z", false));

        SettlementStatistics statistics = SettlementStatistics.of(records, SettlementPeriodUnit.DAY);

        assertThat(statistics.totalAmount()).isEqualTo("2.500000");
        assertThat(statistics.executionCount()).isEqualTo(2);
        assertThat(statistics.periods().stream().map(SettlementStatistics.PeriodRevenue::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo(statistics.totalAmount());
        assertThat(statistics.agents().stream().map(SettlementStatistics.AgentRevenue::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo(statistics.totalAmount());
    }

    @Test
    void 기간은_오래된_것부터_날짜마다_묶는다() {
        List<SettlementRecord> records = List.of(
                record(ExecutionId.newId(), RESEARCH, "0.25", "2026-09-25T09:00:00Z", false),
                record(ExecutionId.newId(), RESEARCH, "1.5", "2026-09-24T10:00:00Z", false),
                record(ExecutionId.newId(), RESEARCH, "0.5", "2026-09-24T23:59:59Z", false));

        SettlementStatistics statistics = SettlementStatistics.of(records, SettlementPeriodUnit.DAY);

        assertThat(statistics.periods())
                .extracting(SettlementStatistics.PeriodRevenue::periodStart,
                        SettlementStatistics.PeriodRevenue::amount,
                        SettlementStatistics.PeriodRevenue::executionCount)
                .containsExactly(
                        tuple(LocalDate.of(2026, 9, 24), new BigDecimal("2.000000"), 2),
                        tuple(LocalDate.of(2026, 9, 25), new BigDecimal("0.250000"), 1));
    }

    // 시각은 UTC 로 기록된다. 한국 시각으로는 10월 1일 새벽이어도 UTC 로 9월 30일이면 9월이다.
    @Test
    void 월_단위면_UTC_기준으로_그달_1일에_묶는다() {
        List<SettlementRecord> records = List.of(
                record(ExecutionId.newId(), RESEARCH, "1", "2026-09-30T23:30:00Z", false),
                record(ExecutionId.newId(), RESEARCH, "2", "2026-10-01T00:10:00Z", false));

        SettlementStatistics statistics = SettlementStatistics.of(records, SettlementPeriodUnit.MONTH);

        assertThat(statistics.periods())
                .extracting(SettlementStatistics.PeriodRevenue::periodStart,
                        SettlementStatistics.PeriodRevenue::amount)
                .containsExactly(
                        tuple(LocalDate.of(2026, 9, 1), new BigDecimal("1.000000")),
                        tuple(LocalDate.of(2026, 10, 1), new BigDecimal("2.000000")));
    }

    @Test
    void 한_실행에서_같은_에이전트의_단계가_여럿이어도_호출은_한_번이다() {
        ExecutionId execution = ExecutionId.newId();
        List<SettlementRecord> records = List.of(
                record(execution, RESEARCH, "0.5", "2026-09-25T09:00:00Z", false),
                record(execution, RESEARCH, "0.5", "2026-09-25T09:00:00Z", false),
                record(ExecutionId.newId(), RESEARCH, "0.5", "2026-09-25T10:00:00Z", false));

        SettlementStatistics statistics = SettlementStatistics.of(records, SettlementPeriodUnit.DAY);

        assertThat(statistics.agents())
                .extracting(SettlementStatistics.AgentRevenue::agent,
                        SettlementStatistics.AgentRevenue::amount,
                        SettlementStatistics.AgentRevenue::executionCount)
                .containsExactly(tuple(RESEARCH, new BigDecimal("1.500000"), 2));
        assertThat(statistics.executionCount()).isEqualTo(2);
    }

    @Test
    void 에이전트는_많이_번_것부터_주고_바로_놓인_노드는_빈_에이전트_한_묶음이다() {
        List<SettlementRecord> records = List.of(
                record(ExecutionId.newId(), RESEARCH, "0.5", "2026-09-25T09:00:00Z", false),
                record(ExecutionId.newId(), null, "0.3", "2026-09-25T09:00:00Z", false),
                record(ExecutionId.newId(), WRITER, "2", "2026-09-25T09:00:00Z", false),
                record(ExecutionId.newId(), null, "0.3", "2026-09-25T09:00:00Z", false));

        SettlementStatistics statistics = SettlementStatistics.of(records, SettlementPeriodUnit.DAY);

        assertThat(statistics.agents())
                .extracting(SettlementStatistics.AgentRevenue::agent, SettlementStatistics.AgentRevenue::amount)
                .containsExactly(
                        tuple(WRITER, new BigDecimal("2.000000")),
                        tuple(null, new BigDecimal("0.600000")),
                        tuple(RESEARCH, new BigDecimal("0.500000")));
    }

    @Test
    void 자기_실행으로_받은_금액은_합계에_들어가고_따로도_센다() {
        List<SettlementRecord> records = List.of(
                record(ExecutionId.newId(), RESEARCH, "1", "2026-09-25T09:00:00Z", true),
                record(ExecutionId.newId(), RESEARCH, "2", "2026-09-25T09:00:00Z", false));

        SettlementStatistics statistics = SettlementStatistics.of(records, SettlementPeriodUnit.DAY);

        assertThat(statistics.totalAmount()).isEqualTo("3.000000");
        assertThat(statistics.selfUseAmount()).isEqualTo("1.000000");
    }

    @Test
    void 받은_것이_없으면_합계는_0이고_목록은_비어_있다() {
        SettlementStatistics statistics = SettlementStatistics.of(List.of(), SettlementPeriodUnit.MONTH);

        assertThat(statistics.totalAmount()).isEqualTo("0.000000");
        assertThat(statistics.selfUseAmount()).isEqualTo("0.000000");
        assertThat(statistics.executionCount()).isZero();
        assertThat(statistics.periods()).isEmpty();
        assertThat(statistics.agents()).isEmpty();
    }

    private static SettlementRecord record(
            ExecutionId execution, AgentId agent, String amount, String settledAt, boolean selfUse) {
        ExecutionSettlement settlement = new ExecutionSettlement(
                "n1", "노드", SUPPLIER, agent, AbcAmount.of(new BigDecimal(amount)));

        return new SettlementRecord(execution, settlement, selfUse, Instant.parse(settledAt));
    }
}
