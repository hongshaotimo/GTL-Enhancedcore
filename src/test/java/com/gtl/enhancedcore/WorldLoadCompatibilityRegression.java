package com.gtl.enhancedcore;

import com.gtl.enhancedcore.common.integration.AddSkyTearsRecipeCompatibility;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Executes the real ADD input callback and our compiled predicate matcher with small dependency fixtures. */
public final class WorldLoadCompatibilityRegression {
    private static final String PREFIX = WorldLoadCompatibilityRegression.class.getName().replace('.', '/') + "$";
    private static int checks;

    public static void main(String[] args) throws Exception {
        run();
        System.out.println("PASS: ADD empty-input callback and aggregation input-bus compatibility");
    }

    public static int run() throws Exception {
        checks = 0;
        var original = readJar("gtladditions-3.3.1Custom.jar", AddSkyTearsRecipeCompatibility.TARGET);
        Method before = inputProbe(original, "Before");
        try {
            before.invoke(null, new Builder(new Ingredient()), (Consumer<Object>) value -> {});
            throw new AssertionError("Original ADD callback did not reproduce the empty-array crash");
        } catch (InvocationTargetException failure) {
            check(failure.getCause() instanceof ArrayIndexOutOfBoundsException, "Original callback reproduces the reported crash");
        }
        var patched = readJar("gtladditions-3.3.1Custom.jar", AddSkyTearsRecipeCompatibility.TARGET);
        AddSkyTearsRecipeCompatibility.transform(patched);
        Method after = inputProbe(patched, "After");
        var ordinary = new Stack(new Object());
        checkInput(after, 1, 0, new Ingredient());
        checkInput(after, 1, 0, new Ingredient(), new Ingredient(ordinary));
        checkInput(after, 0, 1, new Ingredient(), new Ingredient(Sky.INSTANCE.target));
        checkInput(after, 0, 0, new Ingredient(), new Ingredient(Sky.INSTANCE.excluded));
        checkInput(after, 1, 0, new Ingredient(ordinary));
        aggregation();
        return checks;
    }

    private static void checkInput(Method probe, int cloned, int drilled, Ingredient... inputs) throws Exception {
        var builder = new Builder(inputs);
        var input = builder.input;
        probe.invoke(null, builder, (Consumer<Object>) value -> {});
        check(builder.cloned == cloned && builder.drilled == drilled, "Empty entries preserve ordinary/excluded/target-drill dispatch");
        check(builder.input == input && input.get(Capability.CAP).size() == inputs.length, "Input recipe content is untouched");
    }

