package com.gtl.enhancedcore.client.ftbchunks;

/** FTB Chunks claim selection mode. */
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
