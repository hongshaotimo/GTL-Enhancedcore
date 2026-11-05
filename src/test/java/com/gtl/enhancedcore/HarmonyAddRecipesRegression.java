package com.gtl.enhancedcore;

import com.google.gson.JsonParser;
import com.gtl.enhancedcore.common.recipe.iv.IvMachineScope;
import com.gtl.enhancedcore.common.util.TooltipPolicy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

/** Audit the installed ADD ABI and native Harmony lifecycles without loading a Minecraft world. */
final class HarmonyAddRecipesRegression {
    private static int assertions;
    private static final String ADD = "com/gtladd/gtladditions/";
    private static final String ASTRO = ADD + "common/machine/multiblock/controller/ArcanicAstrograph";
    private static final String LOGIC = ASTRO + "$Companion$ArcanicAstrographRecipeLogic";
    private static final String HARMONY = "org/gtlcore/gtlcore/common/machine/multiblock/electric/HarmonyMachine";

    static int run() throws Exception {
        assertions = 0;
        try (var additions = new ZipFile("libs/gtladditions-3.3.1Custom.jar");
             var core = new ZipFile("libs/gtlcore-1.2.3.2-fix4.jar")) {
            String kotlin = "kotlinforforge-4.11.0-all.jar";
            var dependencies = JsonParser.parseString(Files.readString(Path.of("gradle/local-dependencies.json"))).getAsJsonObject();
            check(dependencies.getAsJsonArray("compileOnly").asList().stream().anyMatch(value -> value.getAsString().equals(kotlin))
                            && dependencies.getAsJsonArray("implementation").asList().stream().noneMatch(value -> value.getAsString().equals(kotlin)),
                    "ADD's bundled Kotlin ABI is a compiler-only dependency and is not added to runtime packaging");
            var bundledKotlin = additions.getEntry("META-INF/jars/" + kotlin);
            check(bundledKotlin != null, "Current ADD artifact supplies the exact Kotlin compiler ABI dependency");
            try (var stream = additions.getInputStream(bundledKotlin);
                 var localKotlin = new ZipFile("libs/" + kotlin)) {
                var digest = MessageDigest.getInstance("SHA-256");
                check(Arrays.equals(digest.digest(stream.readAllBytes()), digest.digest(Files.readAllBytes(Path.of("libs", kotlin))))
                                && localKotlin.getEntry("kotlin/jvm/functions/Function2.class") != null,
                        "Local Kotlin SHA-256 matches the installed ADD's nested artifact and exposes Function2");
            }
            var harmony = read(core, HARMONY);
            var traversal = read(core, "org/gtlcore/gtlcore/api/recipe/IAdditionalRecipeIterator");
            method(traversal, "setUseDiveIngredientTreeFind", "(Z)V");
            check(Files.readString(Path.of("src/main/java/com/gtl/enhancedcore/common/recipe/HarmonyAddRecipes.java"))
                            .contains("((IAdditionalRecipeIterator) iterator).setUseDiveIngredientTreeFind(true)"),
                    "No-energy Harmony explicitly enables ADD's exhaustive recipe traversal API");
            check(harmony.superName.endsWith("/NoEnergyMultiblockMachine"),
                    "Harmony remains a no-energy machine and cannot use the electric Mutable supplier");
            var astro = read(additions, ASTRO);
            check((astro.access & Opcodes.ACC_FINAL) != 0 && astro.superName.equals(HARMONY),
                    "Arcanic's final native entity retains the Harmony storage and startup lifecycle");
            check(calls(method(astro, "getRecipeLogic", "()L" + LOGIC + ";"), null, "getRecipeLogic")
                            && casts(method(astro, "getRecipeLogic", "()L" + LOGIC + ";"), LOGIC),
                    "Arcanic's covariant getter requires its original final RecipeLogic type");
            var logic = read(additions, LOGIC);
            var cosmos = method(logic, "findCosmosSimulationRecipe", "(L" + ASTRO + ";)Lcom/gregtechceu/gtceu/api/recipe/GTRecipe;");
            check(calls(cosmos, ASTRO, "consumeCosmosStartup") && calls(cosmos, ASTRO, "applyCreateDataOutput")
                            && calls(cosmos, ASTRO, "getHarmonyDuration") && calls(cosmos, ASTRO, "getParallelAmount"),
                    "Current ADD cosmos ABI exposes startup, CreateData, duration and star-array parallel rules");
            check(calls(method(logic, "getRecipe", "()Lcom/gregtechceu/gtceu/api/recipe/GTRecipe;"),
                            LOGIC, "findCompressedAstralArrayRecipe"),
                    "The original hidden compression fallback remains available");
            check(calls(method(astro, "onWorking", "()Z"), null, "handleCompressionWorking")
                            && calls(method(astro, "afterWorking", "()V"), null, "finishCompression"),
                    "Arcanic compression collection and probability settlement remain in the original entity");
            var helper = read(additions, ADD + "utils/RecipeCalculationHelper");
            var greedy = helper.methods.stream().filter(m -> m.name.equals("calculateParallelsWithGreedyAllocation"))
                    .findFirst().orElseThrow();
            check(calls(greedy, "org/gtlcore/gtlcore/api/recipe/IParallelLogic", "getRecipeOutputChance")
                            && calls(greedy, "org/gtlcore/gtlcore/api/recipe/RecipeRunnerHelper", "handleRecipeInput"),
                    "ADD's installed greedy allocator settles chance outputs and consumes inputs itself");
        }
        for (String id : List.of("eye_of_harmony", "arcanic_astrograph")) {
            check(!IvMachineScope.NATIVE_IDS.contains(id)
                            && IvMachineScope.FOREIGN_NATIVE_IDS.values().stream().noneMatch(ids -> ids.contains(id))
                            && !IvMachineScope.ADD_THREAD_ONLY_IDS.contains(id),
                    id + " stays outside every IV or generic electric supplier whitelist");
        }
        check(TooltipPolicy.page("harmony_add").summary().contains("tooltip.gtl_enhancedcore.harmony_add.cross_recipe"),
                "Both controllers expose their capability in the default tooltip page");
        var zh = JsonParser.parseString(Files.readString(Path.of(
                "src/main/resources/assets/gtl_enhancedcore/lang/zh_cn.json"))).getAsJsonObject();
        var en = JsonParser.parseString(Files.readString(Path.of(
                "src/main/resources/assets/gtl_enhancedcore/lang/en_us.json"))).getAsJsonObject();
        check(zh.get("tooltip.gtl_enhancedcore.harmony_add.cross_recipe").getAsString().equals("支持跨配方并行"),
                "Chinese tooltip states the user-requested cross-recipe capability exactly");
        for (String key : List.of("tooltip.gtl_enhancedcore.harmony_add.cross_recipe",
                "tooltip.gtl_enhancedcore.harmony_add.threads", "gtl_enhancedcore.gui.harmony_add_threads",
                "gtl_enhancedcore.gui.harmony_add_active"))
            check(en.has(key) && zh.has(key), "Both languages include " + key);
        return assertions;
    }

    private static ClassNode read(ZipFile jar, String name) throws Exception {
        var entry = jar.getEntry(name + ".class");
        if (entry == null) throw new AssertionError("Missing current dependency class " + name);
        try (var stream = jar.getInputStream(entry)) {
            var node = new ClassNode();
            new ClassReader(stream).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return node;
        }
    }

    private static MethodNode method(ClassNode type, String name, String descriptor) {
        return type.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(descriptor))
                .findFirst().orElseThrow(() -> new AssertionError("Missing current ABI " + type.name + "." + name));
    }

    private static boolean calls(MethodNode method, String owner, String name) {
        for (var instruction : method.instructions)
            if (instruction instanceof MethodInsnNode call && (owner == null || call.owner.equals(owner))
                    && call.name.equals(name)) return true;
        return false;
    }

    private static boolean casts(MethodNode method, String type) {
        for (var instruction : method.instructions)
            if (instruction instanceof TypeInsnNode cast && cast.getOpcode() == Opcodes.CHECKCAST
                    && cast.desc.equals(type)) return true;
        return false;
    }

    private static void check(boolean valid, String message) {
        if (!valid) throw new AssertionError(message);
        assertions++;
    }
}
