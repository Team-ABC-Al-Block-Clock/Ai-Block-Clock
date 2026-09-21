package com.aiblockclock.domain.agent;

import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class Agent {
    private final AgentId id;
    private final UserId owner;

    private AgentProfile profile;

    private PublicVersion publicVersion;

    public static Agent draft(AgentId id, UserId owner, AgentProfile profile) {
        return new Agent(id, owner, profile, null);
    }

    public void updateProfile(UserId requester, AgentProfile profile) {
        ensureOwner(requester);
        this.profile = profile;
    }

    public void markVersionPublished(UserId requester, AgentVersionId versionId, VersionNumber number) {
        ensureOwner(requester);

        if (publicVersion != null && !publicVersion.isOlderThan(number)) {
            throw new CoreException(ErrorType.AGENT_VERSION_NOT_NEWER);
        }

        this.publicVersion = new PublicVersion(versionId, number);
    }

    public boolean isPlaceableBy(UserId requester) {
        if (isOwnedBy(requester)) {
            return true;
        }

        return publicVersion != null;
    }

    public boolean isOwnedBy(UserId candidate) {
        return owner.equals(candidate);
    }

    public void ensureOwner(UserId requester) {
        if (!isOwnedBy(requester)) {
            throw new CoreException(ErrorType.AGENT_FORBIDDEN);
        }
    }
}
