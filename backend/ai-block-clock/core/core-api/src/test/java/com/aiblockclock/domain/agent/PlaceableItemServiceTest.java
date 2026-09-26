package com.aiblockclock.domain.agent;

import com.aiblockclock.domain.AbcAmount;
import com.aiblockclock.domain.Timestamps;
import com.aiblockclock.domain.node.NodeDefinitionId;
import com.aiblockclock.domain.node.NodeDefinitionReader;
import com.aiblockclock.domain.node.NodeDefinitionSummary;
import com.aiblockclock.domain.node.NodeOutput;
import com.aiblockclock.domain.node.NodeSpec;
import com.aiblockclock.domain.user.User;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.domain.user.UserReader;
import com.aiblockclock.enums.agent.Tier;
import com.aiblockclock.enums.node.AiModel;
import com.aiblockclock.enums.node.NodeType;
import com.aiblockclock.enums.node.OutputKind;
import com.aiblockclock.enums.workflow.PlaceableScope;
import com.aiblockclock.support.paging.CursorPage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class PlaceableItemServiceTest {
    private static final UserId ME = UserId.of(9401L);
    private static final UserId OTHER = UserId.of(9402L);

    private final NodeDefinitionReader nodeReader = mock(NodeDefinitionReader.class);
    private final AgentReader agentReader = mock(AgentReader.class);
    private final UserReader userReader = mock(UserReader.class);
    private final PlaceableItemService service = new PlaceableItemService(nodeReader, agentReader, userReader);

    @BeforeEach
    void 닉네임을_준비한다() {
        given(userReader.readAll(anyCollection())).willReturn(List.of(user(ME, "나"), user(OTHER, "남")));
    }

    @Test
    @DisplayName("내 노드, 내 에이전트, 남의 에이전트 순으로 담는다")
    void putsMineFirst() {
        given(nodeReader.readAllMine(ME, null)).willReturn(List.of(node("성적 분석")));
        given(agentReader.readPublic()).willReturn(List.of(agent(OTHER, "남의 에이전트"), agent(ME, "내 에이전트")));

        CursorPage<PlaceableItem> page = service.find(ME, PlaceableScope.ALL, null, null, 20);

        assertThat(page.content())
                .extracting(PlaceableItem::name, PlaceableItem::mine, PlaceableItem::ownerNickname)
                .containsExactly(
                        tuple("성적 분석", true, "나"),
                        tuple("내 에이전트", true, "나"),
                        tuple("남의 에이전트", false, "남"));
    }

    @Test
    @DisplayName("MINE 이면 남의 에이전트를 빼고 담는다")
    void leavesOthersOutForMine() {
        given(nodeReader.readAllMine(ME, null)).willReturn(List.of());
        given(agentReader.readPublic()).willReturn(List.of(agent(OTHER, "남의 에이전트"), agent(ME, "내 에이전트")));

        assertThat(service.find(ME, PlaceableScope.MINE, null, null, 20).content())
                .extracting(PlaceableItem::name)
                .containsExactly("내 에이전트");
    }

    @Test
    @DisplayName("에이전트도 이름으로 걸러 담는다")
    void filtersAgentsByName() {
        given(nodeReader.readAllMine(any(), any())).willReturn(List.of());
        given(agentReader.readPublic()).willReturn(List.of(agent(OTHER, "성적 리포트"), agent(OTHER, "가정통신문")));

        assertThat(service.find(ME, PlaceableScope.ALL, " 성적 ", null, 20).content())
                .extracting(PlaceableItem::name)
                .containsExactly("성적 리포트");
    }

    @Test
    @DisplayName("한 쪽을 넘기면 다음 쪽 커서로 나머지를 이어 받는다")
    void pagesThroughTheRest() {
        given(nodeReader.readAllMine(ME, null)).willReturn(List.of(node("하나"), node("둘"), node("셋")));
        given(agentReader.readPublic()).willReturn(List.of());

        CursorPage<PlaceableItem> first = service.find(ME, PlaceableScope.ALL, null, null, 2);
        CursorPage<PlaceableItem> second = service.find(ME, PlaceableScope.ALL, null, first.nextCursor(), 2);

        assertThat(first.hasNext()).isTrue();
        assertThat(second.content()).extracting(PlaceableItem::name).containsExactly("셋");
        assertThat(second.hasNext()).isFalse();
    }

    private static NodeDefinitionSummary node(String name) {
        return new NodeDefinitionSummary(NodeDefinitionId.newId(),
                new NodeSpec(name, NodeType.LLM, AiModel.defaultModel(), AbcAmount.of(new BigDecimal("3")),
                        new NodeOutput(OutputKind.TEXT, null)),
                Timestamps.unsaved());
    }

    private static PublicAgent agent(UserId owner, String name) {
        return new PublicAgent(AgentId.newId(), owner, name, "한 줄 소개",
                Tier.initial(), AbcAmount.of(new BigDecimal("6")));
    }

    private static User user(UserId id, String nickname) {
        return new User(id, "0x" + id.value(), nickname, null, null, 0, null, null);
    }
}
