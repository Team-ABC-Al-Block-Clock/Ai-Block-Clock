package com.aiblockclock.domain.execution;

import com.aiblockclock.domain.agent.AgentId;
import com.aiblockclock.domain.agent.AgentVersionId;
import com.aiblockclock.domain.agent.AgentVersionNodeReader;
import com.aiblockclock.domain.agent.AgentVersionReader;
import com.aiblockclock.domain.agent.VersionNode;
import com.aiblockclock.domain.node.NodeDefinition;
import com.aiblockclock.domain.node.NodeDefinitionId;
import com.aiblockclock.domain.node.NodeDefinitionReader;
import com.aiblockclock.domain.node.RefFileReader;
import com.aiblockclock.domain.workflow.ControlNode;
import com.aiblockclock.domain.workflow.GraphNode;
import com.aiblockclock.domain.workflow.NodeKey;
import com.aiblockclock.domain.workflow.WorkflowGraph;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class ExecutionUnitAssembler {
    private final NodeDefinitionReader nodeReader;
    private final RefFileReader refFileReader;
    private final AgentVersionReader agentVersionReader;
    private final AgentVersionNodeReader agentVersionNodeReader;

    public UnitGraph assemble(WorkflowGraph graph) {
        return new UnitGraph(graph.nodesInOrder().stream()
                .map(placed -> new PlacedUnit(placed.key(), assemble(placed)))
                .toList(), graph.edgesInOrder());
    }

    // 캔버스에 놓인 자리는 살아 있는 노드를 따라간다. 아직 굳힌 것이 아니라 고치는 대로 바뀌는 게 맞다.
    private ExecutionUnit assemble(GraphNode placed) {
        return switch (placed.sourceType()) {
            case NODE -> {
                NodeDefinitionId nodeId = NodeDefinitionId.of(placed.reference().referenceId());
                NodeDefinition node = nodeReader.read(nodeId);

                yield new NodeUnit(placed.inputSource(), node.getOwner(), node.getSpec().name(),
                        node.getPrompt(), node.getSpec().model(), node.getSpec().output(),
                        refFileReader.read(nodeId), node.getSpec().baseCost());
            }
            case AGENT -> expand(placed);
            case CONTROL -> control(placed);
            case IO -> new IoUnit();
        };
    }

    // 굳힌 버전 안으로 들어가면 살아 있는 노드를 보지 않는다. 판 시점의 내용이 버전에 박혀 있다.
    private ExecutionUnit expand(GraphNode placed) {
        AgentVersionId versionId = AgentVersionId.of(placed.reference().versionId());
        Map<NodeKey, VersionNode> frozen = agentVersionNodeReader.read(versionId);
        WorkflowGraph inside = agentVersionReader.readGraph(versionId);

        return new AgentUnit(AgentId.of(placed.reference().referenceId()), new UnitGraph(inside.nodesInOrder().stream()
                .map(node -> new PlacedUnit(node.key(), assembleInside(node, frozen)))
                .toList(), inside.edgesInOrder()));
    }

    private ExecutionUnit assembleInside(GraphNode placed, Map<NodeKey, VersionNode> frozen) {
        return switch (placed.sourceType()) {
            case NODE -> {
                VersionNode node = require(frozen, placed.key());

                yield new NodeUnit(placed.inputSource(), node.owner(), node.spec().name(), node.prompt(),
                        node.spec().model(), node.spec().output(), node.refFiles(), node.spec().baseCost());
            }
            case AGENT -> expand(placed);
            case CONTROL -> control(placed);
            case IO -> new IoUnit();
        };
    }

    private static ControlUnit control(GraphNode placed) {
        return new ControlUnit(((ControlNode) placed).condition());
    }

    private static VersionNode require(Map<NodeKey, VersionNode> frozen, NodeKey key) {
        VersionNode node = frozen.get(key);
        if (node == null) {
            throw new CoreException(ErrorType.WORKFLOW_NODE_SOURCE_NOT_FOUND,
                    Map.of("nodeKey", key.value()));
        }

        return node;
    }
}
