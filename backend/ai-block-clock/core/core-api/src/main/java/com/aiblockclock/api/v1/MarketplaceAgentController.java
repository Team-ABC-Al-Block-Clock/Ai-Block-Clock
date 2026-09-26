package com.aiblockclock.api.v1;

import com.aiblockclock.api.v1.response.MarketplaceAgentDetailResponse;
import com.aiblockclock.api.v1.response.MarketplaceAgentPageResponse;
import com.aiblockclock.domain.agent.AgentId;
import com.aiblockclock.domain.agent.MarketplaceAgentService;
import com.aiblockclock.domain.agent.MarketplaceListing;
import com.aiblockclock.support.paging.CursorPage;
import com.aiblockclock.support.response.ApiResult;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/marketplace/agents")
@RequiredArgsConstructor
public class MarketplaceAgentController implements MarketplaceAgentApi {
    private final MarketplaceAgentService marketplaceAgentService;

    @Override
    @GetMapping
    public ApiResult<MarketplaceAgentPageResponse> find(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "9") int size
    ) {
        CursorPage<MarketplaceListing> page = marketplaceAgentService.find(keyword, cursor, size);

        return ApiResult.success(MarketplaceAgentPageResponse.from(page));
    }

    @Override
    @GetMapping("/{agentId}")
    public ApiResult<MarketplaceAgentDetailResponse> get(@PathVariable UUID agentId) {
        MarketplaceListing listing = marketplaceAgentService.get(AgentId.of(agentId));

        return ApiResult.success(MarketplaceAgentDetailResponse.from(listing));
    }
}
