package com.gtl.enhancedcore;

import com.gtl.enhancedcore.common.util.ActiveBlockSnapshot;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.jar.JarFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

/** Exercises the actual fastutil failure and independent traversal, without starting Forge. */
final class ActiveBlockSnapshotRegression {
    private static int assertions;

    static int run() throws Exception {
        assertions = 0;
        originalFailure();
        for (int size : new int[]{0, 1, 2, 16, 1024, 65536}) reentrantChanges(size);
        copyFailureUnlocks();
        concurrentMatcher();
        upstreamContract("libs/gtceu-1.20.1-1.4.4.jar",
                "com/gregtechceu/gtceu/api/machine/multiblock/WorkableMultiblockMachine");
        upstreamContract("libs/gtlcore-1.2.3.3-fix3.jar",
                "org/gtlcore/gtlcore/mixin/gtm/api/machine/WorkableMultiblockMachineMixin");
        mergedMethodPriority();
        return assertions;
    }

    private static void originalFailure() {
        var positions = new LongOpenHashSet(new long[]{1, 2});
        LongIterator original = positions.iterator();
        original.nextLong();
        positions.clear();
        try {
            original.nextLong();
            throw new AssertionError("Live iterator must reproduce the reported failure");
        } catch (NullPointerException expected) {
            check(expected.getStackTrace()[0].getClassName().endsWith("LongOpenHashSet$SetIterator"),
                    "Reproduced the same fastutil iterator failure as the player log");
        }
    }

    private static void reentrantChanges(int size) {
        var positions = new LongOpenHashSet();
        for (int i = 0; i < size; i++) positions.add(i == 0 ? Long.MIN_VALUE : i);
        var expected = positions.clone();
        var lock = new ReentrantLock();
        LongIterator traversal = ActiveBlockSnapshot.iterator(positions, lock);
        check(!lock.isLocked(), "Lock is released before world callbacks");
        check(positions.equals(expected), "Snapshot does not change the structure match context");
        // Model a block callback clearing/rebuilding the match context during traversal.
        var visited = new LongOpenHashSet();
        while (traversal.hasNext()) {
            long position = (Long) traversal.next(); // Same boxed bridge as the upstream loop.
            check(visited.add(position), "Each original position is visited once");
            positions.clear();
            positions.add(Long.MAX_VALUE);
            LongIterator nested = ActiveBlockSnapshot.iterator(positions, lock);
            check(nested.nextLong() == Long.MAX_VALUE && !nested.hasNext(),
                    "Nested traversal sees new context without changing the outer snapshot");
        }
        check(visited.equals(expected), "Outer traversal retains every original position");
        check(!lock.isLocked(), "Nested callbacks retain no lock");
        if (size != 0) check(positions.contains(Long.MAX_VALUE), "Original callback changes are retained");
        try {
            traversal.remove();
            throw new AssertionError("Snapshot iterator must not allow removal");
        } catch (UnsupportedOperationException expectedFailure) { assertions++; }
    }

    private static void copyFailureUnlocks() {
        var lock = new ReentrantLock();
        var sentinel = new IllegalStateException("copy failure");
        var positions = new LongOpenHashSet() {
            @Override public long[] toLongArray() { throw sentinel; }
        };
        try {
            ActiveBlockSnapshot.iterator(positions, lock);
            throw new AssertionError("Copy failure must propagate");
        } catch (IllegalStateException expected) {
            check(expected == sentinel, "Copy errors are not swallowed");
        }
        check(!lock.isLocked(), "Copy errors cannot leak the pattern lock");
        lock.lock();
        try {
            ActiveBlockSnapshot.iterator(new LongOpenHashSet(), lock);
            check(lock.getHoldCount() == 1, "Reentrant caller retains its original lock ownership");
        } finally { lock.unlock(); }
    }

    private static void concurrentMatcher() throws Exception {
        var lock = new ReentrantLock();
        var positions = new LongOpenHashSet();
        var started = new CountDownLatch(1);
        var failure = new AtomicReference<Throwable>();
        Thread matcher = new Thread(() -> {
            started.countDown();
            try {
                for (int generation = 0; generation < 2000; generation++) {
                    lock.lock();
                    try {
                        positions.clear();
                        for (int i = 0; i < 128; i++) positions.add((long) generation * 128 + i);
                    } finally { lock.unlock(); }
                }
            } catch (Throwable caught) { failure.set(caught); }
        }, "active-block-regression-matcher");
        matcher.start();
        try {
            check(started.await(5, TimeUnit.SECONDS), "Matcher started");
            for (int repeat = 0; repeat < 2000; repeat++) {
                LongIterator traversal = ActiveBlockSnapshot.iterator(positions, lock);
                int count = 0;
                long generation = -1;
                while (traversal.hasNext()) {
                    long position = traversal.nextLong();
                    if (generation < 0) generation = position / 128;
                    check(position / 128 == generation, "Snapshot belongs to one complete match generation");
                    count++;
                }
                check(count == 0 || count == 128, "No partially rebuilt match context is published");
            }
        } finally {
            matcher.join(10000);
        }
        check(!matcher.isAlive(), "Concurrent matcher completes without deadlock");
        if (failure.get() != null) throw new AssertionError("Matcher failed", failure.get());
    }

    private static void upstreamContract(String jarPath, String className) throws Exception {
        var node = new ClassNode();
        try (var jar = new JarFile(Path.of(jarPath).toFile());
             var input = jar.getInputStream(jar.getJarEntry(className + ".class"))) {
            new ClassReader(input).accept(node, 0);
        }
        var method = node.methods.stream().filter(m -> m.name.equals("updateActiveBlocks")
                && m.desc.equals("(Z)V")).findFirst().orElseThrow();
        int targets = 0;
        for (var instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call
                    && call.owner.equals("it/unimi/dsi/fastutil/longs/LongSet")
                    && call.name.equals("iterator")
                    && call.desc.equals("()Lit/unimi/dsi/fastutil/longs/LongIterator;")) targets++;
        }
        check(targets == 1, "Exactly one redirect target in " + jarPath);
    }

    private static void mergedMethodPriority() throws Exception {
        var patch = new ClassNode();
        try (var input = ActiveBlockSnapshotRegression.class.getClassLoader().getResourceAsStream(
                "com/gtl/enhancedcore/mixin/gtceu/WorkableActiveBlocksSnapshotMixin.class")) {
            new ClassReader(input).accept(patch, 0);
        }
        var upstream = new ClassNode();
        try (var jar = new JarFile("libs/gtlcore-1.2.3.3-fix3.jar");
             var input = jar.getInputStream(jar.getJarEntry(
                     "org/gtlcore/gtlcore/mixin/gtm/api/machine/WorkableMultiblockMachineMixin.class"))) {
            new ClassReader(input).accept(upstream, 0);
        }
        // Mixin 0.8.5 BeforeInvoke rejects injection when mergedPriority >= injectorPriority.
        check(priority(patch) > priority(upstream), "Redirect may target GTLCore's merged updateActiveBlocks");
        check(patch.methods.stream().noneMatch(m -> m.name.equals("updateActiveBlocks")),
                "Patch retains GTLCore's update body, including flags 18");
    }

    private static int priority(ClassNode node) {
        for (var annotation : node.invisibleAnnotations) {
            if (!annotation.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")) continue;
            for (int i = 0; i < annotation.values.size(); i += 2) {
                if (annotation.values.get(i).equals("priority")) return (Integer) annotation.values.get(i + 1);
            }
        }
        return 1000;
    }

    private static void check(boolean valid, String message) {
        if (!valid) throw new AssertionError(message);
        assertions++;
    }
}
