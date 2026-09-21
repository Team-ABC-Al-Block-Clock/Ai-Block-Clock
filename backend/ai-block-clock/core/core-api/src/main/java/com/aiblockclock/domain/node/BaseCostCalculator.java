package com.aiblockclock.domain.node;

import com.aiblockclock.domain.AbcAmount;
import com.aiblockclock.enums.node.AiModel;

public interface BaseCostCalculator {
    AbcAmount calculate(Prompt prompt, AiModel model);
}
