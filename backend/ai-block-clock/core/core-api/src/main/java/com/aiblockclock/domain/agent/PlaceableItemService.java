package com.aiblockclock.domain.agent;

import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.domain.user.UserReader;
import com.aiblockclock.enums.workflow.PlaceableScope;
import com.aiblockclock.support.paging.CursorPage;
import com.aiblockclock.support.paging.OffsetCursor;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlaceableItemService {
    private final AgentReader agentReader;
    private final AgentGradeMetricReader agentGradeMetricReader;
    private final UserReader userReader;

    public CursorPage<PlaceableItem> find(
            UserId requester, PlaceableScope scope, String keyword, String cursor, int size) {
        int offset = OffsetCursor.decode(cursor);
        String search = keyword == null || keyword.isBlank() ? null : keyword.strip();

        List<PublicAgent> agents = agentReader.readPublic().stream()
                .filter(agent -> scope.includesOthers() || agent.owner().equals(requester))
                .filter(agent -> matches(agent.name(), search))
                .toList();

        Map<UserId, String> nicknames = nicknamesOf(agents);
        Map<AgentId, AgentGradeMetric> metrics = agentGradeMetricReader.readAll(
                agents.stream().map(PublicAgent::id).toList());

        List<PlaceableItem> items = new ArrayList<>();
        agents.stream()
                .filter(agent -> agent.owner().equals(requester))
                .forEach(agent -> items.add(
                        fromAgent(agent, true, nicknames.get(agent.owner()), metrics.get(agent.id()))));
        agents.stream()
                .filter(agent -> !agent.owner().equals(requester))
                .forEach(agent -> items.add(
                        fromAgent(agent, false, nicknames.get(agent.owner()), metrics.get(agent.id()))));

        List<PlaceableItem> content = items.stream().skip(offset).limit(size).toList();
        boolean hasNext = items.size() > offset + size;

        return new CursorPage<>(content, hasNext ? OffsetCursor.encode(offset + size) : null, hasNext);
    }

    private Map<UserId, String> nicknamesOf(List<PublicAgent> agents) {
        Set<UserId> owners = agents.stream()
                .map(PublicAgent::owner)
                .collect(Collectors.toSet());

        Map<UserId, String> nicknames = new HashMap<>();
        userReader.readAll(owners).forEach(user -> nicknames.put(user.id(), user.nickname()));

        return nicknames;
    }

    private static boolean matches(String name, String search) {
        return search == null || name.toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT));
    }

    private static PlaceableItem fromAgent(
            PublicAgent agent, boolean mine, String ownerNickname, AgentGradeMetric metric) {
        return new PlaceableItem(
                agent.id().value(),
                agent.name(),
                mine,
                ownerNickname,
                mine ? agent.baseCost() : agent.price(),
                agent.summary(),
                agent.usageExample(),
                agent.onboardingMessage(),
                metric.countedRuns());
    }
}
