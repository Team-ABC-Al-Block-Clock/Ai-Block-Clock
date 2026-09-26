package com.aiblockclock.domain.agent;

import com.aiblockclock.domain.AbcAmount;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.storage.agent.AgentEntity;
import com.aiblockclock.storage.agent.AgentRepository;
import com.aiblockclock.storage.agent.AgentVersionEntity;
import com.aiblockclock.storage.agent.AgentVersionRepository;
import com.aiblockclock.storage.support.OffsetPageable;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;
import com.aiblockclock.support.paging.CursorPage;
import com.aiblockclock.support.paging.OffsetCursor;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Pageable;
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

    public Agent read(AgentId id) {
        return AgentEntityConverter.toDomain(agentRepository.findById(id.value())
                .orElseThrow(() -> new CoreException(ErrorType.AGENT_NOT_FOUND)));
    }

    public List<Agent> readAll(Collection<AgentId> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }

        return agentRepository.findAllById(ids.stream().map(AgentId::value).toList()).stream()
                .map(AgentEntityConverter::toDomain)
                .toList();
    }

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

    public List<SellingAgent> readSellingBy(UserId owner) {
        return agentVersionRepository.findPublicByOwnerId(owner.value()).stream()
                .map(version -> new SellingAgent(toPublicAgent(version),
                        version.getAgent().getThumbnailUrl(), version.getAgent().getCreatedAt()))
                .toList();
    }

    public CursorPage<MarketplaceAgent> searchMarketplace(String keyword, String cursor, int size) {
        int offset = OffsetCursor.decode(cursor);
        Pageable pageable = OffsetPageable.of(offset, size + 1);

        List<AgentVersionEntity> found = agentVersionRepository.findPublicOrderByLatest(keyword, pageable);

        boolean hasNext = found.size() > size;
        List<MarketplaceAgent> content = found.stream()
                .limit(size)
                .map(AgentReader::toMarketplaceAgent)
                .toList();

        return new CursorPage<>(content, hasNext ? OffsetCursor.encode(offset + size) : null, hasNext);
    }

    public MarketplaceAgent readMarketplace(AgentId id) {
        return agentVersionRepository.findPublicByAgentId(id.value())
                .map(AgentReader::toMarketplaceAgent)
                .orElseThrow(() -> new CoreException(ErrorType.MARKET_AGENT_NOT_FOUND));
    }

    private static List<PublicAgent> toPublicAgents(List<AgentVersionEntity> versions) {
        return versions.stream()
                .map(AgentReader::toPublicAgent)
                .toList();
    }

    private static PublicAgent toPublicAgent(AgentVersionEntity version) {
        return new PublicAgent(
                AgentId.of(version.getAgent().getId()),
                UserId.of(version.getAgent().getOwnerId()),
                version.getAgent().getName(),
                version.getAgent().getSummary(),
                version.getTier(),
                AbcAmount.of(version.getBaseCost()));
    }

    private static MarketplaceAgent toMarketplaceAgent(AgentVersionEntity version) {
        Agent agent = AgentEntityConverter.toDomain(version.getAgent());

        return new MarketplaceAgent(
                agent.getId(),
                agent.getOwner(),
                agent.getProfile(),
                toPublicAgent(version).price(),
                version.getAgent().getCreatedAt());
    }
}
