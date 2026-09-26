package com.aiblockclock.domain.agent;

import com.aiblockclock.domain.AbcAmount;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.enums.agent.MyAgentSort;
import com.aiblockclock.enums.agent.Tier;
import com.aiblockclock.support.paging.CursorPage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

class MyAgentServiceTest {
    private static final UserId ME = UserId.of(9501L);
    private static final Instant NOW = Instant.parse("2026-09-27T15:00:00Z");
    private static final List<Integer> NO_TREND = List.of(0, 0, 0, 0, 0, 0, 0);

    private final AgentReader agentReader = mock(AgentReader.class);
    private final AgentGradeMetricReader agentGradeMetricReader = mock(AgentGradeMetricReader.class);
    private final AgentSalesReader agentSalesReader = mock(AgentSalesReader.class);
    private final MyAgentService service = new MyAgentService(
            agentReader, agentGradeMetricReader, agentSalesReader, Clock.fixed(NOW, ZoneOffset.UTC));

    private final SellingAgent oldest = selling("성적 분석", "성적을 분석한다", "2026-09-01T00:00:00Z");
    private final SellingAgent middle = selling("가정통신문", "가정에 보낼 글을 쓴다", "2026-09-02T00:00:00Z");
    private final SellingAgent newest = selling("출결 정리", "출석부를 정리한다", "2026-09-03T00:00:00Z");

    @Test
    @DisplayName("LATEST 는 최근에 등록한 것부터 준다")
    void ordersByLatest() {
        prepare(Map.of(), Map.of());

        assertThat(namesOf(service.find(ME, null, MyAgentSort.LATEST, null, 9)))
                .containsExactly("출결 정리", "가정통신문", "성적 분석");
    }

    @Test
    @DisplayName("EXECUTIONS 는 성공과 판매자 탓 실패를 더한 실행 수가 많은 것부터 준다")
    void ordersByExecutions() {
        prepare(Map.of(oldest, metric(oldest, 4, 1), middle, metric(middle, 1, 0)), Map.of());

        CursorPage<MyAgent> page = service.find(ME, null, MyAgentSort.EXECUTIONS, null, 9);

        assertThat(namesOf(page)).containsExactly("성적 분석", "가정통신문", "출결 정리");
        assertThat(page.content()).extracting(MyAgent::executionCount).containsExactly(5L, 1L, 0L);
    }

    @Test
    @DisplayName("REVENUE 는 받은 정산금이 많은 것부터 주고, 같으면 최근에 등록한 것이 앞에 온다")
    void ordersByRevenueThenLatest() {
        prepare(Map.of(), Map.of(oldest, "1.500000", middle, "1.500000", newest, "0.250000"));

        assertThat(namesOf(service.find(ME, null, MyAgentSort.REVENUE, null, 9)))
                .containsExactly("가정통신문", "성적 분석", "출결 정리");
    }

    @Test
    @DisplayName("검색어는 이름과 한 줄 소개를 함께 본다")
    void matchesNameOrSummary() {
        prepare(Map.of(), Map.of());

        assertThat(namesOf(service.find(ME, " 성적 ", MyAgentSort.LATEST, null, 9))).containsExactly("성적 분석");
        assertThat(namesOf(service.find(ME, "출석부", MyAgentSort.LATEST, null, 9))).containsExactly("출결 정리");
    }

    @Test
    @DisplayName("정렬한 순서 그대로 다음 쪽 커서로 나머지를 이어 받는다")
    void pagesInSortedOrder() {
        prepare(Map.of(), Map.of(oldest, "3.000000", middle, "2.000000", newest, "1.000000"));

        CursorPage<MyAgent> first = service.find(ME, null, MyAgentSort.REVENUE, null, 2);
        CursorPage<MyAgent> second = service.find(ME, null, MyAgentSort.REVENUE, first.nextCursor(), 2);

        assertThat(namesOf(first)).containsExactly("성적 분석", "가정통신문");
        assertThat(first.hasNext()).isTrue();
        assertThat(namesOf(second)).containsExactly("출결 정리");
        assertThat(second.hasNext()).isFalse();
        assertThat(second.nextCursor()).isNull();
    }

    @Test
    @DisplayName("최근 7일은 엿새 전부터 오늘까지 UTC 날짜로 잡는다")
    void asksTrendForTheLastSevenDays() {
        prepare(Map.of(), Map.of());

        service.find(ME, null, MyAgentSort.LATEST, null, 9);

        then(agentSalesReader).should().readDailyExecutions(eq(ME), anyCollection(),
                eq(LocalDate.of(2026, 9, 21)), eq(LocalDate.of(2026, 9, 27)));
    }

    private void prepare(Map<SellingAgent, AgentGradeMetric> counted, Map<SellingAgent, String> earned) {
        List<SellingAgent> selling = List.of(newest, middle, oldest);
        given(agentReader.readSellingBy(ME)).willReturn(selling);
        given(agentGradeMetricReader.readAll(anyCollection())).willAnswer(invocation -> each(invocation.getArgument(0),
                id -> counted.entrySet().stream()
                        .filter(entry -> entry.getKey().agent().id().equals(id))
                        .map(Map.Entry::getValue)
                        .findFirst()
                        .orElse(AgentGradeMetric.fresh(id))));
        given(agentSalesReader.readRevenues(eq(ME), anyCollection())).willAnswer(invocation -> each(
                invocation.getArgument(1), id -> earned.entrySet().stream()
                        .filter(entry -> entry.getKey().agent().id().equals(id))
                        .map(entry -> new BigDecimal(entry.getValue()))
                        .findFirst()
                        .orElse(new BigDecimal("0.000000"))));
        given(agentSalesReader.readDailyExecutions(eq(ME), anyCollection(), any(), any()))
                .willAnswer(invocation -> each(invocation.getArgument(1), id -> NO_TREND));
    }

    private static <T> Map<AgentId, T> each(Collection<AgentId> ids, Function<AgentId, T> value) {
        return ids.stream().collect(Collectors.toMap(Function.identity(), value));
    }

    private static List<String> namesOf(CursorPage<MyAgent> page) {
        return page.content().stream()
                .map(agent -> agent.selling().agent().name())
                .toList();
    }

    private static AgentGradeMetric metric(SellingAgent agent, long successes, long failures) {
        return new AgentGradeMetric(agent.agent().id(), successes, failures, 0, null, NOW);
    }

    private static SellingAgent selling(String name, String summary, String createdAt) {
        PublicAgent agent = new PublicAgent(AgentId.newId(), ME, name, summary,
                Tier.initial(), AbcAmount.of(new BigDecimal("6")));

        return new SellingAgent(agent, null, Instant.parse(createdAt));
    }
}
