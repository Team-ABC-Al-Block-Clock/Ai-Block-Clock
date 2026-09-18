package com.aiblockclock.domain.node;

import com.aiblockclock.domain.AbcAmount;
import com.aiblockclock.enums.node.AiModel;

import java.time.Instant;

public record NodeDefinitionSummary(
        NodeDefinitionId id,
        String name,
        AiModel model,
        AbcAmount baseCost,
        Instant createdAt,
        Instant updatedAt
) {
}
