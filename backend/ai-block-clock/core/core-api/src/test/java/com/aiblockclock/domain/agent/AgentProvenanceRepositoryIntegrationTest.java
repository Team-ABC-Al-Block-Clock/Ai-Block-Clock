package com.aiblockclock.domain.agent;

import com.aiblockclock.enums.agent.AgentIntegrityMismatchReason;
import com.aiblockclock.enums.agent.AgentIntegrityResult;
import com.aiblockclock.enums.agent.AgentProvenanceStatus;
import com.aiblockclock.storage.agent.AgentProvenanceEntity;
import com.aiblockclock.storage.agent.AgentProvenanceRepository;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 대기 목록 순서 · 한 번만 확정 · 문서 이름 유일 · 확정된 기록에만 대조 결과를 DB 가 지키는지 본다. MySQL 에서도 같은지는 MySqlTest 가 상속해 돈다.
@Tag("integration")
@SpringBootTest
@Transactional
class AgentProvenanceRepositoryIntegrationTest {
    private static final Instant T0 = Instant.parse("2026-09-26T00:00:00Z");

    @Autowired
    private AgentProvenanceRepository agentProvenanceRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void 한_번도_보내지_않은_것과_오래전에_손댄_것부터_고른다() {
        UUID older = request(T0);
        UUID newer = request(T0.plusSeconds(10));

        agentProvenanceRepository.markAttempted(older, T0.plusSeconds(20));

        assertThat(agentProvenanceRepository.findPendingIds(PageRequest.of(0, 10)))
                .containsSubsequence(newer, older);
    }

    @Test
    void 확정된_기록은_대기_목록에서_빠지고_두_번째_확정은_아무것도_바꾸지_않는다() {
        UUID versionId = request(T0);

        int first = confirm(versionId, "0x" + "aa".repeat(32));
        int second = confirm(versionId, "0x" + "bb".repeat(32));
        entityManager.clear();

        assertThat(first).isEqualTo(1);
        assertThat(second).isZero();
        assertThat(agentProvenanceRepository.findPendingIds(PageRequest.of(0, 10))).doesNotContain(versionId);
        assertThat(agentProvenanceRepository.findById(versionId)).hasValueSatisfying(row -> {
            assertThat(row.getStatus()).isEqualTo(AgentProvenanceStatus.CONFIRMED);
            assertThat(row.getTransactionHash()).isEqualTo("0x" + "aa".repeat(32));
            assertThat(row.getBlockNumber()).isEqualTo(15230417L);
        });
    }

    @Test
    void 확정된_기록에는_시도_시각을_덧쓰지_않는다() {
        UUID versionId = request(T0);
        confirm(versionId, "0x" + "aa".repeat(32));

        assertThat(agentProvenanceRepository.markAttempted(versionId, T0.plusSeconds(99))).isZero();
    }

    @Test
    void 같은_문서_이름으로_두_기록을_만들_수_없다() {
        UUID agentId = UUID.randomUUID();
        agentProvenanceRepository.saveAndFlush(
                new AgentProvenanceEntity(UUID.randomUUID(), agentId, 1, "same-name-v1", T0));

        assertThatThrownBy(() -> agentProvenanceRepository.saveAndFlush(
                new AgentProvenanceEntity(UUID.randomUUID(), agentId, 1, "same-name-v1", T0)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 에이전트의_첫_버전_기록을_찾는다() {
        UUID agentId = UUID.randomUUID();
        agentProvenanceRepository.saveAndFlush(
                new AgentProvenanceEntity(UUID.randomUUID(), agentId, 2, name(), T0.plusSeconds(60)));
        agentProvenanceRepository.saveAndFlush(new AgentProvenanceEntity(UUID.randomUUID(), agentId, 1, name(), T0));

        assertThat(agentProvenanceRepository.findFirstByAgentIdOrderByVersionNoAsc(agentId))
                .hasValueSatisfying(row -> assertThat(row.getVersionNo()).isEqualTo(1));
    }

    @Test
    void 확정된_기록에_대조_결과를_덮어쓴다() {
        UUID versionId = request(T0);
        confirm(versionId, "0x" + "aa".repeat(32));

        agentProvenanceRepository.recordIntegrity(versionId, AgentIntegrityResult.MISMATCHED,
                AgentIntegrityMismatchReason.METADATA_CHANGED, T0.plusSeconds(60));
        int updated = agentProvenanceRepository.recordIntegrity(versionId, AgentIntegrityResult.MATCHED, null,
                T0.plusSeconds(120));
        entityManager.clear();

        assertThat(updated).isEqualTo(1);
        assertThat(agentProvenanceRepository.findById(versionId)).hasValueSatisfying(row -> {
            assertThat(row.getIntegrityResult()).isEqualTo(AgentIntegrityResult.MATCHED);
            assertThat(row.getIntegrityMismatchReason()).isNull();
            assertThat(row.getIntegrityCheckedAt()).isEqualTo(T0.plusSeconds(120));
        });
    }

    @Test
    void 확정_전_기록에는_대조_결과를_남기지_않는다() {
        UUID versionId = request(T0);

        int updated = agentProvenanceRepository.recordIntegrity(versionId, AgentIntegrityResult.MISMATCHED,
                AgentIntegrityMismatchReason.NOT_ON_CHAIN, T0.plusSeconds(60));
        entityManager.clear();

        assertThat(updated).isZero();
        assertThat(agentProvenanceRepository.findById(versionId))
                .hasValueSatisfying(row -> assertThat(row.getIntegrityCheckedAt()).isNull());
    }

    private UUID request(Instant requestedAt) {
        UUID versionId = UUID.randomUUID();
        agentProvenanceRepository.saveAndFlush(
                new AgentProvenanceEntity(versionId, UUID.randomUUID(), 1, name(), requestedAt));

        return versionId;
    }

    private int confirm(UUID versionId, String transactionHash) {
        return agentProvenanceRepository.confirm(versionId, "0x535abafa3b650ab9fd7b2cd98c7ad06b18d37a3b",
                "0x" + "cc".repeat(32), "abc://agents/x/versions/1?owner=0x535abafa3b650ab9fd7b2cd98c7ad06b18d37a3b",
                "0x41e2e64a79b19e9329f82d3d55be5e767a6f571d", transactionHash, 15230417L, T0.plusSeconds(30));
    }

    private static String name() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 28) + "-v1";
    }
}
