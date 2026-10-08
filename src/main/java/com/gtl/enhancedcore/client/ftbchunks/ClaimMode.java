package com.gtl.enhancedcore.client.ftbchunks;

/**
 * FTB Chunks 区块选择模式（移植自 GTLsupb，作者确认可直接照搬）。
 */
public enum ClaimMode {
    FREEHAND("freehand"),
    RECTANGLE("rectangle");

    private final String key;

    ClaimMode(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public ClaimMode next() {
        ClaimMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public String translationKey() {
        return "gtl_enhancedcore.claim_mode." + key;
    }
}
