package com.aiblockclock.testsupport;

import com.aiblockclock.enums.node.AiModel;
import com.aiblockclock.enums.node.NodeType;
import com.aiblockclock.storage.node.NodeDefinitionEntity;
import com.aiblockclock.storage.node.NodeDefinitionRepository;

import java.math.BigDecimal;
import java.util.UUID;

public final class NodeDefinitionSeeder {
    private NodeDefinitionSeeder() {
    }

    public static UUID seed(NodeDefinitionRepository nodeRepository, long ownerId) {
        UUID id = UUID.randomUUID();
        nodeRepository.save(new NodeDefinitionEntity(id, ownerId, "시드 노드", NodeType.LLM, "프롬프트 원문",
                AiModel.defaultModel(), new BigDecimal("3.000000")));

        return id;
    }
}
