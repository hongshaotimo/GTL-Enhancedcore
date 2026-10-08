package com.gtl.enhancedcore.audit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Audit-only observation of the supplied collection traversal and original Map operations.
 * Does not snapshot/replace the map, synchronize game operations, or suppress game exceptions.
 * A production unload fix can supply a snapshot; traversal identity is reported separately.
 * Direct writes through an escaped map are not intercepted: size changes and traversal failures
 * are reported as observations, not attributed to the current callback without a write stack.
 */
public final class ChunkUnloadDiagnostics {
    public static final String PROPERTY = "gtl.enhancedcore.chunkUnloadAudit";
    private static final boolean ENABLED = Boolean.getBoolean(PROPERTY);
    private static final AtomicLong IDS = new AtomicLong();
    private static final Scope[] EMPTY = new Scope[0];

    private final Budget budget;
    private volatile Scope[] active = EMPTY;

    public ChunkUnloadDiagnostics() {
        this(Defaults.BUDGET);
    }

    ChunkUnloadDiagnostics(Budget budget) {
        this.budget = budget;
    }

    public static boolean enabled() {
        return ENABLED;
    }

    /** Keep conditional bytecode out of Mixin 0.8.5 field initialisers. */
    public static ChunkUnloadDiagnostics createIfEnabled() {
        return ENABLED ? new ChunkUnloadDiagnostics() : null;
    }

    /**
     * The volatile, per-chunk scope list lets a writer on another thread see an active unload.
     * Nested/reentrant unloads get independent IDs, and all scopes are removed on exceptional exit.
     */
    public <T> void forEach(Map<?, ?> map, Collection<T> values, Consumer<? super T> action,
                           String phase, Supplier<String> chunk) {
        if (budget.exhausted()) {
            values.forEach(action);
            return;
        }
        Scope scope = new Scope(map, values, phase, chunk, budget);
        add(scope);
        Throwable failure = null;
        try {
            values.forEach(value -> {
                scope.current = value;
                scope.last = value;
                int before = map.size();
                long attempts = scope.attempts.get();
                try {
                    action.accept(value);
                } finally {
                    // A delta without an intercepted write is evidence of a coverage gap, not blame.
                    int after = map.size();
                    if (before != after && attempts == scope.attempts.get()) {
                        scope.observation(before, after);
                    }
                    scope.current = null;
                    scope.callbacks++;
                }
            });
        } catch (RuntimeException | Error original) {
            failure = original;
            throw original;
        } finally {
            remove(scope);
            scope.finish(failure);
        }
    }

    public <K, V> V put(Map<K, V> map, K key, V value, String source) {
        Scope[] scopes = active;
        if (scopes.length == 0) return map.put(key, value);
        Event[] events = before(scopes, map, "PUT", source, key, value);
        V previous;
        try {
            previous = map.put(key, value);
        } catch (RuntimeException | Error original) {
            after(scopes, events, map, null, original);
            throw original;
        }
        after(scopes, events, map, previous, null);
        return previous;
    }

    public <K, V> V remove(Map<K, V> map, Object key, String source) {
        Scope[] scopes = active;
        if (scopes.length == 0) return map.remove(key);
        Event[] events = before(scopes, map, "REMOVE", source, key, null);
        V previous;
        try {
            previous = map.remove(key);
        } catch (RuntimeException | Error original) {
            after(scopes, events, map, null, original);
            throw original;
        }
        after(scopes, events, map, previous, null);
        return previous;
    }

    public void clear(Map<?, ?> map, String source) {
        Scope[] scopes = active;
        if (scopes.length == 0) {
            map.clear();
            return;
        }
        Event[] events = before(scopes, map, "CLEAR", source, null, null);
        try {
            map.clear();
        } catch (RuntimeException | Error original) {
            after(scopes, events, map, null, original);
            throw original;
        }
        after(scopes, events, map, null, null);
    }

    private static Event[] before(Scope[] scopes, Map<?, ?> map, String operation, String source,
                                  Object key, Object value) {
        Event[] events = new Event[scopes.length];
        for (int index = 0; index < scopes.length; index++) {
            if (scopes[index].map == map) {
                events[index] = scopes[index].begin(operation, source, key, value);
            }
        }
        return events;
    }

