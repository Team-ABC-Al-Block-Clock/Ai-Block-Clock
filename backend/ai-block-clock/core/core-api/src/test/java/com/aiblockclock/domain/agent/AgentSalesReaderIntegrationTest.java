package com.aiblockclock.domain.agent;

import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.enums.execution.ExecutionStatus;
import com.aiblockclock.storage.execution.ExecutionEntity;
import com.aiblockclock.storage.execution.ExecutionRepository;
import com.aiblockclock.storage.execution.ExecutionSettlementEntity;
import com.aiblockclock.storage.execution.ExecutionSettlementRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@SpringBootTest
@Transactional
class AgentSalesReaderIntegrationTest {
    private static final UserId SELLER = UserId.of(9601L);
    private static final UserId OTHER_SELLER = UserId.of(9602L);
    private static final long BUYER = 9603L;
    private static final AgentId RESEARCH = AgentId.newId();
    private static final AgentId WRITER = AgentId.newId();

    @Autowired
    private AgentSalesReader agentSalesReader;

    @Autowired
    private ExecutionRepository executionRepository;

    @Autowired
    private ExecutionSettlementRepository executionSettlementRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("받은 정산금은 이 판매자가 그 에이전트로 받은 줄만 더한다")
    void sumsOnlyWhatTheSellerReceivedForTheAgent() {
        UUID first = executionBy(BUYER);
        UUID second = executionBy(SELLER.value());
        settle(first, "n1", SELLER, RESEARCH, "1.5");
        settle(first, "n2", SELLER, RESEARCH, "0.5");
        settle(second, "n1", SELLER, RESEARCH, "0.25");
        settle(first, "n3", OTHER_SELLER, RESEARCH, "9");
        settle(first, "n4", SELLER, null, "4");

        Map<AgentId, BigDecimal> revenues = agentSalesReader.readRevenues(SELLER, List.of(RESEARCH, WRITER));

        assertThat(revenues.get(RESEARCH)).isEqualTo(new BigDecimal("2.250000"));
        assertThat(revenues.get(WRITER)).isEqualTo(new BigDecimal("0.000000"));
    }

    @Test
    @DisplayName("날마다 정산을 남긴 실행을 한 번씩 세고, 기간 밖과 남이 받은 줄은 세지 않는다")
    void countsExecutionsPerDay() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        UUID twoSteps = executionBy(BUYER);
        UUID threeDaysAgo = executionBy(BUYER);
        UUID aWeekAgo = executionBy(BUYER);
        UUID othersSale = executionBy(BUYER);
        settle(twoSteps, "n1", SELLER, RESEARCH, "1");
        settle(twoSteps, "n2", SELLER, RESEARCH, "1");
        settle(threeDaysAgo, "n1", SELLER, RESEARCH, "1");
        settle(aWeekAgo, "n1", SELLER, RESEARCH, "1");
        settle(othersSale, "n1", OTHER_SELLER, RESEARCH, "1");
        settleAt(threeDaysAgo, today.minusDays(3));
        settleAt(aWeekAgo, today.minusDays(7));

        Map<AgentId, List<Integer>> trends = agentSalesReader.readDailyExecutions(
                SELLER, List.of(RESEARCH, WRITER), today.minusDays(6), today);

        assertThat(trends.get(RESEARCH)).containsExactly(0, 0, 0, 1, 0, 0, 1);
        assertThat(trends.get(WRITER)).containsExactly(0, 0, 0, 0, 0, 0, 0);
    }

    private UUID executionBy(long requester) {
        UUID id = UUID.randomUUID();
        executionRepository.save(new ExecutionEntity(id, UUID.randomUUID(), UUID.randomUUID(), requester, "run-" + id,
                ExecutionStatus.SUCCEEDED, 1, null, null, null, false, new BigDecimal("10")));

        return id;
    }

    private void settle(UUID execution, String stepId, UserId supplier, AgentId agent, String amount) {
        executionSettlementRepository.saveAndFlush(new ExecutionSettlementEntity(
                execution, stepId, "노드", supplier.value(), agent == null ? null : agent.value(),
                new BigDecimal(amount)));
    }

    private void settleAt(UUID execution, LocalDate day) {
        entityManager.createNativeQuery("update execution_settlement set created_at = ?1 where execution_id = ?2")
                .setParameter(1, day.atTime(LocalTime.NOON).toInstant(ZoneOffset.UTC))
                .setParameter(2, execution.toString())
                .executeUpdate();
        entityManager.clear();
    }
}
