package com.gtl.enhancedcore.common.performance.structure;

/** Per-controller scheduling only; contains no world, inventory or recipe references. */
public final class StructureSearchBackoff {
    private long revision;
    private long retryAt;
    private int failures;
    private boolean delayed;
    private boolean searching;
    private boolean pending;
    private boolean available = true;
    private boolean requiresFormation;

    public synchronized long begin(long now) {
        if (!available || searching || pending || (delayed && now - retryAt < 0)) return -1;
        searching = true;
        requiresFormation = true;
        return revision;
    }

    public synchronized void wake() {
        revision++;
        failures = 0;
        delayed = false;
    }

    public synchronized void load() { available = true; searching = false; requiresFormation = true; wake(); }
    public synchronized void unload() { available = false; wake(); }

    /** Matcher success clears its error before the server thread restores machine capabilities. */
    public synchronized boolean requiresFormation() { return requiresFormation; }

    /** Called by the actual native callback, including a successful manual/event-driven rebuild. */
    public synchronized void formationCompleted() { requiresFormation = false; wake(); }

    public synchronized boolean current(long token) { return available && revision == token; }
    public synchronized void lockBusy() { searching = false; }

    public synchronized void failed(long now, long token) {
        searching = false;
        if (!available || revision != token) return;
        failures = Math.min(4, failures + 1);
        long seconds = Math.min(10, 1L << failures);
        retryAt = now + seconds * 1_000_000_000L;
        delayed = true;
    }

    public synchronized boolean queue(long token) {
        searching = false;
        if (!available || revision != token || pending) return false;
        pending = true;
        return true;
    }

    public synchronized void completed() { pending = false; }
    public synchronized int failures() { return failures; }
}
