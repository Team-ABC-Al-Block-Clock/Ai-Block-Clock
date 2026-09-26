package com.aiblockclock.domain.agent;

import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class Agent {
    private final AgentId id;
    private final UserId owner;
    private final AgentProfile profile;
    private final PublicVersion publicVersion;

    public static Agent draft(AgentId id, UserId owner, AgentProfile profile) {
        return new Agent(id, owner, profile, null);
    }

    public static Agent restore(AgentId id, UserId owner, AgentProfile profile, PublicVersion publicVersion) {
        return new Agent(id, owner, profile, publicVersion);
    }

    public Agent publish(UserId requester, AgentVersionId versionId, VersionNumber number) {
        ensureOwner(requester);

        if (publicVersion != null && !publicVersion.isOlderThan(number)) {
            throw new CoreException(ErrorType.AGENT_VERSION_NOT_NEWER);
        }

        return new Agent(id, owner, profile, new PublicVersion(versionId, number));
    }

    public Agent describedAs(AgentProfile changed) {
        return new Agent(id, owner, changed, publicVersion);
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
