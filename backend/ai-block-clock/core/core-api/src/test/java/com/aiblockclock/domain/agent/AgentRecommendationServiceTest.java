package com.aiblockclock.domain.agent;

import com.aiblockclock.client.ai.RecommendClient;
import com.aiblockclock.client.ai.AiClientError;
import com.aiblockclock.client.ai.AiClientException;
import com.aiblockclock.client.ai.RecommendedAgent;
import com.aiblockclock.domain.AbcAmount;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.enums.agent.Tier;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;

class AgentRecommendationServiceTest {
    private static final UserId OWNER = UserId.of(9301L);

    private final RecommendClient recommendClient = mock(RecommendClient.class);
    private final AgentReader agentReader = mock(AgentReader.class);
    private final AgentRecommendationService service =
            new AgentRecommendationService(recommendClient, agentReader);

    @Test
    @DisplayName("추천 엔진이 매긴 순서를 그대로 지킨다")
    void keepsTheEnginesOrder() {
        PublicAgent first = publicAgent("성적 분석 에이전트");
        PublicAgent second = publicAgent("가정통신문 작성기");
        given(recommendClient.recommend(anyString(), anyInt()))
                .willReturn(List.of(ranked(second), ranked(first)));
        given(agentReader.readPublic(anyCollection())).willReturn(List.of(first, second));

        assertThat(service.recommend("성적표로 리포트를 쓰고 싶어", 8))
                .extracting(PublicAgent::name)
                .containsExactly("가정통신문 작성기", "성적 분석 에이전트");
    }

    @Test
    @DisplayName("색인에만 남아 있고 지금은 공개되지 않은 것은 빼고 내린다")
    void dropsWhatIsNoLongerPublished() {
        PublicAgent published = publicAgent("성적 분석 에이전트");
        given(recommendClient.recommend(anyString(), anyInt()))
                .willReturn(List.of(ranked(published), ranked(publicAgent("내려간 에이전트"))));
        given(agentReader.readPublic(anyCollection())).willReturn(List.of(published));

        assertThat(service.recommend("성적표", 8)).containsExactly(published);
    }

    @Test
    @DisplayName("우리가 넣지 않은 시드 ID 가 섞여 와도 깨지지 않는다")
    void ignoresIdsWeNeverIndexed() {
        given(recommendClient.recommend(anyString(), anyInt()))
                .willReturn(List.of(new RecommendedAgent("ag_seed_01", "시드", 0.8, "설명")));
        given(agentReader.readPublic(anyCollection())).willReturn(List.of());

        assertThat(service.recommend("성적표", 8)).isEmpty();
    }

    @Test
    @DisplayName("추천 엔진에 닿지 못하면 추천을 못 가져왔다고 끊는다")
    void failsWhenTheEngineIsUnreachable() {
        willThrow(new AiClientException(AiClientError.REQUEST_FAILED, "engine is down"))
                .given(recommendClient).recommend(anyString(), anyInt());

        assertThatThrownBy(() -> service.recommend("성적표", 8))
                .isInstanceOf(CoreException.class)
                .extracting(e -> ((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.RECOMMEND_UNAVAILABLE);
    }

    private static RecommendedAgent ranked(PublicAgent agent) {
        return new RecommendedAgent(agent.id().value().toString(), agent.name(), 0.8, agent.summary());
    }

    private static PublicAgent publicAgent(String name) {
        return new PublicAgent(AgentId.newId(), OWNER, name, "한 줄 소개",
                Tier.initial(), AbcAmount.of(new BigDecimal("6")));
    }
}
