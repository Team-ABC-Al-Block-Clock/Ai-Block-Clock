package com.aiblockclock.domain.node;

import com.aiblockclock.domain.AbcAmount;
import com.aiblockclock.enums.node.AiModel;
import com.aiblockclock.enums.node.NodeType;

public record NodeSpec(String name, NodeType nodeType, AiModel model, AbcAmount baseCost) {
}
