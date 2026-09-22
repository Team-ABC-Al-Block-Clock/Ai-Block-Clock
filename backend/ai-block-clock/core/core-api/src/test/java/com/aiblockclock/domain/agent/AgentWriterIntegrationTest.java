package com.aiblockclock.domain.agent;

import com.aiblockclock.domain.AbcAmount;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.domain.workflow.GraphFixtures;
import com.aiblockclock.domain.workflow.GraphId;
import com.aiblockclock.domain.workflow.Revision;
import com.aiblockclock.domain.workflow.WorkflowId;
import com.aiblockclock.enums.agent.PublicationState;
import com.aiblockclock.storage.agent.AgentEntity;
import com.aiblockclock.storage.agent.AgentRepository;
import com.aiblockclock.storage.agent.AgentVersionEntity;
import com.aiblockclock.storage.agent.AgentVersionRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@SpringBootTest
@Transactional
class AgentWriterIntegrationTest {
    private static final UserId OWNER = UserId.of(7201L);

    @Autowired
    private AgentWriter agentWriter;

    @Autowired
    private AgentVersionWriter agentVersionWriter;

    @Autowired
    private AgentReader agentReader;

    @Autowired
    private AgentRepository agentRepository;

    @Autowired
    private AgentVersionRepository agentVersionRepository;

    @Test
    @DisplayName("등록과 동시에 공개되므로 공개 버전이 행에 함께 들어간다")
    void appendsPublishedAgentInOneRow() {
        AgentId agentId = AgentId.newId();
        AgentVersionId versionId = AgentVersionId.newId();

        agentWriter.append(Agent.draft(agentId, OWNER, profile()).publish(OWNER, versionId, VersionNumber.first()));
        agentRepository.flush();

        AgentEntity saved = agentRepository.findById(agentId.value()).orElseThrow();
        assertThat(saved.getPublicVersionId()).isEqualTo(versionId.value());
        assertThat(saved.getPublicVersionNo()).isEqualTo(1);
        assertThat(saved.getSummary()).isEqualTo("성적을 분석한다");
    }

    @Test
    @DisplayName("버전은 그래프 주소와 원본 회차를 들고 공개 상태로 저장된다")
    void appendsVersionWithGraphAddress() {
        AgentId agentId = AgentId.newId();
        agentWriter.append(Agent.draft(agentId, OWNER, profile()));

        GraphId graphId = GraphId.newId();
        WorkflowId sourceId = WorkflowId.newId();
        AgentVersion version = AgentVersion.first(AgentVersionId.newId(), agentId,
                new SourceWorkflow(sourceId, Revision.of(3L)),
                GraphFixtures.straightLine(),
                new VersionCost(2, AbcAmount.of(new BigDecimal("100")))).publish();

        agentVersionWriter.append(version, graphId);
        agentVersionRepository.flush();

        AgentVersionEntity saved = agentVersionRepository.findById(version.getId().value()).orElseThrow();
        assertThat(saved.getGraphId()).isEqualTo(graphId.value());
        assertThat(saved.getSourceWorkflowId()).isEqualTo(sourceId.value());
        assertThat(saved.getSourceRevision()).isEqualTo(3L);
        assertThat(saved.getPromptNodeCount()).isEqualTo(2);
        assertThat(saved.getBaseCost()).isEqualByComparingTo("100");
        assertThat(saved.getPublicationState()).isEqualTo(PublicationState.PUBLISHED);
    }

    @Test
    @DisplayName("소유자만 자기 에이전트로 인정된다")
    void readsOwnership() {
        AgentId agentId = AgentId.newId();
        agentWriter.append(Agent.draft(agentId, OWNER, profile()));
        agentRepository.flush();

        assertThat(agentReader.isOwnedBy(agentId, OWNER)).isTrue();
        assertThat(agentReader.isOwnedBy(agentId, UserId.of(7202L))).isFalse();
    }

    private static AgentProfile profile() {
        return new AgentProfile("성적 분석 에이전트", "성적을 분석한다",
                "학생별 점수 표를 받아 이번 기간과 지난 기간을 비교한다",
                "성적표 엑셀을 올리면 분석 리포트가 나옵니다", "무엇을 분석할까요?", null);
    }
}
