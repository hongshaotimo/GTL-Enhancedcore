package com.gtl.enhancedcore.client.performance.bloom;

import java.util.Collection;
import java.util.function.Predicate;

/** Coalesces repeated invalidation sweeps; confined to the owning client/render thread. */
public final class DeferredQueuePruner<E> {
    public static final int MAX_DEFERRED_REQUESTS = 1024;
    private Predicate<? super E> pending;
    private int requests;

    public void defer(Collection<E> queue, Predicate<? super E> predicate) {
        // The target uses one non-capturing !entry.valid predicate. Preserve correctness if
        // a different predicate is ever supplied rather than silently dropping its sweep.
        if (pending != null && pending != predicate) flush(queue);
        if (!queue.isEmpty()) {
            pending = predicate;
            // World rendering can be suspended while chunk events keep arriving.
            // Bound request retention even with no frame, service or count callback.
            if (++requests >= MAX_DEFERRED_REQUESTS) flush(queue);
        }
    }

    public void flush(Collection<E> queue) {
        if (pending == null) return;
        queue.removeIf(pending);
        pending = null;
        requests = 0;
    }

    public void reset() {
        pending = null;
        requests = 0;
    }
}
