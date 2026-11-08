package com.gtl.enhancedcore;

import com.gtl.enhancedcore.common.performance.DismantleMaterialIndex;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.jar.JarFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

final class DismantleMaterialIndexRegression {
    private static int assertions;
    private record Material(String name) {}
    private record Candidate(Material block, String state) {}
    private record Simple(Supplier<Candidate[]> supplier, boolean airOnly) {
        Simple(Supplier<Candidate[]> supplier) { this(supplier, false); }
    }
    private record Predicate(boolean any, List<Simple> common, List<Simple> limited) {}
    private static class Fixture implements DismantleMaterialIndex.Adapter<Predicate, Simple, Candidate, Material> {
        int inspected;
        public boolean any(Predicate p) { inspected++; return p.any; }
        public int commonSize(Predicate p) { return p.common.size(); }
        public int limitedSize(Predicate p) { return p.limited.size(); }
        public Simple common(Predicate p, int i) { return p.common.get(i); }
        public Simple limited(Predicate p, int i) { return p.limited.get(i); }
        public Supplier<Candidate[]> candidates(Simple p) { return p.supplier; }
        public boolean airOnly(Simple p) { return p.airOnly; }
        public Material block(Candidate c) { return c.block; }
    }

    static int run() throws Exception {
        assertions = 0;
        Material casing = new Material("machine casing"), dirt = new Material("dirt"), lamp = new Material("lamp");
        AtomicInteger calls = new AtomicInteger();
        Supplier<Candidate[]> supplier = () -> {
            calls.incrementAndGet();
            return new Candidate[] {new Candidate(casing, "inactive"), new Candidate(casing, "active"),
                    new Candidate(lamp, "lit")};
        };
        Simple shared = new Simple(supplier), alias = new Simple(supplier);
        Predicate known = new Predicate(false, List.of(shared), List.of(shared, alias));
        Predicate any = new Predicate(true, List.of(new Simple(null)), List.of());
        Predicate[][][] pattern = {{{known, known, any}}};
        var index = new DismantleMaterialIndex.Builder<>(pattern, new Fixture());
        finish(index);
        check(index.snapshot().mayContain(casing) && index.snapshot().mayContain(lamp),
                "all candidate block types including limited alternatives survive the filter");
        check(index.snapshot().blocks().size() == 2,
                "state variants share a block-type entry as in fix4 isBlockValid");
        check(!index.snapshot().mayContain(dirt), "unrelated ground cannot force partial layout planning");
        check(!index.snapshot().unknown(), "ANY cells do not create unknown fallback tests");
        check(calls.get() == 1, "shared predicates, simples and suppliers are evaluated once by identity");
        for (Field field : index.getClass().getDeclaredFields()) {
            field.setAccessible(true);
            if (field.getName().equals("matches") || field.getName().equals("adapter"))
                check(field.get(index) == null, "completed builders release pattern and adapter captures");
        }

        for (Simple fallback : List.of(new Simple(null), new Simple(() -> null),
                new Simple(() -> new Candidate[0]), new Simple(() -> { throw new IllegalStateException("mod supplier"); }))) {
            var unknown = new DismantleMaterialIndex.Builder<>(
                    new Predicate[][][] {{{new Predicate(false, List.of(fallback), List.of())}}}, new Fixture());
            finish(unknown);
            check(unknown.snapshot().unknown() && unknown.snapshot().mayContain(dirt),
                    "missing, empty or failing candidates retain the world-test path safely");
        }
        Predicate empty = new Predicate(false, List.of(), List.of());
        var noAlternatives = new DismantleMaterialIndex.Builder<>(new Predicate[][][] {{{empty}}}, new Fixture());
        finish(noAlternatives);
        check(!noAlternatives.snapshot().mayContain(dirt), "a predicate with no simples cannot pass fix4 isBlockValid");
        var withAir = new DismantleMaterialIndex.Builder<>(new Predicate[][][] {{{
                known, new Predicate(false, List.of(new Simple(null, true)), List.of())}}}, new Fixture());
        finish(withAir);
        check(!withAir.snapshot().unknown() && withAir.snapshot().mayContain(casing) && !withAir.snapshot().mayContain(dirt),
                "singleton AIR fallback cannot disable filtering of unrelated non-air ground");
        var suppliedAir = new DismantleMaterialIndex.Builder<>(new Predicate[][][] {{{new Predicate(false,
                List.of(new Simple(() -> new Candidate[] {new Candidate(lamp, "custom candidate")}, true)), List.of())}}}, new Fixture());
        finish(suppliedAir);
        check(suppliedAir.snapshot().mayContain(lamp), "AIR identity does not override nonempty candidate Block semantics");

        var fixture = new Fixture();
        Predicate[] large = new Predicate[20000];
        for (int i = 0; i < large.length; i++) large[i] = new Predicate(true, List.of(), List.of());
        var bounded = new DismantleMaterialIndex.Builder<>(new Predicate[][][] {{large}}, fixture);
        bounded.advance();
        check(!bounded.done() && fixture.inspected <= 8192, "one advance never consumes an unbounded pattern array");
        finish(bounded);
        check(fixture.inspected == large.length, "bounded traversal eventually sees every pattern reference");
        AtomicInteger extracted = new AtomicInteger();
        Candidate[] manyCandidates = new Candidate[20000];
        java.util.Arrays.fill(manyCandidates, new Candidate(casing, "any state"));
        Fixture boundedCandidates = new Fixture() {
            @Override public Material block(Candidate candidate) { extracted.incrementAndGet(); return super.block(candidate); }
        };
        var candidatesIndex = new DismantleMaterialIndex.Builder<>(new Predicate[][][] {{{
                new Predicate(false, List.of(new Simple(() -> manyCandidates)), List.of())}}}, boundedCandidates);
        candidatesIndex.advance();
        check(!candidatesIndex.done() && extracted.get() <= 8192, "large supplier arrays are consumed incrementally");
        finish(candidatesIndex);
        check(extracted.get() == manyCandidates.length, "supplier arrays are not truncated by the work limit");
        dynamicCandidates();
        upstreamSemantics();
        upstreamAir();
        return assertions;
    }

