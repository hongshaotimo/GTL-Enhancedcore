package com.gtl.enhancedcore.common.recipe.iv;

/** Name ownership belongs to the buffer and survives controller reformation. */
public interface SuperBufferNameAccess {
    String enhanced$getAutomaticName();
    boolean enhanced$isManualName();
    void enhanced$setAutomaticName(String name);
    boolean enhanced$restoreAutomaticName();
}
