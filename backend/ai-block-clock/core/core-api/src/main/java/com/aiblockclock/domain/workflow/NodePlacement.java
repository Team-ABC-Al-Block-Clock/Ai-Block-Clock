package com.aiblockclock.domain.workflow;

import com.aiblockclock.enums.workflow.SourceType;

import java.util.UUID;

public record NodePlacement(NodeKey key, SourceType sourceType, UUID sourceId) {
}
