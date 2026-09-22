package com.aiblockclock.api.v1;

import com.aiblockclock.api.support.auth.CurrentUser;
import com.aiblockclock.api.v1.request.ExecutionStartRequest;
import com.aiblockclock.api.v1.response.ExecutionProgressResponse;
import com.aiblockclock.api.v1.response.ExecutionStartResponse;
import com.aiblockclock.domain.execution.Execution;
import com.aiblockclock.domain.execution.ExecutionId;
import com.aiblockclock.domain.execution.ExecutionService;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.domain.workflow.Workflow;
import com.aiblockclock.domain.workflow.WorkflowId;
import com.aiblockclock.domain.workflow.WorkflowService;
import com.aiblockclock.support.response.ApiResult;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/runs")
@RequiredArgsConstructor
public class ExecutionController implements ExecutionApi {
    private final WorkflowService workflowService;
    private final ExecutionService executionService;

    @Override
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResult<ExecutionStartResponse> start(
            @CurrentUser UserId requester,
            @RequestBody ExecutionStartRequest request
    ) {
        Workflow workflow = workflowService.get(WorkflowId.of(request.workflowId()), requester);

        Execution started = executionService.start(
                requester, workflow, request.comment(), request.mockOrDefault());

        return ApiResult.success(ExecutionStartResponse.from(started));
    }

    @Override
    @GetMapping("/{executionId}")
    public ApiResult<ExecutionProgressResponse> follow(
            @CurrentUser UserId requester,
            @PathVariable UUID executionId
    ) {
        return ApiResult.success(ExecutionProgressResponse.from(
                executionService.follow(ExecutionId.of(executionId), requester)));
    }
}
