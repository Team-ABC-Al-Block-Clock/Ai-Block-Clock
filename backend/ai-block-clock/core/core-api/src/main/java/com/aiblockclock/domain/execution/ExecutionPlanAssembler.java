package com.aiblockclock.domain.execution;

import com.aiblockclock.domain.workflow.Workflow;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ExecutionPlanAssembler {
    private final ExecutionUnitAssembler executionUnitAssembler;

    public ExecutionPlan assemble(Workflow workflow) {
        return ExecutionPlanner.plan(executionUnitAssembler.assemble(workflow.getGraph()));
    }
}
