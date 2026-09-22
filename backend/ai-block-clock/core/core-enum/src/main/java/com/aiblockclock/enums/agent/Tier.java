package com.aiblockclock.enums.agent;

import java.math.BigDecimal;

public enum Tier {
    TIER_1("0.50"),

    TIER_2("0.30"),

    TIER_3("0.10"),

    TIER_4("0.00");

    private final BigDecimal premiumRate;

    Tier(String premiumRate) {
        this.premiumRate = new BigDecimal(premiumRate);
    }

    public static Tier initial() {
        return TIER_4;
    }

    public BigDecimal premiumRate() {
        return premiumRate;
    }
}
