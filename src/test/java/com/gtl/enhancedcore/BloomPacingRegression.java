package com.gtl.enhancedcore;

import com.gtl.enhancedcore.client.performance.bloom.DeferredQueuePruner;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;
import java.util.jar.JarFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

/** Differential queue lifecycle tests and an executable contract against the actual GTLCore jar. */
final class BloomPacingRegression {
    private static int assertions;

    static int run() throws Exception {
        assertions = 0;
        upstreamContract();
        for (int seed = 0; seed < 32; seed++) differential(seed);
        predicateChangeAndClear();
        sweepReduction();
        inactiveFrames();
        noRendering();
        equalPriority();
        return assertions;
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Bloom pacing: " + run() + " assertions passed");
    }

    private static final class Entry {
        final int coordinate;
        final int order;
        boolean valid = true;
        Entry(int coordinate, int order) { this.coordinate = coordinate; this.order = order; }
    }

    private static void differential(int seed) {
        var random = new Random(seed);
        var comparator = Comparator.comparingInt((Entry e) -> e.coordinate).thenComparingInt(e -> e.order);
        var baseline = new PriorityQueue<>(comparator);
        var deferred = new PriorityQueue<>(comparator);
        var pruner = new DeferredQueuePruner<Entry>();
        Predicate<Entry> invalid = e -> !e.valid;
        var all = new ArrayList<Entry>();
        for (int step = 0; step < 2000; step++) {
            int action = random.nextInt(10);
            if (action < 5) {
                // A new identity at the same coordinate must survive an old entry's unload.
                var entry = new Entry(random.nextInt(32), step);
                all.add(entry);
                baseline.add(entry);
                deferred.add(entry);
            } else if (action < 8 && !all.isEmpty()) {
                all.get(random.nextInt(all.size())).valid = false;
                baseline.removeIf(invalid);
                pruner.defer(deferred, invalid);
            } else if (action == 8) {
                pruner.flush(deferred);
                check(baseline.size() == deferred.size(), "Observed pending count is unchanged");
                check(new HashSet<>(baseline).equals(new HashSet<>(deferred)), "All surviving identities match");
                check(baseline.poll() == deferred.poll(), "Next service order is unchanged");
            } else {
                baseline.clear();
                pruner.reset();
                deferred.clear();
                all.clear();
            }
        }
        pruner.flush(deferred);
        while (!baseline.isEmpty()) check(baseline.poll() == deferred.poll(), "Final priority order matches");
        check(deferred.isEmpty(), "No stale work remains");
    }

    private static void predicateChangeAndClear() {
        var queue = new PriorityQueue<Integer>();
        queue.addAll(java.util.List.of(1, 2, 3));
        var pruner = new DeferredQueuePruner<Integer>();
        pruner.defer(queue, i -> i == 1);
        pruner.defer(queue, i -> i == 2);
        pruner.flush(queue);
        check(queue.size() == 1 && queue.peek() == 3, "Different predicates cannot erase a previous request");
        pruner.defer(queue, i -> true);
        pruner.reset();
        queue.clear();
        queue.add(4);
        pruner.flush(queue);
        check(queue.peek() == 4, "World clear drops the old predicate before new work");
    }

    private static void sweepReduction() {
        var queue = new PriorityQueue<Integer>();
        for (int i = 0; i < 8192; i++) queue.add(i);
        var pruner = new DeferredQueuePruner<Integer>();
        int[] visits = {0};
        Predicate<Integer> invalid = i -> { visits[0]++; return i < 2048; };
        for (int chunk = 0; chunk < 256; chunk++) pruner.defer(queue, invalid);
        check(visits[0] == 0, "Unload burst performs no repeated whole-queue sweeps");
        pruner.flush(queue);
        check(visits[0] == 8192 && queue.size() == 6144, "A burst uses exactly one complete sweep");
        pruner.flush(queue);
        check(visits[0] == 8192, "Repeated observation without invalidation performs no sweep");
    }

    private static void inactiveFrames() {
        var queue = new PriorityQueue<>(Comparator.comparingInt((Entry e) -> e.order));
        var pruner = new DeferredQueuePruner<Entry>();
        Predicate<Entry> invalid = e -> !e.valid;
        for (int frame = 0; frame < 5000; frame++) {
            for (int chunk = 0; chunk < 32; chunk++) {
                var entry = new Entry(chunk, frame * 32 + chunk);
                queue.add(entry);
                entry.valid = false;
                pruner.defer(queue, invalid);
            }
            // Models setLevel's RETURN hook, with no buildSome or pendingCount for
            // the entire shader-suppressed session. No dead work survives a frame.
            pruner.flush(queue);
            check(queue.isEmpty(), "Inactive Bloom cannot retain invalid entries across frames");
        }
    }

