package com.aiblockclock.domain.node;

import com.aiblockclock.domain.AbcAmount;
import com.aiblockclock.storage.node.NodeDefinitionEntity;
import com.aiblockclock.storage.node.NodeDefinitionRepository;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class NodeDefinitionWriter {
    private final NodeDefinitionRepository nodeRepository;

    @Transactional
    public NodeDefinition append(NodeDefinition node) {
        return NodeDefinitionEntityConverter.toDomain(nodeRepository.save(NodeDefinitionEntityConverter.toEntity(node)));
    }

    @Transactional
    public NodeDefinition update(NodeDefinitionId id, NodeSpec spec, Prompt prompt) {
        NodeDefinitionEntity entity = nodeRepository.findById(id.value())
                .orElseThrow(() -> new CoreException(ErrorType.NODE_NOT_FOUND));

        entity.edit(spec.name(), prompt.value(), spec.model(), spec.baseCost().value());

        return NodeDefinitionEntityConverter.toDomain(entity);
    }

    // 원가를 다시 정하기 전에 행을 잠근다. 잠근 뒤에 읽은 테스트 기록으로 정해야 동시에 끝난 테스트를 빠뜨리지 않는다.
    @Transactional(propagation = Propagation.MANDATORY)
    public void lock(NodeDefinitionId id) {
        nodeRepository.findByIdForUpdate(id.value())
                .orElseThrow(() -> new CoreException(ErrorType.NODE_NOT_FOUND));
    }

    // 노드 원가만 바꾼다. 이미 굳힌 에이전트 버전은 제 원가를 들고 있어 따라 바뀌지 않는다.
    @Transactional
    public void reprice(NodeDefinitionId id, AbcAmount baseCost) {
        NodeDefinitionEntity entity = nodeRepository.findById(id.value())
                .orElseThrow(() -> new CoreException(ErrorType.NODE_NOT_FOUND));

        entity.reprice(baseCost.value());
    }

    @Transactional
    public void remove(NodeDefinitionId id) {
        nodeRepository.deleteById(id.value());
    }
}
