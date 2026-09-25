package com.aiblockclock.domain.agent;

import com.aiblockclock.domain.AbcAmount;
import com.aiblockclock.enums.node.NodeType;
import com.aiblockclock.enums.workflow.SourceType;

import java.util.UUID;

public record PlaceableItem(
        UUID itemId,
        SourceType itemType,
        String name,
        NodeType nodeType,
        boolean mine,
        String ownerNickname,
        AbcAmount price,
        String summary
) {
}
