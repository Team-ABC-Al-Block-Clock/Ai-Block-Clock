package com.aiblockclock.domain.node;

import com.aiblockclock.domain.Timestamps;

public record NodeDefinitionSummary(NodeDefinitionId id, NodeSpec spec, Timestamps timestamps) {
}
