package com.aiblockclock.domain.agent;

public record PublicVersion(AgentVersionId id, VersionNumber number) {
    public boolean isOlderThan(VersionNumber candidate) {
        return candidate.isAfter(number);
    }
}
