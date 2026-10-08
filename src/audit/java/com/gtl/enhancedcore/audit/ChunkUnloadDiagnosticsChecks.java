package com.gtl.enhancedcore.audit;

import com.gtl.enhancedcore.common.util.BlockEntityRemovalSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Standalone JDK execution checks and separately invoked, opt-in nested game probes. */
public final class ChunkUnloadDiagnosticsChecks {
    private static int assertions;

    private ChunkUnloadDiagnosticsChecks() {}

    public static void main(String[] args) throws InterruptedException {
        check(ChunkUnloadDiagnostics.enabled() == Boolean.getBoolean(ChunkUnloadDiagnostics.PROPERTY),
                "opt-in property");
        ChunkUnloadDiagnostics created = ChunkUnloadDiagnostics.createIfEnabled();
        check((created != null) == ChunkUnloadDiagnostics.enabled(), "factory respects opt-in property");
        check(!ChunkUnloadDiagnostics.enabled() || created != ChunkUnloadDiagnostics.createIfEnabled(),
                "factory creates an independent tracker per chunk");
        if (args.length == 1 && args[0].equals("property-only")) {
            System.out.println("ChunkUnloadDiagnosticsChecks: " + assertions + " assertions passed");
            return;
        }
        quietNormalTraversal();
        stableSnapshotObservation();
        mutationAndCme();
        replacementAndNoop();
        concurrentWriter();
        pendingConcurrentWriter();
        nestedAndOtherMap();
        uninstrumentedChange();
        preservedFailures();
        operationAndTraversalIdentity();
        diagnosticFormattingFailure();
        budgetsAndCleanup();
        System.out.println("ChunkUnloadDiagnosticsChecks: " + assertions + " assertions passed");
    }

    private static void quietNormalTraversal() {
        List<String> reports = new ArrayList<>();
        ChunkUnloadDiagnostics probe = probe(reports, 8, 8, 2);
        Map<Integer, Object> map = new HashMap<>();
        Object entity = new Object();
        check(probe.put(map, 1, entity, "outside") == null, "outside put return");
        check(probe.remove(map, 1, "outside") == entity, "outside remove return");
        map.put(1, entity);
        int[] count = {0};
        probe.forEach(map, map.values(), value -> count[0]++, "onChunkUnloaded", () -> "test");
        probe.forEach(map, map.values(), value -> count[0]++, "setRemoved", () -> "test");
        probe.clear(map, "normal final cleanup");
        check(count[0] == 2, "callbacks exactly once in each phase");
        check(map.isEmpty(), "normal clear unchanged");
        check(reports.isEmpty(), "no normal traversal or out-of-scope logging");
        check(probe.activeScopes() == 0, "normal cleanup");
    }

    private static void stableSnapshotObservation() {
        List<String> reports = new ArrayList<>();
        ChunkUnloadDiagnostics probe = probe(reports, 8, 8, 2);
        Map<Integer, Object> map = new HashMap<>();
        map.put(1, new Object());
        map.put(2, new Object());
        AtomicReference<List<Object>> unloaded = new AtomicReference<>();
        int[] calls = {0};
        probe.forEach(map, BlockEntityRemovalSnapshot.forUnload(map, unloaded::set), value -> {
            calls[0]++;
            probe.remove(map, 1, "stable unload remove");
        }, "onChunkUnloaded", () -> "test:stable");
        probe.forEach(map, BlockEntityRemovalSnapshot.forRemoval(map, unloaded.get()), value -> {
            calls[0]++;
            probe.remove(map, 2, "stable removal remove");
        }, "setRemoved", () -> "test:stable");
        check(calls[0] == 4, "stable snapshots retain both callbacks for both original entities");
        check(map.isEmpty(), "stable traversal retains original map writes");
        check(reports.size() == 2, "both stable traversal phases report map writes");
        check(reports.get(0).contains("traversal=" + BlockEntityRemovalSnapshot.class.getName()
                + "$UnloadValues@"), "snapshot traversal identity is explicit");
        check(reports.get(1).contains("phase=setRemoved"), "stable second phase retains attribution");
        for (String report : reports) {
            check(report.contains("sameThread=true"), "stable traversal preserves writer thread attribution");
            check(!report.contains("originalFailure="), "successful stable traversal is not reported as CME");
        }
        check(probe.activeScopes() == 0, "stable traversal removes all diagnostic scopes");
    }