    private static void dynamicCandidates() throws Exception {
        Material oldBlock = new Material("old casing"), replacement = new Material("new casing");
        Material[] current = {oldBlock};
        AtomicInteger calls = new AtomicInteger();
        Predicate[][][] samePattern = {{{new Predicate(false, List.of(new Simple(() -> {
            calls.incrementAndGet();
            return new Candidate[] {new Candidate(current[0], "current world")};
        })), List.of())}}};
        var first = new DismantleMaterialIndex.Builder<>(samePattern, new Fixture());
        finish(first);
        current[0] = replacement;
        var second = new DismantleMaterialIndex.Builder<>(samePattern, new Fixture());
        finish(second);
        check(calls.get() == 2, "every new task re-evaluates suppliers even for the same pattern identity");
        check(first.snapshot().mayContain(oldBlock) && !first.snapshot().mayContain(replacement)
                        && second.snapshot().mayContain(replacement) && !second.snapshot().mayContain(oldBlock),
                "replaced materials are observed by a later task rather than hidden by a stale snapshot");
        check(DismantleMaterialIndex.Snapshot.class.getDeclaredFields().length == 2,
                "per-job snapshots contain only the block set and unknown bit");
        ClassNode production = new ClassNode();
        try (var input = DismantleMaterialIndexRegression.class.getClassLoader().getResourceAsStream(
                "com/gtl/enhancedcore/common/performance/DismantleMaterialIndex.class")) {
            new ClassReader(input).accept(production, 0);
        }
        check(production.fields.stream().filter(field -> (field.access & Opcodes.ACC_STATIC) != 0)
                        .allMatch(field -> field.desc.equals("I")),
                "production has no static material snapshot/predicate/supplier/world cache");
    }

