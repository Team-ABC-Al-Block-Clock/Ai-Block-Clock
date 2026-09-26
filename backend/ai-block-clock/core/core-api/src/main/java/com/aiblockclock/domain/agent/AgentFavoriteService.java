package com.aiblockclock.domain.agent;

import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.domain.user.UserReader;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;
import com.aiblockclock.support.paging.CursorPage;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AgentFavoriteService {
    private final AgentReader agentReader;
    private final AgentFavoriteReader agentFavoriteReader;
    private final AgentFavoriteWriter agentFavoriteWriter;
    private final AgentGradeMetricReader agentGradeMetricReader;
    private final UserReader userReader;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AgentFavorite add(UserId user, AgentId agent) {
        checkOnMarket(agent);
        agentFavoriteWriter.append(user, agent);

        return agentFavoriteReader.read(user, agent);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AgentFavorite remove(UserId user, AgentId agent) {
        checkOnMarket(agent);
        agentFavoriteWriter.remove(user, agent);

        return agentFavoriteReader.read(user, agent);
    }

    @Transactional(readOnly = true)
    public CursorPage<FavoriteAgent> find(UserId user, String cursor, int size) {
        CursorPage<AgentFavoriteEntry> page = agentFavoriteReader.readPage(user, cursor, size);
        List<AgentId> ids = page.content().stream()
                .map(AgentFavoriteEntry::agentId)
                .toList();

        Map<AgentId, PublicAgent> published = agentReader.readPublic(ids).stream()
                .collect(Collectors.toMap(PublicAgent::id, Function.identity()));
        Map<AgentId, Agent> agents = agentReader.readAll(ids).stream()
                .collect(Collectors.toMap(Agent::getId, Function.identity()));
        Map<AgentId, AgentGradeMetric> metrics = agentGradeMetricReader.readAll(ids);
        Map<UserId, String> nicknames = nicknamesOf(published.values());

        List<FavoriteAgent> content = page.content().stream()
                .map(entry -> toFavoriteAgent(entry, published.get(entry.agentId()), agents.get(entry.agentId()),
                        metrics.get(entry.agentId()), nicknames))
                .toList();

        return new CursorPage<>(content, page.nextCursor(), page.hasNext());
    }

    private void checkOnMarket(AgentId agent) {
        if (agentReader.findPublicVersion(agent).isEmpty()) {
            throw new CoreException(ErrorType.MARKETPLACE_AGENT_NOT_FOUND);
        }
    }

    private Map<UserId, String> nicknamesOf(Collection<PublicAgent> agents) {
        Set<UserId> owners = agents.stream()
                .map(PublicAgent::owner)
                .collect(Collectors.toSet());

        Map<UserId, String> nicknames = new HashMap<>();
        userReader.readAll(owners).forEach(owner -> nicknames.put(owner.id(), owner.nickname()));

        return nicknames;
    }

    private static FavoriteAgent toFavoriteAgent(AgentFavoriteEntry entry, PublicAgent published, Agent agent,
                                                 AgentGradeMetric metric, Map<UserId, String> nicknames) {
        return new FavoriteAgent(
                entry.agentId(),
                published.name(),
                published.summary(),
                agent.getProfile().thumbnailUrl(),
                published.price(),
                metric.countedRuns(),
                nicknames.get(published.owner()),
                entry.favoritedAt());
    }
}
