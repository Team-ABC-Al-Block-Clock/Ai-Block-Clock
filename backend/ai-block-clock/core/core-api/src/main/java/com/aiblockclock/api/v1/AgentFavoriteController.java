package com.aiblockclock.api.v1;

import com.aiblockclock.api.support.auth.CurrentUser;
import com.aiblockclock.api.v1.response.AgentFavoriteResponse;
import com.aiblockclock.domain.agent.AgentFavoriteService;
import com.aiblockclock.domain.agent.AgentId;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.support.response.ApiResult;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/marketplace/agents/{agentId}/favorites")
@RequiredArgsConstructor
public class AgentFavoriteController implements AgentFavoriteApi {
    private final AgentFavoriteService agentFavoriteService;

    @Override
    @PostMapping
    public ApiResult<AgentFavoriteResponse> add(@CurrentUser UserId user, @PathVariable UUID agentId) {
        return ApiResult.success(AgentFavoriteResponse.from(agentFavoriteService.add(user, AgentId.of(agentId))));
    }

    @Override
    @DeleteMapping
    public ApiResult<AgentFavoriteResponse> remove(@CurrentUser UserId user, @PathVariable UUID agentId) {
        return ApiResult.success(AgentFavoriteResponse.from(agentFavoriteService.remove(user, AgentId.of(agentId))));
    }
}
