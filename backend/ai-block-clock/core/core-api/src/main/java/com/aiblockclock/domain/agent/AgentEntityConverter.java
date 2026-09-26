package com.aiblockclock.domain.agent;

import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.storage.agent.AgentEntity;

public final class AgentEntityConverter {
    private AgentEntityConverter() {
    }

    public static Agent toDomain(AgentEntity entity) {
        return Agent.restore(
                AgentId.of(entity.getId()),
                UserId.of(entity.getOwnerId()),
                new AgentProfile(entity.getName(), entity.getSummary(), entity.getDescription(),
                        entity.getUsageExample(), entity.getOnboardingMessage(), entity.getThumbnailUrl()),
                publicVersionOf(entity));
    }

    private static PublicVersion publicVersionOf(AgentEntity entity) {
        if (entity.getPublicVersionId() == null) {
            return null;
        }

        return new PublicVersion(
                AgentVersionId.of(entity.getPublicVersionId()),
                VersionNumber.of(entity.getPublicVersionNo()));
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
