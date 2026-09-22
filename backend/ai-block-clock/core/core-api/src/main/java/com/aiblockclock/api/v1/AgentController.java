package com.aiblockclock.api.v1;

import com.aiblockclock.api.support.auth.CurrentUser;
import com.aiblockclock.api.v1.request.AgentRegisterRequest;
import com.aiblockclock.api.v1.response.AgentRegisterResponse;
import com.aiblockclock.domain.agent.AgentRegistration;
import com.aiblockclock.domain.agent.AgentService;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.domain.workflow.Revision;
import com.aiblockclock.domain.workflow.WorkflowId;
import com.aiblockclock.support.response.ApiResult;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/agents")
@RequiredArgsConstructor
public class AgentController implements AgentApi {
    private final AgentService agentService;

    @Override
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResult<AgentRegisterResponse> register(
            @CurrentUser UserId owner,
            @RequestBody AgentRegisterRequest request
    ) {
        AgentRegistration registered = agentService.register(
                owner,
                WorkflowId.of(request.workflowId()),
                Revision.of(request.revision()),
                request.toProfile());

        return ApiResult.success(AgentRegisterResponse.from(registered));
    }
}
