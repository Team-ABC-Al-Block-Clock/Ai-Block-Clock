package com.aiblockclock.domain.agent;

import com.aiblockclock.domain.DatePeriod;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.enums.agent.MyAgentSort;
import com.aiblockclock.support.paging.CursorPage;
import com.aiblockclock.support.paging.OffsetCursor;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MyAgentService {
    private static final int TREND_DAYS = 7;

    private final AgentReader agentReader;
    private final AgentGradeMetricReader agentGradeMetricReader;
    private final AgentSalesReader agentSalesReader;
    private final AgentFavoriteReader agentFavoriteReader;
    private final Clock clock;

    public CursorPage<MyAgent> find(UserId requester, String keyword, MyAgentSort sort, String cursor, int size) {
        int offset = OffsetCursor.decode(cursor);
        String search = keyword == null || keyword.isBlank() ? null : keyword.strip().toLowerCase(Locale.ROOT);

        List<SellingAgent> selling = agentReader.readSellingBy(requester).stream()
                .filter(agent -> matches(agent.agent(), search))
                .toList();
        List<AgentId> ids = selling.stream()
                .map(agent -> agent.agent().id())
                .toList();

        LocalDate today = LocalDate.ofInstant(clock.instant(), DatePeriod.ZONE);
        Map<AgentId, AgentGradeMetric> metrics = agentGradeMetricReader.readAll(ids);
        Map<AgentId, BigDecimal> revenues = agentSalesReader.readRevenues(requester, ids);
        Map<AgentId, List<Integer>> trends = agentSalesReader.readDailyExecutions(
                requester, ids, today.minusDays(TREND_DAYS - 1), today);
        Set<AgentId> favorited = agentFavoriteReader.readFavorited(requester, ids);

        List<MyAgent> agents = selling.stream()
                .map(agent -> new MyAgent(agent, revenues.get(agent.agent().id()),
                        metrics.get(agent.agent().id()).countedRuns(), favorited.contains(agent.agent().id()),
                        trends.get(agent.agent().id())))
                .sorted(MyAgent.orderOf(sort))
                .toList();

        List<MyAgent> content = agents.stream().skip(offset).limit(size).toList();
        boolean hasNext = agents.size() > offset + size;

        return new CursorPage<>(content, hasNext ? OffsetCursor.encode(offset + size) : null, hasNext);
    }

    private static boolean matches(PublicAgent agent, String search) {
        return search == null
                || agent.name().toLowerCase(Locale.ROOT).contains(search)
                || agent.summary().toLowerCase(Locale.ROOT).contains(search);
    }
}
