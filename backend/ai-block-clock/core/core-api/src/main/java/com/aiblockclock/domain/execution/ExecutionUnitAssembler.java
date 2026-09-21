package com.aiblockclock.domain.execution;

import com.aiblockclock.domain.node.NodeDefinitionId;
import com.aiblockclock.domain.node.NodeDefinitionReader;
import com.aiblockclock.domain.workflow.GraphNode;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ExecutionUnitAssembler {
    private final NodeDefinitionReader nodeReader;

    public ExecutionUnit assemble(GraphNode placed) {
        return switch (placed.sourceType()) {
            case NODE -> new NodeUnit(
                    placed.inputSource(),
                    nodeReader.read(NodeDefinitionId.of(placed.reference().referenceId())));
            case AGENT -> throw new CoreException(ErrorType.INTERNAL_ERROR);
        };
    }
}
