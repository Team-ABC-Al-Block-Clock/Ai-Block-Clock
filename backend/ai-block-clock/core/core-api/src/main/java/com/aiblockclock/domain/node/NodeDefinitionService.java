package com.aiblockclock.domain.node;

import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.enums.node.AiModel;
import com.aiblockclock.support.paging.CursorPage;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NodeDefinitionService {
    private final NodeDefinitionReader nodeReader;
    private final NodeDefinitionWriter nodeWriter;
    private final BaseCostCalculator baseCostCalculator;
    private final NodeDefinitionValidator validator;

    public NodeDefinition create(UserId requester, String name, Prompt prompt, AiModel model) {
        NodeSpec spec = new NodeSpec(name, model, baseCostCalculator.calculate(prompt, model));

        return nodeWriter.append(NodeDefinition.create(NodeDefinitionId.newId(), requester, spec, prompt));
    }

    public NodeDefinition get(NodeDefinitionId id, UserId requester) {
        NodeDefinition node = nodeReader.read(id);
        NodeDefinitionValidator.ensureOwner(node.getOwner(), requester);

        return node;
    }

    public CursorPage<NodeDefinitionSummary> getMine(UserId requester, String keyword, String cursor, int size) {
        return nodeReader.readMine(requester, keyword, cursor, size);
    }

    @Transactional
    public NodeDefinition update(NodeDefinitionId id, UserId requester, String name, Prompt prompt, AiModel model) {
        validator.ensureEditable(id, requester);

        return nodeWriter.update(id, new NodeSpec(name, model, baseCostCalculator.calculate(prompt, model)), prompt);
    }

    @Transactional
    public void delete(NodeDefinitionId id, UserId requester) {
        validator.ensureDeletable(id, requester);

        nodeWriter.remove(id);
    }
}
