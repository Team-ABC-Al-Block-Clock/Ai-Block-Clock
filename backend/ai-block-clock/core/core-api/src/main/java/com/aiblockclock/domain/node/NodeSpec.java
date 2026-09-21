package com.aiblockclock.domain.node;

import com.aiblockclock.domain.AbcAmount;
import com.aiblockclock.enums.node.AiModel;

public record NodeSpec(String name, AiModel model, AbcAmount baseCost) {
}