    private static void mutationAndCme() {
        List<String> reports = new ArrayList<>();
        ChunkUnloadDiagnostics probe = probe(reports, 8, 8, 2);
        Map<Integer, Object> map = new HashMap<>();
        Object original = new Object();
        Object added = new Object();
        map.put(1, original);
        expectCme(() -> probe.forEach(map, map.values(), value -> {
            if (value != original) return;
            check(probe.remove(map, 1, "getBlockEntity.removed/m_5685_") == original,
                    "remove exact previous identity");
            check(probe.put(map, 2, added, "setBlockEntity/m_142169_") == null,
                    "put exact previous identity");
        }, "setRemoved", () -> "test:1,2"));
        check(map.size() == 1 && map.get(2) == added, "same-size remove/insert remains visible");
        String report = only(reports);
        check(report.contains("source=getBlockEntity.removed/m_5685_"), "remove source captured");
        check(report.contains("source=setBlockEntity/m_142169_"), "insert source captured");
        check(report.contains("sameThread=true"), "same-thread reentrancy");
        check(report.contains("java.lang.Object@"), "entity class and identity");
        check(report.contains("phase=setRemoved"), "second pass label");
        check(report.contains("originalFailure=java.util.ConcurrentModificationException"), "CME retained");
        check(report.substring(0, report.indexOf("originalFailure="))
                .contains("ChunkUnloadDiagnosticsChecks.lambda$"), "writer caller stack");
        check(probe.activeScopes() == 0, "CME scope removed");
    }

    private static void replacementAndNoop() {
        List<String> reports = new ArrayList<>();
        ChunkUnloadDiagnostics probe = probe(reports, 8, 8, 2);
        Map<Integer, Object> map = new HashMap<>();
        Object old = new Object();
        Object replacement = new Object();
        map.put(1, old);
        probe.forEach(map, map.values(), value -> {
            check(probe.put(map, 1, replacement, "replace") == old, "replacement previous");
            check(probe.remove(map, 99, "missing") == null, "missing remove return");
        }, "setRemoved", () -> "test");
        check(map.get(1) == replacement, "replacement preserved without artificial CME");
        String report = only(reports);
        check(report.contains("sizeObserved=1->1"), "replacement is not asserted structural");
        check(report.contains("failure=null"), "no fabricated failure");
    }

    private static void concurrentWriter() throws InterruptedException {
        List<String> reports = new ArrayList<>();
        ChunkUnloadDiagnostics probe = probe(reports, 8, 8, 2);
        Map<Integer, Object> map = new HashMap<>();
        map.put(1, new Object());
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread writer = new Thread(() -> {
            try {
                probe.remove(map, 1, "worker.removeBlockEntity");
            } catch (Throwable thrown) {
                failure.set(thrown);
            }
        }, "audit-test-writer");
        expectCme(() -> probe.forEach(map, map.values(), value -> {
            writer.start();
            try {
                writer.join(5000);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AssertionError(interrupted);
            }
            check(!writer.isAlive(), "writer is not blocked by traversal");
        }, "setRemoved", () -> "test"));
        writer.join();
        check(failure.get() == null, "writer completed");
        String report = only(reports);
        check(report.contains("writer=audit-test-writer#"), "writer name and ID");
        check(report.contains("sameThread=false"), "cross-thread attribution");
        check(probe.activeScopes() == 0, "cross-thread cleanup");
    }

