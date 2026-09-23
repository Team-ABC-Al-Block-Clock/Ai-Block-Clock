package com.aiblockclock.domain.agent;

import com.aiblockclock.domain.AbcAmount;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.storage.agent.AgentEntity;
import com.aiblockclock.storage.agent.AgentRepository;
import com.aiblockclock.storage.agent.AgentVersionEntity;
import com.aiblockclock.storage.agent.AgentVersionRepository;

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
    private final AgentVersionRepository agentVersionRepository;

    public boolean isOwnedBy(AgentId id, UserId owner) {
        return agentRepository.existsByIdAndOwnerId(id.value(), owner.value());
    }

    public Optional<AgentVersionId> findPublicVersion(AgentId id) {
        return agentRepository.findById(id.value())
                .map(AgentEntity::getPublicVersionId)
                .map(AgentVersionId::of);
    }

    public List<PublicAgent> readPublic() {
        return toPublicAgents(agentVersionRepository.findPublic());
    }

    public List<PublicAgent> readPublic(Collection<AgentId> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }

        return toPublicAgents(
                agentVersionRepository.findPublicByAgentIds(ids.stream().map(AgentId::value).toList()));
    }

    private static List<PublicAgent> toPublicAgents(List<AgentVersionEntity> versions) {
        return versions.stream()
                .map(version -> new PublicAgent(
                        AgentId.of(version.getAgent().getId()),
                        UserId.of(version.getAgent().getOwnerId()),
                        version.getAgent().getName(),
                        version.getAgent().getSummary(),
                        version.getTier(),
                        AbcAmount.of(version.getBaseCost())))
                .toList();
    }
}
