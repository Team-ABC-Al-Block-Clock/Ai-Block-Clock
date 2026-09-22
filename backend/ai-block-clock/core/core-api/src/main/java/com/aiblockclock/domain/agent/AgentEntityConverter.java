package com.aiblockclock.domain.agent;

import com.aiblockclock.storage.agent.AgentEntity;

public final class AgentEntityConverter {
    private AgentEntityConverter() {
    }

    public static AgentEntity toEntity(Agent agent) {
        AgentProfile profile = agent.getProfile();
        PublicVersion publicVersion = agent.getPublicVersion();

        return new AgentEntity(
                agent.getId().value(),
                agent.getOwner().value(),
                profile.name(),
                profile.summary(),
                profile.description(),
                profile.usageExample(),
                profile.onboardingMessage(),
                profile.thumbnailUrl(),
                publicVersion == null ? null : publicVersion.id().value(),
                publicVersion == null ? null : publicVersion.number().value());
    }
}
