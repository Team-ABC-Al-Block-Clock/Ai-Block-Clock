package com.aiblockclock.api.v1;

import com.aiblockclock.api.v1.response.AgentProvenanceResponse;
import com.aiblockclock.domain.agent.AgentId;
import com.aiblockclock.domain.agent.AgentProvenanceQueryService;
import com.aiblockclock.support.response.ApiResult;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/marketplace/agents")
@RequiredArgsConstructor
public class AgentProvenanceController implements AgentProvenanceApi {
    private final AgentProvenanceQueryService agentProvenanceQueryService;

    @Override
    @GetMapping("/{agentId}/provenance")
    public ApiResult<AgentProvenanceResponse> getProvenance(@PathVariable UUID agentId) {
        return ApiResult.success(AgentProvenanceResponse.from(
                agentProvenanceQueryService.provenanceOf(AgentId.of(agentId))));
    }
}
