package com.aiblockclock.enums.agent;

import java.math.BigDecimal;

public enum Tier {
    TIER_1("0.50", 1),

    TIER_2("0.30", 2),

    TIER_3("0.10", 3),

    TIER_4("0.00", 4);

    private final BigDecimal premiumRate;

    private final int level;

    Tier(String premiumRate, int level) {
        this.premiumRate = new BigDecimal(premiumRate);
        this.level = level;
    }

    public static Tier initial() {
        return TIER_4;
    }

    public BigDecimal premiumRate() {
        return premiumRate;
    }

    public int level() {
        return level;
    }
}
