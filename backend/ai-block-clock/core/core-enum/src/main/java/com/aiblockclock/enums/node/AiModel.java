package com.aiblockclock.enums.node;

public enum AiModel {
    GEMINI_2_5_FLASH("gemini-2.5-flash"),
    GEMINI_2_5_PRO("gemini-2.5-pro");

    private final String key;

    AiModel(String key) {
        this.key = key;
    }

    public static AiModel defaultModel() {
        return GEMINI_2_5_FLASH;
    }

    public String key() {
        return key;
    }
}
