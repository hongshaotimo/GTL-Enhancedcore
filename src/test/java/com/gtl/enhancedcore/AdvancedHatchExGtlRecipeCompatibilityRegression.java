package com.gtl.enhancedcore;

import com.gtl.enhancedcore.common.integration.AdvancedHatchExGtlRecipeCompatibility;
import com.gtl.enhancedcore.common.integration.AdvancedHatchLaserRecipeCompatibility;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicVerifier;

/** Executes the actual fix9 loops and pair dispatcher with registration-shaped machine fixtures. */
public final class AdvancedHatchExGtlRecipeCompatibilityRegression {
    private static int assertions;
    private static final Map<Object, String> MACHINES = new IdentityHashMap<>();
    private static final List<String> RECIPES = new ArrayList<>();
    private static boolean currentAdditions;
    private static final String AH = "com/xingmot/gtmadvancedhatch/common/data/AHMachines";
    private static final String CORE = "org/gtlcore/gtlcore/common/data/GTLMachines";
    private static final String EX = "com/xingmot/gtmadvancedhatch/integration/gtlcore/ExGTLMachines";
    private static final String LDLIB = "com/lowdragmc/lowdraglib/LDLib";

    public static int run() throws Exception {
        assertions = 0;
        try (ZipFile zip = new ZipFile("libs/gtmadvancedhatch-0.1.10-fix2.jar")) {
            check(zip.getEntry(AdvancedHatchLaserRecipeCompatibility.EX_TARGET + ".class") == null,
                    "Compile dependency fix2 has no ExGTLRecipes; optional pseudo target remains absent");
        }
        int total = assertions;
        String mods = System.getenv("GTLE_AUDIT_MODS");
        if (mods != null && !mods.isBlank()) {
            Path runtime = Path.of(mods, "GTMAdvancedHatch-1.20.1-0.1.10 fix9.jar");
            if (Files.isRegularFile(runtime)) total += run(runtime);
        }
        return total;
    }

    public static int run(Path jar) throws Exception {
        assertions = 0;
        int[] shared = {4, 5, 6, 14};
        check(Arrays.equals(AdvancedHatchExGtlRecipeCompatibility.withoutEv(shared), new int[]{5, 6, 14})
                && Arrays.equals(shared, new int[]{4, 5, 6, 14}), "Only EV is removed and the shared registration tiers remain unchanged");
        ClassNode node = read(jar);
        MethodNode init = named(node, "initRecipes");
        var original = Arrays.asList(init.instructions.toArray());
        check(AdvancedHatchLaserRecipeCompatibility.transform(node) == 6, "All three Core input/output conversion pairs use the filtered list");
        var added = Arrays.stream(init.instructions.toArray()).filter(value -> !original.contains(value)).toList();
        check(added.size() == 1 && added.getFirst() instanceof MethodInsnNode call && call.name.equals("withoutEv") && call.desc.equals("([I)[I"),
                "Only one same-stack-shape array filter is inserted");
        check(Arrays.stream(init.instructions.toArray()).filter(original::contains).toList().equals(original),
                "Both actual loops, all six amperages and original argument construction remain unchanged");
        var frames = new Analyzer<>(new BasicVerifier()).analyze(node.name, init);
        check(frames[init.instructions.indexOf(added.getFirst())].getStackSize() == 1,
                "The filter consumes and returns one array without changing frames or maximum stack");
        ClassWriter writer = new ClassWriter(0); node.accept(writer);
        ClassNode roundtrip = new ClassNode(); new ClassReader(writer.toByteArray()).accept(roundtrip, 0);
        new Analyzer<>(new BasicVerifier()).analyze(roundtrip.name, named(roundtrip, "initRecipes"));
        check(true, "Actual transformed init survives ClassWriter(0) and stack/local verification");
        for (boolean high : new boolean[]{false, true}) for (boolean add : new boolean[]{false, true}) {
            execute(jar, false, high, add);
            execute(jar, true, high, add);
        }
        boolean duplicate = false;
        try { AdvancedHatchLaserRecipeCompatibility.transform(node); } catch (IllegalStateException expected) { duplicate = true; }
        check(duplicate, "Duplicate filtering is rejected");
        ClassNode drift = read(jar);
        for (var instruction : named(drift, "initRecipes").instructions)
            if (instruction instanceof FieldInsnNode field && field.name.equals("WIRELESS_ENERGY_INPUT_HATCH_262144A")) field.name += "_changed";
        boolean rejected = false;
        try { AdvancedHatchLaserRecipeCompatibility.transform(drift); } catch (IllegalStateException expected) { rejected = true; }
        check(rejected, "Changed source inventories fail before mutation");
        return assertions;
    }

