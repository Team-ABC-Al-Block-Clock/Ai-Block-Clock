package com.aiblockclock.client.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

// 색인에 넣는 공개 메타데이터. 프롬프트 원문과 내부 구성은 여기 담지 않는다 —
// 벡터에서 원문이 복원될 수 있어 색인에 들어가면 프롬프트 비공개가 깨진다.
public record IndexedAgent(
        @JsonProperty("agent_id") String agentId,
        String name,
        String description,
        List<String> tags,
        String category,
        @JsonProperty("owner_id") String ownerId,
        int tier,
        @JsonProperty("success_rate") double successRate,
        @JsonProperty("is_public") boolean isPublic
) {
}
