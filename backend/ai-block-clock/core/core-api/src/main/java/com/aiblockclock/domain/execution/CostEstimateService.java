package com.aiblockclock.domain.execution;

import com.aiblockclock.domain.payment.AbcBalanceReader;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.domain.workflow.Workflow;
import com.aiblockclock.domain.workflow.WorkflowId;
import com.aiblockclock.domain.workflow.WorkflowReader;
import com.aiblockclock.domain.workflow.WorkflowValidator;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CostEstimateService {
    private final WorkflowReader workflowReader;
    private final CostEstimateAssembler costEstimateAssembler;
    private final AbcBalanceReader abcBalanceReader;

    @Transactional(readOnly = true)
    public CostQuote quote(WorkflowId workflowId, UserId requester) {
        Workflow workflow = workflowReader.read(workflowId);
        WorkflowValidator.ensureOwner(workflow.getOwner(), requester);

        CostEstimate estimate = costEstimateAssembler.assemble(workflow);

        return CostQuote.of(estimate, abcBalanceReader.read(requester));
    }
}
