package com.gtl.enhancedcore.common.recipe.iv;

/** Limits maintenance of a proven empty order engine; never retains orders or machine parts. */
public final class IvIdleOrderPolling {
    private static final long INTERVAL = 20L;
    private boolean initialized;
    private boolean workingEnabled;
    private long lastObservedTick;
    private long lastFullTick;

    /** The caller must inspect every current buffer's complete order list before passing true. */
    public boolean shouldRun(long tick, boolean allOrdersEmpty, boolean enabled) {
        if (!allOrdersEmpty) {
            reset();
            return true;
        }
        boolean run = !initialized || enabled != workingEnabled || tick < lastObservedTick;
        if (!run) {
            long elapsed = tick - lastFullTick;
            run = elapsed < 0L || elapsed >= INTERVAL;
        }
        initialized = true;
        workingEnabled = enabled;
        lastObservedTick = tick;
        if (run) lastFullTick = tick;
        return run;
    }

    /** Availability, mode and load/reset transitions must revisit binding and diagnostics immediately. */
    public void reset() { initialized = false; }
}