    private static void equalPriority() {
        var comparator = Comparator.comparingInt((Entry e) -> e.coordinate);
        var baseline = new PriorityQueue<>(comparator);
        var deferred = new PriorityQueue<>(comparator);
        var pruner = new DeferredQueuePruner<Entry>();
        Predicate<Entry> invalid = e -> !e.valid;
        for (int i = 0; i < 1024; i++) {
            var entry = new Entry(i % 8, i);
            baseline.add(entry);
            deferred.add(entry);
            if ((i & 3) == 0) entry.valid = false;
            baseline.removeIf(invalid);
            pruner.defer(deferred, invalid);
        }
        pruner.flush(deferred);
        check(new HashSet<>(baseline).equals(new HashSet<>(deferred)), "All equal-priority identities survive");
        while (!baseline.isEmpty()) {
            check(comparator.compare(baseline.poll(), deferred.poll()) == 0,
                    "Heapification preserves comparator priority, including exact ties");
        }
        check(deferred.isEmpty(), "Equal-priority updates are neither duplicated nor lost");
    }

    private static void noRendering() {
        var queue = new PriorityQueue<>(Comparator.comparingInt((Entry e) -> e.order));
        var pruner = new DeferredQueuePruner<Entry>();
        Predicate<Entry> invalid = e -> !e.valid;
        for (int request = 0; request < 10000; request++) {
            var entry = new Entry(0, request);
            queue.add(entry);
            entry.valid = false;
            pruner.defer(queue, invalid);
            check(queue.size() < DeferredQueuePruner.MAX_DEFERRED_REQUESTS,
                    "Suppressed world rendering still has a bounded invalidation-request backlog");
        }
        pruner.flush(queue);
        check(queue.isEmpty(), "Final boundary releases the remaining suspended-render backlog");
    }

