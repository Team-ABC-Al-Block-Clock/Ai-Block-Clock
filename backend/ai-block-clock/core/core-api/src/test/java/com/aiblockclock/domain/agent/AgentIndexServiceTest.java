package com.aiblockclock.domain.agent;

import com.aiblockclock.client.ai.IndexOutcome;
import com.aiblockclock.client.ai.IndexedAgent;
import com.aiblockclock.client.ai.RecommendClient;
import com.aiblockclock.client.ai.AiClientError;
import com.aiblockclock.client.ai.AiClientException;
import com.aiblockclock.domain.AbcAmount;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.domain.workflow.GraphFixtures;
import com.aiblockclock.domain.workflow.Revision;
import com.aiblockclock.domain.workflow.WorkflowId;
import com.aiblockclock.enums.agent.Tier;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;

class AgentIndexServiceTest {
    private static final UserId OWNER = UserId.of(9301L);

    private final RecommendClient recommendClient = mock(RecommendClient.class);
    private final AgentReader agentReader = mock(AgentReader.class);
    private final AgentIndexService indexer = new AgentIndexService(recommendClient, agentReader);

    @Test
    @DisplayName("등록한 에이전트는 마켓에 공개되는 값만 색인에 실어 보낸다")
    void sendsOnlyPublicMetadata() {
        given(recommendClient.upsert(anyList())).willReturn(new IndexOutcome("upsert", 1, 1, "v1"));

        indexer.index(registration());

        assertThat(sentRecord())
                .extracting(IndexedAgent::name, IndexedAgent::description,
                        IndexedAgent::tier, IndexedAgent::isPublic)
                .containsExactly("성적 분석 에이전트", "성적을 분석한다", Tier.initial().level(), true);
    }

    @Test
    @DisplayName("프롬프트 원문과 내부 구성은 색인에 담기지 않는다")
    void neverSendsPromptOrGraph() {
        given(recommendClient.upsert(anyList())).willReturn(new IndexOutcome("upsert", 1, 1, "v1"));

        indexer.index(registration());

        assertThat(sentRecord().toString())
                .doesNotContain("프롬프트")
                .doesNotContain("n1");
    }

    @Test
    @DisplayName("색인에 넣지 못해도 등록한 쪽으로 예외가 나가지 않는다")
    void swallowsIndexFailure() {
        willThrow(new AiClientException(AiClientError.REQUEST_FAILED, "engine is down"))
                .given(recommendClient).upsert(anyList());

        assertThatCode(() -> indexer.index(registration())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("전체 다시 넣기는 공개된 에이전트를 모두 실어 보낸다")
    void rebuildsWithEveryPublicAgent() {
        given(agentReader.readPublic()).willReturn(List.of(
                publicAgent("성적 분석 에이전트"), publicAgent("가정통신문 작성기")));
        given(recommendClient.rebuild(anyList())).willReturn(new IndexOutcome("rebuild", 2, 2, "v1"));

        indexer.rebuild();

        ArgumentCaptor<List<IndexedAgent>> sent = ArgumentCaptor.captor();
        then(recommendClient).should().rebuild(sent.capture());
        assertThat(sent.getValue()).extracting(IndexedAgent::name)
                .containsExactly("성적 분석 에이전트", "가정통신문 작성기");
    }

    @Test
    @DisplayName("기동 시 다시 넣기는 무엇이 터져도 기동을 막지 않는다")
    void startupRebuildNeverBlocksBoot() {
        willThrow(new IllegalStateException("db is down")).given(agentReader).readPublic();

        assertThatCode(indexer::rebuildOnStartup).doesNotThrowAnyException();
    }

    private IndexedAgent sentRecord() {
        ArgumentCaptor<List<IndexedAgent>> sent = ArgumentCaptor.captor();
        then(recommendClient).should().upsert(sent.capture());

        return sent.getValue().get(0);
    }

    private static AgentRegistration registration() {
        AgentId agentId = AgentId.newId();
        AgentVersion version = AgentVersion.first(
                AgentVersionId.newId(),
                agentId,
                new SourceWorkflow(WorkflowId.newId(), Revision.initial()),
                GraphFixtures.straightLine(),
                new VersionCost(2, AbcAmount.of(new BigDecimal("6")))).publish();
        Agent agent = Agent.draft(agentId, OWNER, profile())
                .publish(OWNER, version.getId(), version.getNumber());

        return new AgentRegistration(agent, version);
    }

    private static PublicAgent publicAgent(String name) {
        return new PublicAgent(AgentId.newId(), OWNER, name, "한 줄 소개",
                Tier.initial(), AbcAmount.of(new BigDecimal("6")));
    }

    private static AgentProfile profile() {
        return new AgentProfile("성적 분석 에이전트", "성적을 분석한다",
                "학생별 점수 표를 받아 이번 기간과 지난 기간을 비교한다", null, null, null);
    }
}
