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
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CatalogNodeSourceResolver implements NodeSourceResolver {
    private final NodeDefinitionRepository nodeRepository;
    private final NodeDefinitionWriter nodeWriter;
    private final NodeSpecAssembler specAssembler;

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
                .map(defined -> ensureDeclaredType(placement, defined))
                .map(sourceId -> place(placement, sourceId));
    }

    private UUID create(UserId requester, NodeDraft draft) {
        Prompt prompt = Prompt.of(draft.prompt());
        NodeSpec spec = specAssembler.assemble(draft.name(), draft.model());

        return nodeWriter.append(NodeDefinition.create(NodeDefinitionId.newId(), requester, spec, prompt))
                .getId().value();
    }

    // 종류를 밝히지 않은 배치는 행이 가진 종류를 그대로 따른다.
    private static UUID ensureDeclaredType(NodePlacement placement, NodeDefinitionEntity defined) {
        if (placement.nodeType() != null && defined.getNodeType() != placement.nodeType()) {
            throw new CoreException(ErrorType.WORKFLOW_NODE_TYPE_MISMATCH, Map.of(
                    "nodeKey", placement.key().value(),
                    "sourceId", placement.sourceId(),
                    "nodeType", placement.nodeType()));
        }

        return defined.getId();
    }

    private static GraphNode place(NodePlacement placement, UUID sourceId) {
        return new BaseNode(placement.key(), sourceId, placement.inputSource());
    }
}