    private static void nestedAndOtherMap() {
        List<String> reports = new ArrayList<>();
        ChunkUnloadDiagnostics probe = probe(reports, 8, 8, 2);
        Map<Integer, Object> map = new HashMap<>();
        Map<Integer, Object> other = new HashMap<>();
        Object entity = new Object();
        map.put(1, entity);
        probe.forEach(map, map.values(), value -> {
            probe.put(other, 1, entity, "pending NBT excluded");
            probe.forEach(map, map.values(), nested -> probe.put(map, 1, entity, "nested"),
                    "onChunkUnloaded", () -> "nested");
            check(probe.activeScopes() == 1, "outer scope restored");
        }, "setRemoved", () -> "outer");
        check(reports.size() == 2, "nested write observed by both scopes");
        check(reports.stream().noneMatch(report -> report.contains("pending NBT excluded")),
                "map identity excludes other collections");
        check(probe.activeScopes() == 0, "nested cleanup");
        reports.clear();
        expectCme(() -> probe.forEach(map, map.values(),
                value -> probe.clear(map, "nested clear"), "setRemoved", () -> "test"));
        check(only(reports).contains("CLEAR source=nested clear"), "nested clear observed");
    }

    private static void pendingConcurrentWriter() throws InterruptedException {
        List<String> reports = new ArrayList<>();
        ChunkUnloadDiagnostics probe = probe(reports, 8, 8, 2);
        CountDownLatch changed = new CountDownLatch(1);
        CountDownLatch allowReturn = new CountDownLatch(1);
        Map<Integer, Object> map = new HashMap<>() {
            private static final long serialVersionUID = 1L;

            @Override
            public Object remove(Object key) {
                Object previous = super.remove(key);
                changed.countDown();
                await(allowReturn);
                return previous;
            }
        };
        map.put(1, new Object());
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread writer = new Thread(() -> {
            try {
                probe.remove(map, 1, "pending worker remove");
            } catch (Throwable thrown) {
                failure.set(thrown);
            }
        }, "audit-pending-writer");
        try {
            expectCme(() -> probe.forEach(map, map.values(), value -> {
                writer.start();
                await(changed);
            }, "setRemoved", () -> "test"));
            String report = only(reports);
            check(report.contains("source=pending worker remove"), "writer entry captured before return");
            check(report.contains("result=PENDING"), "in-flight operation explicitly marked");
            check(report.contains("attempts=1 returned=0"), "pending is not reported as completed");
            check(report.contains("writer=audit-pending-writer#"), "pending writer identity captured");
        } finally {
            allowReturn.countDown();
            writer.join(5000);
        }
        check(!writer.isAlive() && failure.get() == null, "pending writer finishes without retry");
        check(probe.activeScopes() == 0, "pending writer does not keep unload scope active");
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) throw new AssertionError("diagnostic test timed out");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }

    private static void uninstrumentedChange() {
        List<String> reports = new ArrayList<>();
        ChunkUnloadDiagnostics probe = probe(reports, 8, 8, 2);
        Map<Integer, Object> map = new HashMap<>();
        map.put(1, new Object());
        expectCme(() -> probe.forEach(map, map.values(), value -> map.clear(),
                "setRemoved", () -> "test"));
        String report = only(reports);
        check(report.contains("UNATTRIBUTED_SIZE_CHANGE"), "direct write coverage gap");
        check(report.contains("writer=UNKNOWN"), "no false callback attribution");
        check(report.contains("attempts=0"), "no invented intercepted writer");
    }

    private static void preservedFailures() {
        List<String> reports = new ArrayList<>();
        ChunkUnloadDiagnostics probe = probe(reports, 8, 8, 2);
        Map<Integer, Object> map = Map.of(1, new Object());
        RuntimeException sentinel = new RuntimeException("sentinel");
        try {
            probe.forEach(map, map.values(), value -> { throw sentinel; }, "setRemoved", () -> "test");
            throw new AssertionError("expected callback exception");
        } catch (RuntimeException caught) {
            check(caught == sentinel, "same callback exception object propagated");
        }
        AssertionError error = new AssertionError("sentinel error");
        try {
            probe.forEach(map, map.values(), value -> { throw error; }, "setRemoved", () -> "test");
            throw new AssertionError("expected callback error");
        } catch (AssertionError caught) {
            check(caught == error, "same callback error object propagated");
        }
        check(probe.activeScopes() == 0, "all exceptional scopes removed");
        ChunkUnloadDiagnostics.Budget brokenOutput = new ChunkUnloadDiagnostics.Budget(
                2, 1, 1, 8, message -> { throw new IllegalStateException("diagnostic sink"); });
        ChunkUnloadDiagnostics broken = new ChunkUnloadDiagnostics(brokenOutput);
        try {
            broken.forEach(map, map.values(), value -> { throw sentinel; }, "setRemoved", () -> "test");
            throw new AssertionError("expected original exception with broken sink");
        } catch (RuntimeException caught) {
            check(caught == sentinel, "diagnostic output cannot mask game exception");
        }
        check(brokenOutput.loggingFailures.get() == 1, "diagnostic-only failure counted");
    }

    private static void budgetsAndCleanup() {
        List<String> reports = new ArrayList<>();
        ChunkUnloadDiagnostics.Budget budget = new ChunkUnloadDiagnostics.Budget(2, 1, 1, 8, reports::add);
        ChunkUnloadDiagnostics probe = new ChunkUnloadDiagnostics(budget);
        Map<Integer, Object> map = new HashMap<>();
        Object entity = new Object();
        map.put(1, entity);
        probe.forEach(map, map.values(), value -> {
            for (int index = 0; index < 10; index++) probe.put(map, 1, entity, "repeat");
        }, "setRemoved", () -> "test");
        check(only(reports).contains("droppedEvents=8"), "bounded per-phase events");
        ChunkUnloadDiagnostics otherChunk = new ChunkUnloadDiagnostics(budget);
        otherChunk.forEach(map, map.values(), value -> otherChunk.put(map, 1, entity, "capped"),
                "setRemoved", () -> "other");
        check(reports.size() == 1, "process-wide normal report budget");
        expectCme(() -> probe.forEach(map, map.values(), value -> probe.clear(map, "failure"),
                "setRemoved", () -> "test"));
        check(reports.size() == 2, "failure report capacity reserved after normal cap");
        map.put(1, entity);
        expectCme(() -> probe.forEach(map, map.values(), value -> probe.clear(map, "capped failure"),
                "setRemoved", () -> "test"));
        check(reports.size() == 2, "failure budget enforced without swallowing CME");
        check(probe.activeScopes() == 0 && otherChunk.activeScopes() == 0, "budget cleanup");
    }

    private static void operationAndTraversalIdentity() {
        List<String> reports = new ArrayList<>();
        ChunkUnloadDiagnostics probe = probe(reports, 8, 8, 2);
        CountingMap map = new CountingMap();
        Object entity = new Object();
        map.put(1, entity);
        int[] traversals = {0};
        int[] callbacks = {0};
        AbstractCollection<Object> originalValues = new AbstractCollection<>() {
            @Override
            public Iterator<Object> iterator() {
                throw new AssertionError("probe must not replace original forEach with an iterator or snapshot");
            }

            @Override
            public int size() {
                return map.size();
            }

            @Override
            public void forEach(Consumer<? super Object> action) {
                traversals[0]++;
                map.values().forEach(action);
            }
        };
        probe.forEach(map, originalValues, value -> {
            callbacks[0]++;
            check(probe.put(map, 1, entity, "replace") == entity, "active put exact return");
            check(probe.remove(map, 99, "missing") == null, "active remove exact return");
        }, "setRemoved", () -> "test");
        check(traversals[0] == 1 && callbacks[0] == 1, "original forEach and callback called once");
        check(map.putCalls == 2 && map.removeCalls == 1, "each original map operation called once");
        RuntimeException sentinel = new RuntimeException("map operation sentinel");
        map.removeFailure = sentinel;
        try {
            probe.forEach(map, originalValues, value -> probe.remove(map, 1, "throwing remove"),
                    "setRemoved", () -> "test");
            throw new AssertionError("expected original map exception");
        } catch (RuntimeException caught) {
            check(caught == sentinel, "same map operation exception propagated");
        }
        check(map.removeCalls == 2, "failed operation not retried");
        check(map.get(1) == entity, "failed operation did not erase entry");
        check(probe.activeScopes() == 0, "failed operation cleanup");
    }

    private static void diagnosticFormattingFailure() {
        List<String> reports = new ArrayList<>();
        ChunkUnloadDiagnostics.Budget budget = new ChunkUnloadDiagnostics.Budget(8, 8, 2, 32, reports::add);
        ChunkUnloadDiagnostics probe = new ChunkUnloadDiagnostics(budget);
        Map<Object, Object> map = new HashMap<>();
        Object key = new Object() {
            @Override
            public String toString() {
                throw new IllegalStateException("diagnostic key formatting");
            }
        };
        Object entity = new Object();
        map.put(key, entity);
        probe.forEach(map, map.values(), value -> {
            check(probe.put(map, key, entity, "formatting failure") == entity,
                    "diagnostic formatting cannot change original return");
        }, "setRemoved", () -> "test");
        check(budget.loggingFailures.get() == 1, "formatting failure accounted for");
        check(only(reports).contains("droppedEvents=1"), "formatting failure visible");
        check(probe.activeScopes() == 0, "formatting failure cleanup");
    }

    public static final class RuntimeChecks {
        private int assertions;

        private RuntimeChecks() {}

        public static int run(Object world) throws ReflectiveOperationException {
            return ((Number) runResults(world).get("assertions")).intValue();
        }

        public static Map<String, Object> runResults(Object world) throws ReflectiveOperationException {
            if (!Boolean.getBoolean("gtl.enhancedcore.chunkUnloadProbe")) {
                throw new IllegalStateException("The temporary-chunk probe requires explicit opt-in");
            }
            if (!(world instanceof ServerLevel level) || !level.getServer().isSameThread()) {
                throw new IllegalArgumentException("The chunk unload probe runs on the server thread only");
            }
            if (!ChunkUnloadDiagnostics.enabled()) {
                throw new IllegalStateException("Enable chunkUnloadAudit to capture the control writer stack");
            }
            RuntimeChecks checks = new RuntimeChecks();
            checks.originalControl(level);
            for (int size = 1; size <= 16; size++) {
                checks.selfRemoval(level, size);
                checks.firstPhaseMutation(level, size);
            }
            checks.neighborRemoval(level);
            checks.nestedUnload(level, false);
            checks.nestedUnload(level, true);
            checks.callbackFailure(level, false);
            checks.callbackFailure(level, true);
            checks.emptyAndRepeated(level);
            checks.tickerRebinding(level);
            return Map.of("assertions", checks.assertions,
                    "originalControlCmeConfirmed", true,
                    "productionSnapshotChecksPassed", true,
                    "tickerNullBindingVerified", true,
                    "tickerRebindingVerified", true,
                    "tickerRebindingCycles", 8);
        }

        private void originalControl(ServerLevel level) throws ReflectiveOperationException {
            RuntimeFixture fixture = new RuntimeFixture(level, 1);
            ProbeBlockEntity entity = fixture.entities.get(0);
            entity.removal = () -> fixture.chunk.getBlockEntity(entity.getBlockPos(),
                    LevelChunk.EntityCreationType.CHECK);
            Field trackerField = null;
            for (Field field : LevelChunk.class.getDeclaredFields()) {
                if (field.getType() == ChunkUnloadDiagnostics.class) trackerField = field;
            }
            verify(trackerField != null, "The runtime unload audit Mixin is installed");
            trackerField.setAccessible(true);
            ChunkUnloadDiagnostics tracker = (ChunkUnloadDiagnostics) trackerField.get(fixture.chunk);
            verify(tracker != null, "The runtime audit tracker is enabled");
            try {
                tracker.forEach(fixture.chunk.getBlockEntities(), fixture.chunk.getBlockEntities().values(),
                        BlockEntity::setRemoved, "setRemoved-vanilla-control", () -> "detached-control:0,0");
                throw new AssertionError("The original real-chunk map traversal must throw CME");
            } catch (ConcurrentModificationException expected) {
                verify(expected.getStackTrace()[0].getClassName().equals("java.util.HashMap$Values"),
                        "The runtime control reproduces HashMap.values.forEach CME");
            }
            verify(entity.isRemoved() && entity.removalCalls == 1,
                    "The control invokes the real BlockEntity setRemoved before its lookup");
            verify(fixture.chunk.getBlockEntities().isEmpty(),
                    "The real getBlockEntity(CHECK) removes the current removed entity");
            verify(tracker.activeScopes() == 0, "The original runtime CME cleans up its audit scope");
            fixture.chunk.clearAllBlockEntities();
            verifyReleased(fixture.chunk);
        }

        private void selfRemoval(ServerLevel level, int size) throws ReflectiveOperationException {
            RuntimeFixture fixture = new RuntimeFixture(level, size);
            for (ProbeBlockEntity entity : fixture.entities) {
                entity.removal = () -> fixture.chunk.getBlockEntity(entity.getBlockPos(),
                        LevelChunk.EntityCreationType.CHECK);
            }
            fixture.chunk.clearAllBlockEntities();
            verifyCompleted(fixture, 1, 1);
            fixture.chunk.clearAllBlockEntities();
            verifyCompleted(fixture, 1, 1);
        }

        private void firstPhaseMutation(ServerLevel level, int size) throws ReflectiveOperationException {
            RuntimeFixture fixture = new RuntimeFixture(level, size);
            ProbeBlockEntity replacement = new ProbeBlockEntity(new BlockPos(0, 64, 0));
            fixture.entities.get(0).unload = () -> {
                fixture.chunk.getBlockEntities().clear();
                fixture.chunk.getBlockEntities().put(replacement.getBlockPos(), replacement);
            };
            fixture.chunk.clearAllBlockEntities();
            verifyCompleted(fixture, 1, 1);
            verify(replacement.isRemoved() && replacement.removalCalls == 1,
                    "First-phase replacement is also disposed");
            verify(replacement.unloadCalls == 0, "Replacement notification follows the original phase order");
        }

        private void neighborRemoval(ServerLevel level) throws ReflectiveOperationException {
            RuntimeFixture fixture = new RuntimeFixture(level, 16);
            for (int index = 0; index < fixture.entities.size(); index++) {
                ProbeBlockEntity neighbor = fixture.entities.get((index + 1) % fixture.entities.size());
                fixture.entities.get(index).removal = () ->
                        fixture.chunk.getBlockEntities().remove(neighbor.getBlockPos());
            }
            fixture.chunk.clearAllBlockEntities();
            verifyCompleted(fixture, 1, 1);
        }

        private void nestedUnload(ServerLevel level, boolean duringRemoval) throws ReflectiveOperationException {
            RuntimeFixture fixture = new RuntimeFixture(level, 5);
            boolean[] entered = {false};
            Runnable nested = () -> {
                if (entered[0]) return;
                entered[0] = true;
                fixture.chunk.clearAllBlockEntities();
            };
            if (duringRemoval) fixture.entities.get(0).removal = nested;
            else fixture.entities.get(0).unload = nested;
            fixture.chunk.clearAllBlockEntities();
            verify(entered[0], "A real recursive clearAllBlockEntities invocation executes");
            verifyCompleted(fixture, 2, 2);
        }

        private void callbackFailure(ServerLevel level, boolean duringRemoval) throws ReflectiveOperationException {
            RuntimeFixture fixture = new RuntimeFixture(level, 5);
            RuntimeException sentinel = new IllegalStateException("temporary chunk callback sentinel");
            Runnable failure = () -> { throw sentinel; };
            if (duringRemoval) fixture.entities.get(0).removal = failure;
            else fixture.entities.get(0).unload = failure;
            try {
                fixture.chunk.clearAllBlockEntities();
                throw new AssertionError("The runtime callback failure must propagate");
            } catch (RuntimeException caught) {
                verify(caught == sentinel, "The runtime callback exception is unchanged");
            }
            verify(fixture.chunk.getBlockEntities().size() == 5, "Failure does not silently clear entities");
            verify(fixture.tickers.size() == 1, "Failure does not silently clear tickers");
            verifyReleased(fixture.chunk);
            for (ProbeBlockEntity entity : fixture.entities) {
                entity.unload = () -> {};
                entity.removal = () -> {};
            }
            fixture.chunk.clearAllBlockEntities();
            for (ProbeBlockEntity entity : fixture.entities) verify(entity.isRemoved(), "Explicit recovery disposes entities");
            verify(fixture.chunk.getBlockEntities().isEmpty(), "Explicit recovery clears entities");
            verifyTicker(fixture);
            verifyReleased(fixture.chunk);
        }

        private void emptyAndRepeated(ServerLevel level) throws ReflectiveOperationException {
            RuntimeFixture fixture = new RuntimeFixture(level, 0);
            for (int cycle = 0; cycle < 32; cycle++) {
                fixture.chunk.clearAllBlockEntities();
                verify(fixture.chunk.getBlockEntities().isEmpty(), "Repeated empty unload is safe");
                verifyReleased(fixture.chunk);
            }
            verifyTicker(fixture);
        }

        private void tickerRebinding(ServerLevel level) throws ReflectiveOperationException {
            RuntimeFixture fixture = new RuntimeFixture(level, 0);
            Method rebind;
            try {
                rebind = fixture.wrapper.getClass().getDeclaredMethod("m_156449_", TickingBlockEntity.class);
            } catch (NoSuchMethodException mappedEnvironment) {
                rebind = fixture.wrapper.getClass().getDeclaredMethod("rebind", TickingBlockEntity.class);
            }
            rebind.setAccessible(true);
            for (int cycle = 0; cycle < 8; cycle++) {
                fixture.chunk.clearAllBlockEntities();
                verifyTicker(fixture);
                verifyReleased(fixture.chunk);
                ProbeTicker replacement = new ProbeTicker();
                rebind.invoke(fixture.wrapper, replacement);
                fixture.tickers.put(replacement.getPos(), fixture.wrapper);
                fixture.wrapper.tick();
                verify(replacement.ticks == 1, "A cleared real ticker wrapper can be rebound and tick again");
                verify(fixture.ticker.ticks == 1, "Rebinding never revives the original ticker");
                fixture.chunk.clearAllBlockEntities();
                verify(fixture.tickers.isEmpty(), "The rebound real ticker is cleared on the next unload");
                fixture.wrapper.tick();
                verify(replacement.ticks == 1, "The rebound ticker stops after its next unload");
                verifyReleased(fixture.chunk);
            }
        }

        private void verifyCompleted(RuntimeFixture fixture, int unloadCalls, int removalCalls)
                throws ReflectiveOperationException {
            verify(fixture.chunk.getBlockEntities() == fixture.originalMap, "The real chunk map identity is retained");
            verify(fixture.originalMap.isEmpty(), "The real chunk entity map is cleared");
            for (ProbeBlockEntity entity : fixture.entities) {
                verify(entity.unloadCalls == unloadCalls, "Every original entity receives its unload callback");
                verify(entity.removalCalls == removalCalls, "Every original entity receives its removal callback");
                verify(entity.isRemoved(), "Every original real BlockEntity is marked removed");
            }
            verifyTicker(fixture);
            verifyReleased(fixture.chunk);
        }

        private void verifyTicker(RuntimeFixture fixture) {
            verify(fixture.tickers.isEmpty(), "The original ticker map is cleared");
            fixture.wrapper.tick();
            verify(fixture.ticker.ticks == 1, "An escaped original ticker wrapper is rebound to the null ticker");
        }

        private void verifyReleased(LevelChunk chunk) throws ReflectiveOperationException {
            Field snapshot = LevelChunk.class.getDeclaredField("gtlEnhancedcore$unloadedEntities");
            snapshot.setAccessible(true);
            verify(snapshot.get(chunk) == null, "The production Mixin retains no completed or failed snapshot");
        }

        private void verify(boolean condition, String message) {
            assertions++;
            if (!condition) throw new AssertionError(message);
        }

        private static final class RuntimeFixture {
            final LevelChunk chunk;
            final List<ProbeBlockEntity> entities = new ArrayList<>();
            final Map<BlockPos, BlockEntity> originalMap;
            final Map<BlockPos, Object> tickers;
            final ProbeTicker ticker = new ProbeTicker();
            final TickingBlockEntity wrapper;

            @SuppressWarnings("unchecked")
            RuntimeFixture(ServerLevel level, int size) throws ReflectiveOperationException {
                chunk = new LevelChunk(level, new ChunkPos(0, 0));
                originalMap = chunk.getBlockEntities();
                for (int index = 0; index < size; index++) {
                    ProbeBlockEntity entity = new ProbeBlockEntity(new BlockPos(index, 64, 0));
                    entities.add(entity);
                    originalMap.put(entity.getBlockPos(), entity);
                }
                Field tickerField;
                try {
                    tickerField = LevelChunk.class.getDeclaredField("f_156362_");
                } catch (NoSuchFieldException mappedEnvironment) {
                    tickerField = LevelChunk.class.getDeclaredField("tickersInLevel");
                }
                tickerField.setAccessible(true);
                tickers = (Map<BlockPos, Object>) tickerField.get(chunk);
                Class<?> wrapperClass = Class.forName(
                        "net.minecraft.world.level.chunk.LevelChunk$RebindableTickingBlockEntityWrapper");
                Constructor<?> constructor = wrapperClass.getDeclaredConstructor(LevelChunk.class, TickingBlockEntity.class);
                constructor.setAccessible(true);
                wrapper = (TickingBlockEntity) constructor.newInstance(chunk, ticker);
                tickers.put(ticker.getPos(), wrapper);
                wrapper.tick();
            }
        }

        private static final class ProbeBlockEntity extends BlockEntity {
            int unloadCalls;
            int removalCalls;
            Runnable unload = () -> {};
            Runnable removal = () -> {};

            ProbeBlockEntity(BlockPos position) {
                super(BlockEntityType.CHEST, position, Blocks.CHEST.defaultBlockState());
            }

            @Override
            public void onChunkUnloaded() {
                unloadCalls++;
                super.onChunkUnloaded();
                unload.run();
            }

            @Override
            public void setRemoved() {
                removalCalls++;
                super.setRemoved();
                removal.run();
            }
        }

        private static final class ProbeTicker implements TickingBlockEntity {
            int ticks;

            @Override
            public void tick() {
                ticks++;
            }

            @Override
            public boolean isRemoved() {
                return false;
            }

            @Override
            public BlockPos getPos() {
                return new BlockPos(0, 64, 0);
            }

            @Override
            public String getType() {
                return "enhancedcore-temporary-unload-probe";
            }
        }
    }

    private static final class CountingMap extends HashMap<Integer, Object> {
        private static final long serialVersionUID = 1L;
        int putCalls;
        int removeCalls;
        RuntimeException removeFailure;

        @Override
        public Object put(Integer key, Object value) {
            putCalls++;
            return super.put(key, value);
        }

        @Override
        public Object remove(Object key) {
            removeCalls++;
            if (removeFailure != null) throw removeFailure;
            return super.remove(key);
        }
    }

    private static ChunkUnloadDiagnostics probe(List<String> reports, int events, int normal, int failures) {
        return new ChunkUnloadDiagnostics(new ChunkUnloadDiagnostics.Budget(events, normal, failures, 32,
                reports::add));
    }

    private static String only(List<String> reports) {
        check(reports.size() == 1, "exactly one report");
        return reports.get(0);
    }

    private static void expectCme(Runnable action) {
        try {
            action.run();
            throw new AssertionError("original HashMap traversal must still throw CME");
        } catch (ConcurrentModificationException expected) {
            check(true, "original CME propagated");
        }
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
