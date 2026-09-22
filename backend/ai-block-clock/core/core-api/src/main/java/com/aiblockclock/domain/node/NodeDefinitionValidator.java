package com.aiblockclock.domain.node;

import com.aiblockclock.domain.OwnerId;
import com.aiblockclock.storage.node.NodeDefinitionEntity;
import com.aiblockclock.storage.node.NodeDefinitionRepository;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class NodeDefinitionValidator {
    private final NodeDefinitionRepository nodeRepository;
    private final NodeDefinitionUsageReader usageReader;

    public static void ensureOwner(OwnerId owner, OwnerId requester) {
        if (!owner.equals(requester)) {
            throw new CoreException(ErrorType.NODE_FORBIDDEN);
        }
    }

    @Transactional
    public void ensureEditable(NodeDefinitionId id, OwnerId requester) {
        ensureOwner(ownerOf(lock(id)), requester);
    }

    @Transactional
    public void ensureDeletable(NodeDefinitionId id, OwnerId requester) {
        NodeDefinitionEntity entity = lock(id);
        ensureOwner(ownerOf(entity), requester);

        if (usageReader.isPlacedInAnyWorkflow(id)) {
            throw new CoreException(ErrorType.NODE_PLACED_IN_WORKFLOW);
        }
    }

    private NodeDefinitionEntity lock(NodeDefinitionId id) {
        return nodeRepository.findByIdForUpdate(id.value())
                .orElseThrow(() -> new CoreException(ErrorType.NODE_NOT_FOUND));
    }

    private static OwnerId ownerOf(NodeDefinitionEntity entity) {
        return OwnerId.of(entity.getOwnerId());
    }
}
