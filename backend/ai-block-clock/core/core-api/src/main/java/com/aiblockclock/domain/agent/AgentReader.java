package com.aiblockclock.domain.agent;

import com.aiblockclock.domain.AbcAmount;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.storage.agent.AgentRepository;
import com.aiblockclock.storage.agent.PublicAgentRow;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AgentReader {
    private final AgentRepository agentRepository;

    public boolean isOwnedBy(AgentId id, UserId owner) {
        return agentRepository.existsByIdAndOwnerId(id.value(), owner.value());
    }

    public Optional<AgentVersionId> findPublicVersion(AgentId id) {
        return agentRepository.findPublicVersionId(id.value()).map(AgentVersionId::of);
    }

    public List<PublicAgent> readPublic() {
        return toPublicAgents(agentRepository.findPublic());
    }

    public List<PublicAgent> readPublic(Collection<AgentId> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }

        return toPublicAgents(agentRepository.findPublicByIds(ids.stream().map(AgentId::value).toList()));
    }

    private static List<PublicAgent> toPublicAgents(List<PublicAgentRow> rows) {
        return rows.stream()
                .map(row -> new PublicAgent(
                        AgentId.of(row.agentId()),
                        UserId.of(row.ownerId()),
                        row.name(),
                        row.summary(),
                        row.tier(),
                        AbcAmount.of(row.baseCost())))
                .toList();
    }
}
