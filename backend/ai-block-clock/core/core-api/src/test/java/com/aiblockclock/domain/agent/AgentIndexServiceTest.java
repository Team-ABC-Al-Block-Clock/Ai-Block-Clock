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
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;

class AgentIndexServiceTest {
    private static final UserId OWNER = UserId.of(9301L);
    // AI grading-formula.md v1.0 의 초기값이다. 성공률은 성공 / (성공 + 공급자 탓 실패) 라 조정값과 상관없다.
    private static final AgentGradeProperties FORMULA = new AgentGradeProperties(
            10, 3.5, 1000, 2, new BigDecimal("0.6"), new BigDecimal("0.4"), new AgentTierLadder(
                    new AgentTierRequirement(new BigDecimal("80"), 0.98, 50),
                    new AgentTierRequirement(new BigDecimal("65"), 0.95, 20),
                    new AgentTierRequirement(new BigDecimal("45"), 0.90, 0)));

    private final RecommendClient recommendClient = mock(RecommendClient.class);
    private final AgentReader agentReader = mock(AgentReader.class);
    private final AgentGradeMetricReader agentGradeMetricReader = mock(AgentGradeMetricReader.class);
    private final AgentIndexService indexer =
            new AgentIndexService(recommendClient, agentReader, agentGradeMetricReader, FORMULA);

    @Test
    @DisplayName("등록한 에이전트는 마켓에 공개되는 값만 색인에 실어 보낸다")
    void sendsOnlyPublicMetadata() {
        givenFreshMetric();
        given(recommendClient.upsert(anyList())).willReturn(new IndexOutcome("upsert", 1, 1, "v1"));

        indexer.index(registration());

        assertThat(sentRecord())
                .extracting(IndexedAgent::name, IndexedAgent::description,
                        IndexedAgent::tier, IndexedAgent::successRate, IndexedAgent::isPublic)
                .containsExactly("성적 분석 에이전트", "성적을 분석한다", Tier.initial().level(), 1.0, true);
    }

    @Test
    @DisplayName("프롬프트 원문과 내부 구성은 색인에 담기지 않는다")
    void neverSendsPromptOrGraph() {
        givenFreshMetric();
        given(recommendClient.upsert(anyList())).willReturn(new IndexOutcome("upsert", 1, 1, "v1"));

        indexer.index(registration());

        assertThat(sentRecord().toString())
                .doesNotContain("프롬프트")
                .doesNotContain("n1");
    }

    @Test
    @DisplayName("색인에 넣지 못해도 등록한 쪽으로 예외가 나가지 않는다")
    void swallowsIndexFailure() {
        givenFreshMetric();
        willThrow(new AiClientException(AiClientError.REQUEST_FAILED, "engine is down"))
                .given(recommendClient).upsert(anyList());

        assertThatCode(() -> indexer.index(registration())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("전체 다시 넣기는 공개된 에이전트를 모두 실어 보낸다")
    void rebuildsWithEveryPublicAgent() {
        List<PublicAgent> agents = List.of(publicAgent("성적 분석 에이전트"), publicAgent("가정통신문 작성기"));
        given(agentReader.readPublic()).willReturn(agents);
        given(agentGradeMetricReader.readAll(anyList())).willReturn(Map.of(
                agents.get(0).id(), AgentGradeMetric.fresh(agents.get(0).id()),
                agents.get(1).id(), AgentGradeMetric.fresh(agents.get(1).id())));
        given(recommendClient.rebuild(anyList())).willReturn(new IndexOutcome("rebuild", 2, 2, "v1"));

        indexer.rebuild();

        assertThat(rebuiltRecords()).extracting(IndexedAgent::name)
                .containsExactly("성적 분석 에이전트", "가정통신문 작성기");
    }

    // 재산정 배치가 등급을 바꾼 뒤 다시 넣으면, 색인도 바뀐 등급과 재료에서 계산한 성공률(120 / 125)을 받는다.
    @Test
    void 전체_다시_넣기는_가격_등급과_재료에서_계산한_성공률을_실어_보낸다() {
        PublicAgent seeded = new PublicAgent(AgentId.newId(), OWNER, "성적 분석 에이전트", "한 줄 소개", null, null,
                Tier.TIER_2, AbcAmount.of(new BigDecimal("6")));
        PublicAgent fresh = publicAgent("가정통신문 작성기");
        given(agentReader.readPublic()).willReturn(List.of(seeded, fresh));
        given(agentGradeMetricReader.readAll(anyList())).willReturn(Map.of(
                seeded.id(), new AgentGradeMetric(seeded.id(), 120, 5, 25, new BigDecimal("4.6"), Instant.now()),
                fresh.id(), AgentGradeMetric.fresh(fresh.id())));
        given(recommendClient.rebuild(anyList())).willReturn(new IndexOutcome("rebuild", 2, 2, "v1"));

        indexer.rebuild();

        assertThat(rebuiltRecords())
                .extracting(IndexedAgent::name, IndexedAgent::tier, IndexedAgent::successRate)
                .containsExactly(
                        tuple("성적 분석 에이전트", 2, 0.96),
                        tuple("가정통신문 작성기", 4, 1.0));
    }

    @Test
    @DisplayName("기동 시 다시 넣기는 무엇이 터져도 기동을 막지 않는다")
    void startupRebuildNeverBlocksBoot() {
        willThrow(new IllegalStateException("db is down")).given(agentReader).readPublic();

        assertThatCode(indexer::rebuildOnStartup).doesNotThrowAnyException();
    }

    private void givenFreshMetric() {
        given(agentGradeMetricReader.read(any())).willAnswer(call -> AgentGradeMetric.fresh(call.getArgument(0)));
    }

    private IndexedAgent sentRecord() {
        ArgumentCaptor<List<IndexedAgent>> sent = ArgumentCaptor.captor();
        then(recommendClient).should().upsert(sent.capture());

        return sent.getValue().get(0);
    }

    private List<IndexedAgent> rebuiltRecords() {
        ArgumentCaptor<List<IndexedAgent>> sent = ArgumentCaptor.captor();
        then(recommendClient).should().rebuild(sent.capture());

        return sent.getValue();
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
        return new PublicAgent(AgentId.newId(), OWNER, name, "한 줄 소개", null, null,
                Tier.initial(), AbcAmount.of(new BigDecimal("6")));
    }

    private static AgentProfile profile() {
        return new AgentProfile("성적 분석 에이전트", "성적을 분석한다",
                "학생별 점수 표를 받아 이번 기간과 지난 기간을 비교한다", null, null, null);
    }
}