    private static Method inputProbe(ClassNode node, String suffix) throws Exception {
        var source = node.methods.stream().filter(method -> method.name.equals("init$lambda$4")).findFirst().orElseThrow();
        var method = new MethodNode(source.access, source.name, source.desc, source.signature,
                source.exceptions.toArray(String[]::new));
        source.accept(method);
        var cloneStart = java.util.Arrays.stream(method.instructions.toArray())
                .filter(instruction -> instruction instanceof FieldInsnNode field
                        && field.owner.endsWith("/GTLAddRecipesTypes")).findFirst().orElseThrow();
        // The output-building suffix remains production bytecode; this probe observes entry into that suffix.
        for (var instruction = cloneStart; instruction != null;) {
            var next = instruction.getNext();
            method.instructions.remove(instruction);
            instruction = next;
        }
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                WorldLoadCompatibilityRegression.class.getName().replace('.', '/'), "recordClone", "(L" + PREFIX + "Builder;)V", false));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        for (var instruction : method.instructions)
            if (instruction instanceof MethodInsnNode call && call.owner.equals(node.name)
                    && call.getOpcode() == Opcodes.INVOKESPECIAL) call.setOpcode(Opcodes.INVOKEVIRTUAL);
        var mapping = Map.of(
                node.name, PREFIX + "Sky",
                "com/gregtechceu/gtceu/data/recipe/builder/GTRecipeBuilder", PREFIX + "Builder",
                "com/gregtechceu/gtceu/api/capability/recipe/ItemRecipeCapability", PREFIX + "Capability",
                "com/gregtechceu/gtceu/api/recipe/content/Content", PREFIX + "Content",
                "net/minecraft/world/item/crafting/Ingredient", PREFIX + "Ingredient",
                "net/minecraft/world/item/ItemStack", PREFIX + "Stack",
                "net/minecraft/world/item/Item", "java/lang/Object",
                "it/unimi/dsi/fastutil/objects/ObjectOpenCustomHashSet", "java/util/HashSet",
                "kotlin/jvm/internal/Intrinsics", WorldLoadCompatibilityRegression.class.getName().replace('.', '/'));
        return probe(method, suffix, mapping).getMethod("probe", Builder.class, Consumer.class);
    }

    private static void aggregation() throws Exception {
        var upstream = readJar("gtlcore-1.2.3.3-fix3.jar", "org/gtlcore/gtlcore/common/data/machines/MultiBlockMachineA");
        check(upstream.methods.stream().anyMatch(method -> {
            var fields = java.util.Arrays.stream(method.instructions.toArray()).filter(FieldInsnNode.class::isInstance)
                    .map(FieldInsnNode.class::cast).map(field -> field.name).toList();
            return fields.contains("ITEM_IMPORT_BUS") && fields.contains("HUGE_ITEM_IMPORT_BUS");
        }), "Current GTLCore structure contains both ordinary and huge input-bus alternatives");
        var mixin = readResource("com/gtl/enhancedcore/mixin/gtlcore/AggregationDevicePatternBufferMixin");
        var method = mixin.methods.stream().filter(value -> value.name.equals("isItemImportBusOnly")).findFirst().orElseThrow();
        var mapping = Map.of(
                "com/gregtechceu/gtceu/api/pattern/TraceabilityPredicate", PREFIX + "ItemPredicate",
                "com/gregtechceu/gtceu/api/pattern/predicates/SimplePredicate", PREFIX + "Simple",
                "com/gregtechceu/gtceu/api/machine/multiblock/PartAbility", PREFIX + "Ability",
                "com/lowdragmc/lowdraglib/utils/BlockInfo", PREFIX + "Info",
                "net/minecraft/world/level/block/state/BlockState", PREFIX + "State",
                "net/minecraft/world/level/block/state/BlockBehaviour$BlockStateBase", PREFIX + "State",
                "net/minecraft/world/level/block/Block", PREFIX + "Block");
        Method matcher = probe(method, "Aggregation", mapping).getMethod("probe", ItemPredicate.class);
        var ordinary = new Simple(true);
        var huge = new Simple(true);
        var output = new Simple(false);
        check((boolean) matcher.invoke(null, new ItemPredicate(ordinary)), "Original single input-bus rule remains supported");
        check((boolean) matcher.invoke(null, new ItemPredicate(ordinary, huge)), "Updated two-branch input-bus rule is recognized");
        check(!(boolean) matcher.invoke(null, new ItemPredicate(ordinary, output)), "Mixed output/casing candidates remain closed");
        var limited = new ItemPredicate(ordinary, huge);
        limited.limited.add(ordinary);
        check(!(boolean) matcher.invoke(null, limited), "Limited rules remain unchanged");
        var controller = new ItemPredicate(ordinary, huge);
        controller.isController = true;
        check(!(boolean) matcher.invoke(null, controller), "Controller position remains unchanged");
    }

    private static Class<?> probe(MethodNode method, String suffix, Map<String, String> mapping) {
        method.name = "probe";
        method.access = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC;
        method.localVariables = null;
        var node = new ClassNode();
        node.version = Opcodes.V21;
        node.access = Opcodes.ACC_PUBLIC;
        node.name = PREFIX + "Probe" + suffix;
        node.superName = "java/lang/Object";
        node.methods.add(method);
        var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(new ClassRemapper(writer, new SimpleRemapper(mapping)));
        byte[] bytes = writer.toByteArray();
        return new ClassLoader(WorldLoadCompatibilityRegression.class.getClassLoader()) {
            Class<?> define() { return defineClass(null, bytes, 0, bytes.length); }
        }.define();
    }

    private static ClassNode readJar(String jar, String name) throws Exception {
        try (var zip = new ZipFile(Path.of("libs", jar).toFile()); var input = zip.getInputStream(zip.getEntry(name + ".class"))) {
            return read(input);
        }
    }
    private static ClassNode readResource(String name) throws Exception {
        try (var input = WorldLoadCompatibilityRegression.class.getClassLoader().getResourceAsStream(name + ".class")) { return read(input); }
    }
    private static ClassNode read(InputStream input) throws Exception {
        var node = new ClassNode();
        new ClassReader(input).accept(node, 0);
        return node;
    }
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    public static void recordClone(Builder builder) { builder.cloned++; }
    public static void checkNotNullParameter(Object value, String name) { java.util.Objects.requireNonNull(value, name); }
    public static final class Builder {
        public final Map<Capability, List<Content>> input;
        int cloned, drilled;
        Builder(Ingredient... values) { input = Map.of(Capability.CAP, java.util.Arrays.stream(values).map(Content::new).toList()); }
    }
    public static final class Content { public final Object content; Content(Object value) { content = value; } }
    public static final class Capability {
        public static final Capability CAP = new Capability();
        public Object of(Object value) { return value; }
    }
    public static final class Ingredient {
        private final Stack[] stacks;
        Ingredient(Stack... values) { stacks = values; }
        public Stack[] m_43908_() { return stacks; }
    }
    public static final class Stack {
        private final Object item;
        Stack(Object value) { item = value; }
        public Object m_41720_() { return item; }
        public boolean m_150930_(Object value) { return item == value; }
    }
    public static final class Sky {
        public static final Sky INSTANCE = new Sky();
        final Stack target = new Stack(new Object()), excluded = new Stack(new Object());
        public HashSet<Stack> getExceptDrills() { return new HashSet<>(List.of(excluded)); }
        public Stack getTargetDrill() { return target; }
        public void genTargetDrillRecipe(Builder builder, Consumer<?> provider) { builder.drilled++; }
    }
    public static final class ItemPredicate {
        public final List<Simple> common;
        public final List<Simple> limited = new java.util.ArrayList<>();
        public boolean isController;
        ItemPredicate(Simple... values) { common = List.of(values); }
    }
    public static final class Simple {
        public final Supplier<Info[]> candidates;
        Simple(boolean input) { candidates = () -> new Info[] {new Info(new State(new Block(input)))}; }
    }
    public record Info(State state) { public State getBlockState() { return state; } }
    public record State(Block block) { public Block getBlock() { return block; } }
    public record Block(boolean input) {}
    public static final class Ability {
        public static final Ability IMPORT_ITEMS = new Ability();
        public boolean isApplicable(Block block) { return block.input(); }
    }
}
