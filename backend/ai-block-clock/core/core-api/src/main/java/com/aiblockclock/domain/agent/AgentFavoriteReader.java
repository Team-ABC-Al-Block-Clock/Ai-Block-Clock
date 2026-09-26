package com.aiblockclock.domain.agent;

import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.storage.agent.AgentFavoriteEntity;
import com.aiblockclock.storage.agent.AgentFavoriteRepository;
import com.aiblockclock.storage.support.OffsetPageable;
import com.aiblockclock.support.paging.CursorPage;
import com.aiblockclock.support.paging.OffsetCursor;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AgentFavoriteReader {
    private final AgentFavoriteRepository agentFavoriteRepository;

    public AgentFavorite read(UserId user, AgentId agent) {
        return new AgentFavorite(
                agent,
                agentFavoriteRepository.existsByUserIdAndAgentId(user.value(), agent.value()),
                agentFavoriteRepository.countByAgentId(agent.value()));
    }

    public CursorPage<AgentFavoriteEntry> readPage(UserId user, String cursor, int size) {
        int offset = OffsetCursor.decode(cursor);

        List<AgentFavoriteEntry> found = agentFavoriteRepository
                .findPublicByUserId(user.value(), OffsetPageable.of(offset, size + 1)).stream()
                .map(AgentFavoriteReader::toEntry)
                .toList();
        boolean hasNext = found.size() > size;
        List<AgentFavoriteEntry> content = found.stream()
                .limit(size)
                .toList();

        return new CursorPage<>(content, hasNext ? OffsetCursor.encode(offset + size) : null, hasNext);
    }

    private static AgentFavoriteEntry toEntry(AgentFavoriteEntity entity) {
        return new AgentFavoriteEntry(AgentId.of(entity.getAgentId()), entity.getCreatedAt());
    }
}
