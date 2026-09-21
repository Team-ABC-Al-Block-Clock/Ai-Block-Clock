package com.aiblockclock.domain.agent;

import com.aiblockclock.domain.AbcAmount;

public record VersionCost(int promptNodeCount, AbcAmount baseCost) {
}