    private static void upstreamSemantics() throws Exception {
        ClassNode upstream = new ClassNode();
        try (JarFile jar = new JarFile("libs/gtlcore-1.2.3.3-fix3.jar");
                var input = jar.getInputStream(jar.getJarEntry("org/gtlcore/gtlcore/api/pattern/AdvancedBlockPattern.class"))) {
            new ClassReader(input).accept(upstream, 0);
        }
        var method = upstream.methods.stream().filter(candidate -> candidate.name.equals("isBlockValid")).findFirst().orElseThrow();
        JumpInsnNode nullCandidates = null, emptyCandidates = null;
        int comparison = -1, fallbackTest = -1;
        boolean supplierReached = false;
        for (int i = 0; i < method.instructions.size(); i++) {
            AbstractInsnNode instruction = method.instructions.get(i);
            if (instruction instanceof JumpInsnNode jump && instruction.getOpcode() == Opcodes.IFNULL
                    && supplierReached && nullCandidates == null) nullCandidates = jump;
            if (instruction instanceof JumpInsnNode jump && instruction.getOpcode() == Opcodes.IFLE
                    && emptyCandidates == null) emptyCandidates = jump;
            if (instruction instanceof MethodInsnNode call) {
                if (call.owner.equals("java/util/function/Supplier") && call.name.equals("get")) supplierReached = true;
                if (call.owner.equals("net/minecraft/world/level/block/state/BlockState")
                        && call.desc.equals("(Lnet/minecraft/world/level/block/Block;)Z")) comparison = i;
                if (call.owner.equals("com/gregtechceu/gtceu/api/pattern/predicates/SimplePredicate")
                        && call.name.equals("test")) fallbackTest = i;
            }
        }
        check(nullCandidates != null && emptyCandidates != null && nullCandidates.label == emptyCandidates.label,
                "actual fix4 sends null and empty candidates to the same fallback branch");
        int fallback = method.instructions.indexOf(nullCandidates.label);
        check(comparison > method.instructions.indexOf(emptyCandidates) && comparison < fallback
                        && fallbackTest > fallback,
                "actual fix4 compares candidate Block types before the fallback SimplePredicate.test");
        boolean skipsFallback = false, directSuccess = false;
        for (int i = comparison + 1; i < fallback; i++) {
            AbstractInsnNode instruction = method.instructions.get(i);
            if (instruction.getOpcode() == Opcodes.IRETURN) directSuccess = true;
            if (instruction instanceof JumpInsnNode jump && instruction.getOpcode() == Opcodes.GOTO
                    && method.instructions.indexOf(jump.label) > fallbackTest) skipsFallback = true;
        }
        check(directSuccess && skipsFallback,
                "nonempty candidate match returns true directly and a mismatch skips the world predicate fallback");
    }

    private static void upstreamAir() throws Exception {
        ClassNode simple = new ClassNode();
        try (JarFile jar = new JarFile("libs/gtceu-1.20.1-1.4.4.jar");
                var input = jar.getInputStream(jar.getJarEntry("com/gregtechceu/gtceu/api/pattern/predicates/SimplePredicate.class"))) {
            new ClassReader(input).accept(simple, 0);
        }
        var initializer = simple.methods.stream().filter(method -> method.name.equals("<clinit>")).findFirst().orElseThrow();
        boolean airHasNullCandidates = false;
        for (AbstractInsnNode instruction : initializer.instructions) {
            if (instruction instanceof org.objectweb.asm.tree.FieldInsnNode field
                    && field.getOpcode() == Opcodes.PUTSTATIC && field.name.equals("AIR")) {
                AbstractInsnNode constructor = previousCode(instruction);
                airHasNullCandidates = constructor instanceof MethodInsnNode call && call.name.equals("<init>")
                        && previousCode(constructor).getOpcode() == Opcodes.ACONST_NULL;
            }
        }
        check(airHasNullCandidates, "actual GTCEu AIR singleton is constructed with no candidate supplier");
        var airTest = simple.methods.stream().filter(method -> method.name.equals("lambda$static$1")).findFirst().orElseThrow();
        check(java.util.Arrays.stream(airTest.instructions.toArray()).anyMatch(instruction ->
                        instruction instanceof MethodInsnNode call && call.owner.equals("net/minecraft/world/level/Level")
                                && call.desc.equals("(Lnet/minecraft/core/BlockPos;)Z")),
                "actual GTCEu AIR fallback delegates to the world's empty-block query");
    }

    private static AbstractInsnNode previousCode(AbstractInsnNode instruction) {
        do { instruction = instruction.getPrevious(); } while (instruction != null && instruction.getOpcode() < 0);
        return instruction;
    }

    private static void finish(DismantleMaterialIndex.Builder<?, ?, ?, ?> builder) {
        for (int i = 0; i < 100 && !builder.done(); i++) builder.advance();
        check(builder.done(), "incremental material indexing finishes");
    }

    private static void check(boolean valid, String message) {
        assertions++;
        if (!valid) throw new AssertionError(message);
    }
}
