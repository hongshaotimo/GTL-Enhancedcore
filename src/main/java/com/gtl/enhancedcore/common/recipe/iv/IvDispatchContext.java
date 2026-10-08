package com.gtl.enhancedcore.common.recipe.iv;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;

/** Preserves GTLCore's exact smart-order count, including patterns containing only virtual tools. */
public final class IvDispatchContext {
    private record Dispatch(ICraftingProvider provider, IPatternDetails pattern, long operations) {}
    private static final ThreadLocal<Dispatch> CURRENT = new ThreadLocal<>();
    private IvDispatchContext() {}
    public static void set(ICraftingProvider provider, IPatternDetails pattern, long operations) {
        CURRENT.set(new Dispatch(provider, pattern, operations));
    }
    public static long operations(ICraftingProvider provider, IPatternDetails pattern) {
        Dispatch dispatch = CURRENT.get();
        return dispatch != null && dispatch.provider == provider && dispatch.pattern == pattern ? dispatch.operations : -1;
    }
    public static void clear() { CURRENT.remove(); }
}
