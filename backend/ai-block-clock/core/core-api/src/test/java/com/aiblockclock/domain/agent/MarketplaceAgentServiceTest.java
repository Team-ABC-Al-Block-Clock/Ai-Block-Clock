package com.aiblockclock.domain.agent;

import com.aiblockclock.domain.AbcAmount;
import com.aiblockclock.domain.user.User;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.domain.user.UserReader;
import com.aiblockclock.support.paging.CursorPage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

class MarketplaceAgentServiceTest {
    private static final UserId SELLER = UserId.of(9501L);
    private static final UserId VIEWER = UserId.of(9502L);

    private final AgentReader agentReader = mock(AgentReader.class);
    private final AgentGradeMetricReader agentGradeMetricReader = mock(AgentGradeMetricReader.class);
    private final UserReader userReader = mock(UserReader.class);
    private final AgentFavoriteReader agentFavoriteReader = mock(AgentFavoriteReader.class);
    private final MarketplaceAgentService service =
            new MarketplaceAgentService(agentReader, agentGradeMetricReader, userReader, agentFavoriteReader);

    @BeforeEach
    void 닉네임과_빈_지표를_준비한다() {
        given(userReader.readAll(anyCollection())).willReturn(List.of(
                new User(SELLER, "0x" + SELLER.value(), "학원장", null, null, 0, null, null)));
        given(agentGradeMetricReader.readAll(anyCollection())).willAnswer(invocation -> {
            Collection<AgentId> agents = invocation.getArgument(0);

            return agents.stream().collect(Collectors.toMap(Function.identity(), AgentGradeMetric::fresh));
        });
    }

    @Test
    @DisplayName("검색어 앞뒤 공백을 떼고 넘기고, 비어 있으면 검색어 없이 찾는다")
    void stripsKeyword() {
        given(agentReader.searchMarketplace(any(), any(), anyInt())).willReturn(CursorPage.last(List.of()));

        service.find(VIEWER, " 성적 ", null, 9);
        service.find(VIEWER, "  ", null, 9);

        then(agentReader).should().searchMarketplace("성적", null, 9);
        then(agentReader).should().searchMarketplace(isNull(), isNull(), anyInt());
    }

    @Test
    @DisplayName("에이전트마다 소유자 닉네임과 실행 지표, 내가 즐겨찾기했는지를 붙이고 커서는 그대로 넘긴다")
    void attachesOwnerAndMetric() {
        MarketplaceAgent first = agent("성적 분석");
        MarketplaceAgent second = agent("가정통신문");
        given(agentReader.searchMarketplace(any(), any(), anyInt()))
                .willReturn(new CursorPage<>(List.of(first, second), "Mg", true));
        given(agentGradeMetricReader.readAll(anyCollection())).willReturn(Map.of(
                first.id(), new AgentGradeMetric(first.id(), 9, 1, 2, new BigDecimal("4.50"), Instant.now()),
                second.id(), AgentGradeMetric.fresh(second.id())));

        given(agentFavoriteReader.readFavorited(VIEWER, List.of(first.id(), second.id())))
                .willReturn(Set.of(second.id()));

        CursorPage<MarketplaceListing> page = service.find(VIEWER, null, null, 2);

        assertThat(page.content())
                .extracting(listing -> listing.agent().profile().name(), MarketplaceListing::ownerNickname,
                        MarketplaceListing::executionCount, MarketplaceListing::successRate,
                        MarketplaceListing::favorited)
                .containsExactly(
                        tuple("성적 분석", "학원장", 10L, 0.9, false),
                        tuple("가정통신문", "학원장", 0L, 0.0, true));
        assertThat(page.nextCursor()).isEqualTo("Mg");
        assertThat(page.hasNext()).isTrue();
    }

    @Test
    @DisplayName("상세는 즐겨찾기한 사람 수까지 붙여 돌려준다")
    void getsWithOwnerAndMetric() {
        MarketplaceAgent found = agent("성적 분석");
        given(agentReader.readMarketplace(found.id())).willReturn(found);
        given(agentGradeMetricReader.readAll(anyCollection())).willReturn(Map.of(
                found.id(), new AgentGradeMetric(found.id(), 3, 1, 0, null, Instant.now())));

        given(agentFavoriteReader.readFavorited(VIEWER, List.of(found.id()))).willReturn(Set.of(found.id()));
        given(agentFavoriteReader.read(VIEWER, found.id())).willReturn(new AgentFavorite(found.id(), true, 5));

        MarketplaceAgentDetail detail = service.get(VIEWER, found.id());
        MarketplaceListing listing = detail.listing();

        assertThat(detail.favoriteCount()).isEqualTo(5);
        assertThat(listing.favorited()).isTrue();
        assertThat(listing.ownerNickname()).isEqualTo("학원장");
        assertThat(listing.executionCount()).isEqualTo(4);
        assertThat(listing.successRate()).isEqualTo(0.75);
    }

    private static MarketplaceAgent agent(String name) {
        return new MarketplaceAgent(AgentId.newId(), SELLER,
                new AgentProfile(name, "한 줄 소개", "자세한 설명", null, null, null),
                AbcAmount.of(new BigDecimal("6")), Instant.now());
    }
}
