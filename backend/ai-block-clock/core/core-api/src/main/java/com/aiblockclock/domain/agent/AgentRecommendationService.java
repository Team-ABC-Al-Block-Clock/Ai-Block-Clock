package com.aiblockclock.domain.agent;

import com.aiblockclock.client.ai.RecommendClient;
import com.aiblockclock.client.ai.AiClientException;
import com.aiblockclock.client.ai.RecommendedAgent;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentRecommendationService {
    private final RecommendClient recommendClient;
    private final AgentReader agentReader;

    public List<PublicAgent> recommend(String query, int size) {
        List<AgentId> ranked = rank(query, size);
        Map<AgentId, PublicAgent> published = agentReader.readPublic(ranked).stream()
                .collect(Collectors.toMap(PublicAgent::id, Function.identity()));

        return ranked.stream()
                .map(published::get)
                .filter(Objects::nonNull)
                .toList();
    }

    private List<AgentId> rank(String query, int size) {
        try {
            return recommendClient.recommend(query, size).stream()
                    .map(RecommendedAgent::agentId)
                    .map(AgentRecommendationService::toAgentId)
                    .flatMap(Optional::stream)
                    .toList();
        } catch (AiClientException exception) {
            log.warn("추천을 가져오지 못했다: {}", exception.getMessage(), exception);

            throw new CoreException(ErrorType.RECOMMEND_UNAVAILABLE);
        }
    }

    // 색인에는 시연용 시드처럼 우리가 넣지 않은 ID 가 섞여 있을 수 있다. 읽을 수 없으면 버린다.
    private static Optional<AgentId> toAgentId(String value) {
        try {
            return Optional.of(AgentId.of(UUID.fromString(value)));
        } catch (IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }
}