    private static void execute(Path jar, boolean transformed, boolean high, boolean additions) throws Exception {
        MACHINES.clear(); RECIPES.clear();
        currentAdditions = additions;
        ClassNode node = read(jar);
        if (transformed) AdvancedHatchLaserRecipeCompatibility.transform(node);
        // Preserve actual init and pair methods; replace only final external builder transaction with observation.
        MethodNode terminal = named(node, "registerLaserRecipe");
        check(terminal.desc.equals(AdvancedHatchExGtlRecipeCompatibility.RECIPE_DESCRIPTOR), "Terminal parameter ABI is audited");
        long machineInputs = Arrays.stream(terminal.instructions.toArray()).filter(value -> value instanceof MethodInsnNode call
                && call.name.equals("inputItems") && call.desc.startsWith("(L" + AdvancedHatchExGtlRecipeCompatibility.MACHINE + ";I)")).count();
        check(machineInputs == 3, "Terminal recipe consumes the passed source plus original accelerator and transformer");
        terminal.instructions.clear(); terminal.tryCatchBlocks.clear(); terminal.localVariables = null;
        terminal.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        terminal.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1)); terminal.instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
        terminal.instructions.add(new VarInsnNode(Opcodes.ALOAD, 3)); terminal.instructions.add(new VarInsnNode(Opcodes.ALOAD, 4));
        terminal.instructions.add(new VarInsnNode(Opcodes.ALOAD, 5)); terminal.instructions.add(new VarInsnNode(Opcodes.ILOAD, 6));
        terminal.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                "com/gtl/enhancedcore/AdvancedHatchExGtlRecipeCompatibilityRegression", "record",
                "(Ljava/util/function/Consumer;IILjava/lang/String;Ljava/lang/Object;Ljava/lang/Object;I)V", false));
        terminal.instructions.add(new InsnNode(Opcodes.RETURN)); terminal.maxStack = 7; terminal.maxLocals = 7;
        var classes = new HashMap<String, byte[]>();
        ClassNode machine = plain(AdvancedHatchExGtlRecipeCompatibility.MACHINE);
        classes.put(machine.name, bytes(machine));
        Map<String, ClassNode> holders = new HashMap<>();
        for (var instruction : named(node, "initRecipes").instructions) if (instruction instanceof FieldInsnNode field) {
            var holder = holders.computeIfAbsent(field.owner, AdvancedHatchExGtlRecipeCompatibilityRegression::plain);
            if (holder.fields.stream().noneMatch(value -> value.name.equals(field.name)))
                holder.fields.add(new FieldNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, field.name, field.desc, null, null));
        }
        holders.forEach((name, holder) -> classes.put(name, bytes(holder)));
        ClassNode ldlib = plain(LDLIB);
        ldlib.fields.add(new FieldNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "loaded", "Z", null, null));
        MethodNode isLoaded = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "isModLoaded", "(Ljava/lang/String;)Z", null, null);
        isLoaded.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, LDLIB, "loaded", "Z")); isLoaded.instructions.add(new InsnNode(Opcodes.IRETURN));
        isLoaded.maxStack = 1; isLoaded.maxLocals = 1; ldlib.methods.add(isLoaded); classes.put(LDLIB, bytes(ldlib));
        classes.put(node.name, bytes(node));
        var loader = new FixtureLoader(classes);
        Class<?> machineClass = loader.loadClass(machine.name.replace('/', '.'));
        for (var entry : holders.entrySet()) {
            Class<?> type = loader.loadClass(entry.getKey().replace('/', '.'));
            for (var field : entry.getValue().fields) {
                if (field.desc.equals("[I")) {
                    type.getField(field.name).set(null, field.name.equals("NET_HIGH_TIERS") ? IntStream.rangeClosed(4, 14).toArray()
                            : high ? new int[]{13, 14} : new int[]{7, 8});
                } else {
                    Object array = Array.newInstance(machineClass, 15);
                    int minimum = entry.getKey().equals(CORE) || entry.getKey().equals(EX) && additions ? 5
                            : field.name.matches(".*(16777216|67108864|67108863|2147483647)A") ? high ? 13 : 7 : 4;
                    int maximum = entry.getKey().equals(EX) && !additions || entry.getKey().equals(AH)
                            && field.name.matches(".*(16777216|67108864|2147483647)A") ? high ? 14 : 8 : 14;
                    for (int tier = minimum; tier <= maximum; tier++) {
                        Object definition = machineClass.getConstructor().newInstance(); Array.set(array, tier, definition);
                        MACHINES.put(definition, field.name + ":" + tier);
                    }
                    type.getField(field.name).set(null, array);
                }
            }
        }
        loader.loadClass(LDLIB.replace('/', '.')).getField("loaded").setBoolean(null, additions);
        try {
            loader.loadClass(node.name.replace('/', '.')).getMethod("initRecipes", Consumer.class).invoke(null, (Consumer<Object>) ignored -> {});
            check(transformed, "Unfixed actual dispatcher must expose the missing EV source");
        } catch (InvocationTargetException failure) {
            if (transformed) throw failure;
            check(failure.getCause() instanceof IllegalStateException && failure.getCause().getMessage().contains("missing source"),
                    "Original actual dispatcher reproduces a null source in the EV 262144A pair");
            return;
        } finally { MACHINES.clear(); }
        check(RECIPES.size() == 72, "All sixty IV..MAX Core conversions and twelve highest-tier conversions execute");
        for (int amperage : new int[]{262144, 1048576, 4194304, 16777216, 67108864, Integer.MAX_VALUE})
            check(RECIPES.stream().anyMatch(value -> value.startsWith(amperage + "/")), "Amperage " + amperage + " remains registered");
        check(RECIPES.stream().noneMatch(value -> value.contains("/4/")), "Only unsupported EV conversions disappear");
        for (int tier : high ? new int[]{13, 14} : new int[]{7, 8})
            check(RECIPES.stream().filter(value -> value.contains("/" + tier + "/")).count() == 12,
                    "Highest target tier " + tier + " retains all Core and highest conversions");
    }

    public static void record(Consumer<?> provider, int tier, int amperage, String direction, Object hatch, Object ingredient, int count) {
        if (ingredient == null) throw new IllegalStateException("missing source " + amperage + "/" + tier);
        check(hatch != null && MACHINES.containsKey(hatch) && MACHINES.containsKey(ingredient), "Every actual dispatcher call supplies registered source and output definitions");
        boolean highest = amperage > 4194304;
        int sourceTier = highest && amperage != Integer.MAX_VALUE && currentAdditions ? tier - 1 : tier;
        check(MACHINES.get(ingredient).endsWith(":" + sourceTier) && MACHINES.get(hatch).endsWith(":" + tier),
                "Actual argument construction retains ADD's previous-tier source and current-tier output");
        check(count == (highest && amperage != Integer.MAX_VALUE && MACHINES.get(ingredient).endsWith(":" + (tier - 1)) ? 16
                : amperage == Integer.MAX_VALUE ? 16 : 4), "Original ADD-dependent ingredient quantities remain intact");
        RECIPES.add(amperage + "/" + tier + "/" + direction);
    }

    private static ClassNode plain(String name) {
        ClassNode node = new ClassNode(); node.name = name; node.superName = "java/lang/Object"; node.version = Opcodes.V17; node.access = Opcodes.ACC_PUBLIC;
        MethodNode constructor = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0)); constructor.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false));
        constructor.instructions.add(new InsnNode(Opcodes.RETURN)); constructor.maxStack = 1; constructor.maxLocals = 1; node.methods.add(constructor); return node;
    }
    private static byte[] bytes(ClassNode node) { ClassWriter writer = new ClassWriter(0); node.accept(writer); return writer.toByteArray(); }
    private static final class FixtureLoader extends ClassLoader {
        private final Map<String, byte[]> classes;
        FixtureLoader(Map<String, byte[]> classes) { super(AdvancedHatchExGtlRecipeCompatibilityRegression.class.getClassLoader()); this.classes = classes; }
        @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> type = findLoadedClass(name);
                if (type == null) { byte[] data = classes.get(name.replace('.', '/')); type = data == null ? super.loadClass(name, false) : defineClass(name, data, 0, data.length); }
                if (resolve) resolveClass(type); return type;
            }
        }
    }
    private static ClassNode read(Path jar) throws Exception {
        try (ZipFile zip = new ZipFile(jar.toFile()); InputStream input = zip.getInputStream(zip.getEntry(AdvancedHatchLaserRecipeCompatibility.EX_TARGET + ".class"))) {
            ClassNode node = new ClassNode(); new ClassReader(input).accept(node, 0); return node;
        }
    }
    private static MethodNode named(ClassNode node, String name) { return node.methods.stream().filter(method -> method.name.equals(name)).findFirst().orElseThrow(); }
    private static void check(boolean value, String message) { assertions++; if (!value) throw new AssertionError(message); }
}
