package com.aiblockclock.domain.agent;

import com.aiblockclock.domain.AbcAmount;
import com.aiblockclock.domain.workflow.GraphFixtures;
import com.aiblockclock.domain.workflow.WorkflowGraph;
import com.aiblockclock.domain.workflow.Revision;
import com.aiblockclock.domain.workflow.WorkflowId;
import com.aiblockclock.enums.agent.Tier;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

class AgentVersionTest {
    private static final AgentId AGENT_ID = AgentId.newId();

    private static AgentVersion firstVersion() {
        return AgentVersion.first(AgentVersionId.newId(), AGENT_ID, source(1),
                GraphFixtures.straightLine(), cost(2, "100"));
    }

    private static SourceWorkflow source(long revision) {
        return new SourceWorkflow(WorkflowId.newId(), Revision.of(revision));
    }

    private static VersionCost cost(int promptNodeCount, String baseCost) {
        return new VersionCost(promptNodeCount, AbcAmount.of(new BigDecimal(baseCost)));
    }

    @Test
    @DisplayName("첫 버전은 1번이고, 최하 등급에 굳은 원가로 시작한다")
    void firstVersionStartsAtTierFour() {
        AgentVersion version = firstVersion();

        assertThat(version.getNumber()).isEqualTo(VersionNumber.first());
        assertThat(version.getTier()).isEqualTo(Tier.TIER_4);
        assertThat(version.getCost().promptNodeCount()).isEqualTo(2);
        assertThat(version.isPublished()).isFalse();
    }

    @Test
    @DisplayName("다음 버전은 직전 버전에서 번호를 이어받는다")
    void nextVersionContinuesNumbering() {
        AgentVersion previous = firstVersion();

        AgentVersion next = AgentVersion.next(previous, AgentVersionId.newId(), source(2),
                GraphFixtures.straightLine(), cost(3, "150"));

        assertThat(next.getNumber()).isEqualTo(VersionNumber.of(2));
        assertThat(next.getAgentId()).isEqualTo(previous.getAgentId());
    }

    @Test
    @DisplayName("빈 그래프로는 실행 정의를 만들 수 없다")
    void rejectsEmptySnapshot() {
        assertThatThrownBy(() -> AgentVersion.first(AgentVersionId.newId(), AGENT_ID, source(1),
                WorkflowGraph.empty(), cost(0, "1")))
                .isInstanceOf(CoreException.class)
                .extracting(e -> ((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.AGENT_WORKFLOW_EMPTY);
    }

    @Test
    @DisplayName("공개한 뒤에는 등급을 손댈 수 없다")
    void publishedVersionIsFrozen() {
        AgentVersion version = firstVersion();
        version.publish();

        assertThatThrownBy(() -> version.changeTier(Tier.TIER_1))
                .isInstanceOf(CoreException.class);
        assertThatThrownBy(version::publish)
                .isInstanceOf(CoreException.class);
    }

    @Test
    @DisplayName("원본 워크플로우가 바뀌어도 스냅샷은 그대로다")
    void snapshotIsDetachedFromSource() {
        WorkflowGraph original = GraphFixtures.straightLine();
        AgentVersion version = AgentVersion.first(AgentVersionId.newId(), AGENT_ID, source(1),
                original, cost(2, "100"));

        assertThat(version.getSnapshot()).isSameAs(original);
    }

    @Test
    @DisplayName("단가는 등급의 프리미엄을 얹어 계산해서 내보낸다")
    void finalPriceAppliesPremium() {
        AgentVersion version = firstVersion();

        assertThat(version.finalPrice().value()).isEqualByComparingTo("100.000000");

        version.changeTier(Tier.TIER_1);
        assertThat(version.finalPrice().value()).isEqualByComparingTo("150.000000");
    }
}
