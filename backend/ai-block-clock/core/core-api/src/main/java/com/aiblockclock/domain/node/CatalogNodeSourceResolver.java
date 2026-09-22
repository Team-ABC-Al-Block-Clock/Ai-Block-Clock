package com.aiblockclock.domain.node;

import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.domain.workflow.BaseNode;
import com.aiblockclock.domain.workflow.GraphNode;
import com.aiblockclock.domain.workflow.NodeDraft;
import com.aiblockclock.domain.workflow.NodePlacement;
import com.aiblockclock.domain.workflow.NodeSourceResolver;
import com.aiblockclock.enums.workflow.SourceType;
import com.aiblockclock.storage.node.NodeDefinitionEntity;
import com.aiblockclock.storage.node.NodeDefinitionRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CatalogNodeSourceResolver implements NodeSourceResolver {
    private final NodeDefinitionRepository nodeRepository;
    private final NodeDefinitionService nodeService;

    @Override
    public SourceType sourceType() {
        return SourceType.NODE;
    }

    @Override
    public Optional<GraphNode> resolve(UserId requester, NodePlacement placement) {
        if (placement.draft() != null) {
            return Optional.of(place(placement, create(requester, placement.draft())));
        }

        return nodeRepository.findByIdForShare(placement.sourceId())
                .filter(defined -> placement.nodeType() == null || defined.getNodeType() == placement.nodeType())
                .map(NodeDefinitionEntity::getId)
                .map(sourceId -> place(placement, sourceId));
    }

    private UUID create(UserId requester, NodeDraft draft) {
        NodeDefinition created =
                nodeService.create(requester, draft.name(), Prompt.of(draft.prompt()), draft.model());

        return created.getId().value();
    }

    private static GraphNode place(NodePlacement placement, UUID sourceId) {
        return new BaseNode(placement.key(), sourceId, placement.inputSource());
    }
}