    private static void upstreamContract() throws Exception {
        ClassNode node = new ClassNode();
        try (var jar = new JarFile(Path.of("libs/gtlcore-1.2.3.2-fix4.jar").toFile())) {
            try (var in = jar.getInputStream(jar.getJarEntry("org/gtlcore/gtlcore/client/bloom/SectionMeshes.class"))) {
                new ClassReader(in).accept(node, 0);
            }
            ClassNode client = new ClassNode();
            try (var in = jar.getInputStream(jar.getJarEntry("org/gtlcore/gtlcore/client/bloom/BloomClient.class"))) {
                new ClassReader(in).accept(client, 0);
            }
            int levelCalls = 0;
            int activeCalls = 0;
            int branchesBeforeLevel = 0;
            for (var method : client.methods) {
                if (!method.name.equals("beginFrame") || !method.desc.equals("()V")) continue;
                for (var instruction : method.instructions) {
                    if (levelCalls == 0 && instruction instanceof org.objectweb.asm.tree.JumpInsnNode) branchesBeforeLevel++;
                    if (instruction instanceof MethodInsnNode call) {
                        if (call.owner.equals(node.name) && call.name.equals("setLevel")) {
                            check(call.desc.equals("(Lnet/minecraft/client/multiplayer/ClientLevel;)V"),
                                    "Frame lifecycle calls exact setLevel descriptor");
                            levelCalls++;
                            check(activeCalls == 0, "setLevel runs before active Bloom gating");
                        }
                        if (call.name.equals("active")) activeCalls++;
                    }
                }
            }
            check(levelCalls == 1 && activeCalls == 1 && branchesBeforeLevel == 1,
                    "Only initialization guards frame-boundary cleanup, not active/shader availability");
            boolean initialized = false;
            int registrations = 0;
            for (var method : client.methods) {
                if (!method.name.equals("init")) continue;
                for (var instruction : method.instructions) {
                    if (instruction instanceof FieldInsnNode field && field.name.equals("initialized")
                            && field.getOpcode() == Opcodes.PUTSTATIC) initialized = true;
                    if (instruction instanceof MethodInsnNode call && call.name.equals("addListener")) {
                        check(initialized, "Chunk-event listeners register only after Bloom initialization");
                        registrations++;
                    }
                }
            }
            check(registrations > 0, "Initialization event-registration contract exists");
            ClassNode renderer = new ClassNode();
            try (var in = jar.getInputStream(jar.getJarEntry("org/gtlcore/gtlcore/mixin/bloom/GameRendererMixin.class"))) {
                new ClassReader(in).accept(renderer, 0);
            }
            var begin = renderer.methods.stream().filter(m -> m.name.equals("gtlcore$bloom$begin")).findFirst().orElseThrow();
            var code = new ArrayList<Integer>();
            for (var instruction : begin.instructions) if (instruction.getOpcode() >= 0) code.add(instruction.getOpcode());
            check(code.equals(java.util.List.of(Opcodes.INVOKESTATIC, Opcodes.RETURN)),
                    "World-render hook invokes beginFrame without active-state branches");
            var call = (MethodInsnNode) java.util.stream.StreamSupport.stream(begin.instructions.spliterator(), false)
                    .filter(i -> i instanceof MethodInsnNode).findFirst().orElseThrow();
            check(call.owner.equals(client.name) && call.name.equals("beginFrame") && call.desc.equals("()V"),
                    "World-render callback invokes exact beginFrame entry");
            var inject = begin.visibleAnnotations.stream().filter(a -> a.desc.endsWith("/Inject;")).findFirst().orElseThrow();
            check(annotationValue(inject, "method").equals(java.util.List.of("renderLevel")),
                    "Callback runs on every renderLevel invocation");
            var at = (org.objectweb.asm.tree.AnnotationNode) ((java.util.List<?>) annotationValue(inject, "at")).getFirst();
            check(annotationValue(at, "value").equals("HEAD"), "Frame cleanup begins before world rendering");
        }
        Set<String> readers = new HashSet<>();
        int removals = 0;
        int validReads = 0;
        int statusCountCalls = 0;
        var predicateOpcodes = new ArrayList<Integer>();
        for (var method : node.methods) {
            if (method.name.equals("setLevel")) {
                int returns = 0;
                for (var instruction : method.instructions) if (instruction.getOpcode() == Opcodes.RETURN) returns++;
                check(returns == 2, "Frame hook covers both same-world and changed-world returns");
            }
            for (var instruction : method.instructions) {
                if (method.name.equals("lambda$unloadChunk$4") && instruction.getOpcode() >= 0) {
                    predicateOpcodes.add(instruction.getOpcode());
                }
                if (instruction instanceof FieldInsnNode field && field.name.equals("pending")) readers.add(method.name);
                if (method.name.equals("lambda$unloadChunk$4") && instruction instanceof FieldInsnNode field) {
                    check(field.name.equals("valid") && field.desc.equals("Z"), "Prune predicate reads only validity");
                    validReads++;
                }
                if (instruction instanceof MethodInsnNode call) {
                    if (method.name.equals("lambda$unloadChunk$4")) check(false, "Prune predicate cannot invoke callbacks");
                    if (method.name.equals("status") && call.name.equals("pendingCount")) statusCountCalls++;
                    if (method.name.equals("unloadChunk") && call.owner.equals("java/util/PriorityQueue")
                            && call.name.equals("removeIf")) {
                        removals++;
                        check(call.desc.equals("(Ljava/util/function/Predicate;)Z"), "Exact redirect descriptor matches");
                        var next = call.getNext();
                        while (next != null && next.getOpcode() < 0) next = next.getNext();
                        check(next != null && next.getOpcode() == Opcodes.POP, "Unload ignores the deferred boolean result");
                    }
                }
            }
        }
        check(removals == 1 && validReads == 1, "Actual target has one pure validity sweep");
        check(predicateOpcodes.equals(java.util.List.of(Opcodes.ALOAD, Opcodes.GETFIELD,
                        Opcodes.IFNE, Opcodes.ICONST_1, Opcodes.GOTO, Opcodes.ICONST_0, Opcodes.IRETURN)),
                "Predicate is exactly !entry.valid, with no extra effects or dependencies");
        check(statusCountCalls == 1, "Status observes the flushed pendingCount API");
        check(readers.equals(Set.of("<init>", "unloadChunk", "buildSome", "pollNext", "queueAgain", "pendingCount", "clear")),
                "No unhandled pending-queue observation was added upstream");
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private static Object annotationValue(org.objectweb.asm.tree.AnnotationNode annotation, String key) {
        for (int i = 0; i < annotation.values.size(); i += 2) {
            if (annotation.values.get(i).equals(key)) return annotation.values.get(i + 1);
        }
        throw new AssertionError("Missing annotation attribute: " + key);
    }
}
