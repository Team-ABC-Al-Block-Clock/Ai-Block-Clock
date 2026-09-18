package com.aiblockclock.domain.node;

import com.aiblockclock.domain.AbcAmount;
import com.aiblockclock.domain.OwnerId;
import com.aiblockclock.enums.node.AiModel;
import com.aiblockclock.support.paging.CursorPage;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NodeDefinitionService {
    private final NodeDefinitionReader nodeReader;
    private final NodeDefinitionWriter nodeWriter;
    private final BaseCostCalculator baseCostCalculator;
    private final NodeDefinitionDeletionPolicy deletionPolicy;

    public NodeDefinition create(OwnerId requester, String name, Prompt prompt, AiModel model) {
        AbcAmount baseCost = baseCostCalculator.calculate(prompt, model);

        return nodeWriter.append(NodeDefinition.create(NodeDefinitionId.newId(), requester, name, prompt, model, baseCost));
    }

    public NodeDefinition get(NodeDefinitionId id, OwnerId requester) {
        NodeDefinition node = nodeReader.read(id);
        node.ensureOwner(requester);

        return node;
    }

    public CursorPage<NodeDefinitionSummary> getMine(OwnerId requester, String keyword, String cursor, int size) {
        return nodeReader.readMine(requester, keyword, cursor, size);
    }

    public NodeDefinition update(NodeDefinitionId id, OwnerId requester, String name, Prompt prompt, AiModel model) {
        NodeDefinition node = nodeReader.read(id);
        node.update(requester, name, prompt, model, baseCostCalculator.calculate(prompt, model));

        return nodeWriter.update(node);
    }

    public void delete(NodeDefinitionId id, OwnerId requester) {
        deletionPolicy.ensureDeletable(nodeReader.read(id), requester);
        nodeWriter.remove(id);
    }
}
