package com.aiblockclock.domain.agent;

import com.aiblockclock.domain.AbcAmount;

import java.util.UUID;

public record PlaceableItem(
        UUID itemId,
        String name,
        boolean mine,
        String ownerNickname,
        AbcAmount price,
        String summary
) {
}
