package com.aiblockclock.domain.agent;

import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.domain.user.UserReader;
import com.aiblockclock.support.paging.CursorPage;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MarketplaceAgentService {
    private final AgentReader agentReader;
    private final AgentGradeMetricReader agentGradeMetricReader;
    private final UserReader userReader;

    public CursorPage<MarketplaceListing> find(String keyword, String cursor, int size) {
        String search = keyword == null || keyword.isBlank() ? null : keyword.strip();
        CursorPage<MarketplaceAgent> page = agentReader.searchMarketplace(search, cursor, size);

        List<MarketplaceListing> content = listingsOf(page.content());

        return new CursorPage<>(content, page.nextCursor(), page.hasNext());
    }

    public MarketplaceListing get(AgentId id) {
        return listingsOf(List.of(agentReader.readMarketplace(id))).get(0);
    }

    private List<MarketplaceListing> listingsOf(List<MarketplaceAgent> agents) {
        Map<AgentId, AgentGradeMetric> metrics = agentGradeMetricReader.readAll(
                agents.stream().map(MarketplaceAgent::id).toList());
        Map<UserId, String> nicknames = nicknamesOf(agents);

        return agents.stream()
                .map(agent -> new MarketplaceListing(agent, nicknames.get(agent.owner()), metrics.get(agent.id())))
                .toList();
    }

    private Map<UserId, String> nicknamesOf(List<MarketplaceAgent> agents) {
        Set<UserId> owners = agents.stream()
                .map(MarketplaceAgent::owner)
                .collect(Collectors.toSet());

        Map<UserId, String> nicknames = new HashMap<>();
        userReader.readAll(owners).forEach(user -> nicknames.put(user.id(), user.nickname()));

        return nicknames;
    }
}
