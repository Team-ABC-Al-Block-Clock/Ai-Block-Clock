package com.aiblockclock.domain.agent;

public record AgentProfile(
        String name,
        String summary,
        String description,
        String usageExample,
        String onboardingMessage,
        String thumbnailUrl
) {
}