    private static void after(Scope[] scopes, Event[] events, Map<?, ?> map, Object previous, Throwable failure) {
        for (int index = 0; index < scopes.length; index++) {
            if (scopes[index].map == map) {
                scopes[index].complete(events[index], previous, failure);
            }
        }
    }

    private synchronized void add(Scope scope) {
        Scope[] next = new Scope[active.length + 1];
        System.arraycopy(active, 0, next, 0, active.length);
        next[active.length] = scope;
        active = next;
    }

    private synchronized void remove(Scope scope) {
        Scope[] next = new Scope[active.length - 1];
        int index = 0;
        for (Scope item : active) {
            if (item != scope) next[index++] = item;
        }
        active = next.length == 0 ? EMPTY : next;
    }

    int activeScopes() {
        return active.length;
    }

    private static String identity(Object value) {
        return value == null ? "null"
                : value.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(value));
    }

    private static String thread(Thread thread) {
        return thread.getName() + "#" + thread.threadId();
    }

    private static int setting(String suffix, int fallback, int maximum) {
        return Math.max(1, Math.min(maximum, Integer.getInteger(PROPERTY + suffix, fallback)));
    }

    private static final class Defaults {
        private static final Budget BUDGET = new Budget(
                setting(".events", 32, 256), setting(".reports", 16, 256),
                setting(".failures", 4, 32), setting(".stackDepth", 32, 96),
                message -> org.apache.logging.log4j.LogManager
                        .getLogger("EnhancedcoreChunkUnloadAudit").warn(message));
    }

    /** A process-wide budget, shared by all chunk trackers; failure reports have reserved capacity. */
    static final class Budget {
        final int events;
        final int reports;
        final int failures;
        final int stackDepth;
        final Consumer<String> sink;
        final AtomicInteger normalReports = new AtomicInteger();
        final AtomicInteger failureReports = new AtomicInteger();
        final AtomicInteger loggingFailures = new AtomicInteger();

        Budget(int events, int reports, int failures, int stackDepth, Consumer<String> sink) {
            this.events = events;
            this.reports = reports;
            this.failures = failures;
            this.stackDepth = stackDepth;
            this.sink = sink;
        }

        boolean acquire(boolean failure) {
            AtomicInteger counter = failure ? failureReports : normalReports;
            int limit = failure ? failures : reports;
            for (int current = counter.get(); current < limit; current = counter.get()) {
                if (counter.compareAndSet(current, current + 1)) return true;
            }
            return false;
        }

        boolean exhausted() {
            return normalReports.get() >= reports && failureReports.get() >= failures;
        }
    }

    private static final class Event {
        final String entry;
        volatile String result = "PENDING (callsite entered; result not yet recorded)";

        Event(String entry) {
            this.entry = entry;
        }
    }

    private static final class Scope {
        final long id = IDS.incrementAndGet();
        final long started = System.nanoTime();
        final Map<?, ?> map;
        final String traversal;
        final int initialSize;
        final String phase;
        final Supplier<String> chunk;
        final Budget budget;
        final Thread owner = Thread.currentThread();
        final AtomicLong attempts = new AtomicLong();
        final List<Event> events = new ArrayList<>();
        volatile Object current;
        Object last;
        long callbacks;
        long returned;
        long threw;
        long observations;
        long dropped;

        Scope(Map<?, ?> map, Collection<?> values, String phase, Supplier<String> chunk, Budget budget) {
            this.map = map;
            this.traversal = identity(values);
            this.initialSize = map.size();
            this.phase = phase;
            this.chunk = chunk;
            this.budget = budget;
        }

        synchronized Event begin(String operation, String source, Object key, Object value) {
            attempts.incrementAndGet();
            if (events.size() >= budget.events) {
                dropped++;
                return null;
            }
            try {
                Thread writer = Thread.currentThread();
                StringBuilder event = new StringBuilder(512)
                        .append(operation).append(" source=").append(source)
                        .append(" writer=").append(thread(writer))
                        .append(" sameThread=").append(writer == owner)
                        .append(" key=").append(key)
                        .append(" value=").append(identity(value))
                        .append(" sizeBeforeObserved=").append(map.size())
                        .append(" callback=").append(identity(current))
                        .append(" elapsedNs=").append(System.nanoTime() - started);
                StackTraceElement[] stack = writer.getStackTrace();
                int count = 0;
                for (StackTraceElement frame : stack) {
                    if (frame.getClassName().equals(Thread.class.getName())
                            || frame.getClassName().equals(ChunkUnloadDiagnostics.class.getName())
                            || frame.getClassName().startsWith(ChunkUnloadDiagnostics.class.getName() + "$")) continue;
                    if (count++ == budget.stackDepth) {
                        event.append("\n    ... stack truncated");
                        break;
                    }
                    event.append("\n    at ").append(frame);
                }
                Event entry = new Event(event.toString());
                events.add(entry);
                return entry;
            } catch (RuntimeException diagnosticFailure) {
                budget.loggingFailures.incrementAndGet();
                dropped++;
                return null;
            }
        }

        synchronized void complete(Event event, Object previous, Throwable failure) {
            if (failure == null) returned++;
            else threw++;
            if (event != null) {
                try {
                    event.result = (failure == null ? "RETURNED previous=" + identity(previous)
                            : "THREW original=" + identity(failure))
                            + " sizeAfterObserved=" + map.size();
                } catch (RuntimeException diagnosticFailure) {
                    budget.loggingFailures.incrementAndGet();
                }
            }
        }

        synchronized void observation(int before, int after) {
            observations++;
            if (events.size() >= budget.events) {
                dropped++;
                return;
            }
            Event event = new Event("UNATTRIBUTED_SIZE_CHANGE sizeObserved=" + before + "->" + after
                    + " observer=" + thread(Thread.currentThread())
                    + " callback=" + identity(current)
                    + " writer=UNKNOWN (callback association is not writer attribution)");
            event.result = "OBSERVATION_ONLY";
            events.add(event);
        }

        synchronized void finish(Throwable failure) {
            if (attempts.get() == 0 && observations == 0 && failure == null) return;
            if (!budget.acquire(failure != null)) return;
            // Only diagnostic formatting/output is isolated here, never a game operation or callback.
            try {
                StringBuilder report = new StringBuilder(1024)
                        .append("[chunk-unload-audit] phaseId=").append(id)
                        .append(" chunk=").append(chunk.get())
                        .append(" phase=").append(phase)
                        .append(" owner=").append(thread(owner))
                        .append(" map=").append(identity(map))
                        .append(" traversal=").append(traversal)
                        .append(" sizeObserved=").append(initialSize).append("->").append(map.size())
                        .append(" callbacks=").append(callbacks)
                        .append(" lastCallback=").append(identity(last))
                        .append(" attempts=").append(attempts.get())
                        .append(" returned=").append(returned)
                        .append(" threw=").append(threw)
                        .append(" unattributedSizeChanges=").append(observations)
                        .append(" droppedEvents=").append(dropped)
                        .append(" failure=").append(identity(failure))
                        .append("\ncoverage=LevelChunk getBlockEntity.removed/removeBlockEntity/")
                        .append("setBlockEntity/clearAllBlockEntities map callsites only; ")
                        .append("direct escaped-map writes and other injected callsites are NOT intercepted; ")
                        .append("PENDING is not proof of a completed write; ")
                        .append("size is an observation, not an atomic structural-change test.");
                for (int index = 0; index < events.size(); index++) {
                    Event event = events.get(index);
                    report.append("\n  event[").append(index).append("] ")
                            .append(event.entry).append("\n    result=").append(event.result);
                }
                if (failure != null) {
                    report.append("\n  originalFailure=").append(failure.getClass().getName());
                    StackTraceElement[] stack = failure.getStackTrace();
                    for (int index = 0; index < Math.min(stack.length, budget.stackDepth); index++) {
                        report.append("\n    at ").append(stack[index]);
                    }
                }
                report.append("\nreportBudget normal=").append(budget.normalReports.get())
                        .append('/').append(budget.reports)
                        .append(" failures=").append(budget.failureReports.get())
                        .append('/').append(budget.failures)
                        .append(" diagnosticOutputFailures=").append(budget.loggingFailures.get())
                        .append(" (further reports stop at the corresponding limit)");
                budget.sink.accept(report.toString());
            } catch (RuntimeException diagnosticFailure) {
                budget.loggingFailures.incrementAndGet();
            }
        }
    }
}
