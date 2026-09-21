package com.aiblockclock.domain.agent;

import com.aiblockclock.domain.OwnerId;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class Agent {
    private final AgentId id;
    private final OwnerId owner;

    private AgentProfile profile;

    private PublicVersion publicVersion;

    public static Agent draft(AgentId id, OwnerId owner, AgentProfile profile) {
        return new Agent(id, owner, profile, null);
    }

    public void updateProfile(OwnerId requester, AgentProfile profile) {
        ensureOwner(requester);
        this.profile = profile;
    }

    public void markVersionPublished(OwnerId requester, AgentVersionId versionId, VersionNumber number) {
        ensureOwner(requester);

        if (publicVersion != null && !publicVersion.isOlderThan(number)) {
            throw new CoreException(ErrorType.AGENT_VERSION_NOT_NEWER);
        }

        this.publicVersion = new PublicVersion(versionId, number);
    }

    public boolean isOwnedBy(OwnerId candidate) {
        return owner.equals(candidate);
    }

    public void ensureOwner(OwnerId requester) {
        if (!isOwnedBy(requester)) {
            throw new CoreException(ErrorType.AGENT_FORBIDDEN);
        }
    }
}
