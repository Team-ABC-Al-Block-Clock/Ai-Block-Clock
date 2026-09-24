package com.aiblockclock.domain.execution;

import com.aiblockclock.domain.workflow.Workflow;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ExecutionPlanAssembler {
    private final ExecutionUnitAssembler executionUnitAssembler;

    public ExecutionPlan assemble(Workflow workflow) {
        List<PlannedPrompt> prompts = workflow.getGraph().nodesInOrder().stream()
                .flatMap(placed -> executionUnitAssembler.assemble(placed).flatten(placed.key()).stream())
                .toList();

        return new ExecutionPlan(prompts);
    }
}
