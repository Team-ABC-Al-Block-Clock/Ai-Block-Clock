package com.aiblockclock.domain.node;

import com.aiblockclock.enums.node.AiModel;
import com.aiblockclock.enums.node.NodeType;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class NodeSpecAssembler {
    private final BaseCostCalculator baseCostCalculator;
    private final NodeTestRunReader nodeTestRunReader;

    // 새 노드는 아직 테스트 실행으로 잰 적이 없어 기본 원가로 시작한다.
    public NodeSpec assemble(String name, AiModel model) {
        return new NodeSpec(name, NodeType.LLM, model, baseCostCalculator.calculate(List.of()));
    }

    // 고친 노드는 그동안 잰 원가를 이어 쓴다. 고친 프롬프트로 다시 테스트하면 최근 기록부터 바뀐다.
    public NodeSpec assembleEdited(NodeDefinitionId id, String name, AiModel model) {
        return new NodeSpec(name, NodeType.LLM, model, baseCostCalculator.calculate(
                nodeTestRunReader.readRecentCosts(id, baseCostCalculator.sampleSize())));
    }
}
