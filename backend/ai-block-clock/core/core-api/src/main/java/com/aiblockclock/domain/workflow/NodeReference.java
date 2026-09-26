package com.aiblockclock.domain.workflow;

import java.util.UUID;

public record NodeReference(UUID referenceId, UUID versionId) {
    public static NodeReference of(UUID referenceId) {
        return new NodeReference(referenceId, null);
    }

    public static NodeReference of(UUID referenceId, UUID versionId) {
        return new NodeReference(referenceId, versionId);
    }

    public boolean hasVersion() {
        return versionId != null;
    }
}
