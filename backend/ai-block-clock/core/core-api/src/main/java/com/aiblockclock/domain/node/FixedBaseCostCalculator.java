package com.aiblockclock.domain.node;

import com.aiblockclock.domain.AbcAmount;
import com.aiblockclock.enums.node.AiModel;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class FixedBaseCostCalculator implements BaseCostCalculator {
    private static final AbcAmount FIXED = AbcAmount.of(new BigDecimal("3.000000"));

    @Override
    public AbcAmount calculate(Prompt prompt, AiModel model) {
        return FIXED;
    }
}
