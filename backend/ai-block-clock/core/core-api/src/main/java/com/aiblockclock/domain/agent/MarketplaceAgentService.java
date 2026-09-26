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
    private final AgentFavoriteReader agentFavoriteReader;

    public CursorPage<MarketplaceListing> find(UserId requester, String keyword, String cursor, int size) {
        String search = keyword == null || keyword.isBlank() ? null : keyword.strip();
        CursorPage<MarketplaceAgent> page = agentReader.searchMarketplace(search, cursor, size);

        List<MarketplaceListing> content = listingsOf(requester, page.content());

        return new CursorPage<>(content, page.nextCursor(), page.hasNext());
    }

    public MarketplaceAgentDetail get(UserId requester, AgentId id) {
        MarketplaceListing listing = listingsOf(requester, List.of(agentReader.readMarketplace(id))).get(0);

        return new MarketplaceAgentDetail(listing, agentFavoriteReader.read(requester, id).favoriteCount());
    }

    private List<MarketplaceListing> listingsOf(UserId requester, List<MarketplaceAgent> agents) {
        List<AgentId> ids = agents.stream().map(MarketplaceAgent::id).toList();
        Map<AgentId, AgentGradeMetric> metrics = agentGradeMetricReader.readAll(ids);
        Map<UserId, String> nicknames = nicknamesOf(agents);
        Set<AgentId> favorited = agentFavoriteReader.readFavorited(requester, ids);

        return agents.stream()
                .map(agent -> new MarketplaceListing(agent, nicknames.get(agent.owner()), metrics.get(agent.id()),
                        favorited.contains(agent.id())))
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
