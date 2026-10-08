package com.gtl.enhancedcore.common.recipe.iv;

/** Runtime contract added to GTLCore pattern-buffer machines by the lifecycle mixin. */
public interface IvBufferAccess {
    IvBufferState iv$getState();
    boolean iv$isDedicatedDisplay();
    void iv$syncDedicated(boolean dedicated);

    /** Buffers without FOA need no override; implementations with FOA must report its actual state. */
    default boolean iv$isFoaEnabled() { return false; }
}
