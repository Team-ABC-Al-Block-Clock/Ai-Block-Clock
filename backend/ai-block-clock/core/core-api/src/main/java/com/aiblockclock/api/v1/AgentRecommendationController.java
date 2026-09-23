package com.aiblockclock.api.v1;

import com.aiblockclock.api.v1.response.AgentRecommendationResponse;
import com.aiblockclock.domain.agent.AgentRecommendationService;
import com.aiblockclock.domain.agent.PublicAgent;
import com.aiblockclock.support.response.ApiResult;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/marketplace/agents/recommendations")
@RequiredArgsConstructor
public class AgentRecommendationController implements AgentRecommendationApi {
    private final AgentRecommendationService recommendationService;

    @Override
    @GetMapping
    public ApiResult<AgentRecommendationResponse> recommend(
            @RequestParam String query,
            @RequestParam(defaultValue = "8") int size
    ) {
        List<PublicAgent> found = recommendationService.recommend(query, size);

        return ApiResult.success(AgentRecommendationResponse.from(found));
    }
}
