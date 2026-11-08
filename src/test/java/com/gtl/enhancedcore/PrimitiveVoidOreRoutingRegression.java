package com.gtl.enhancedcore;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gtl.enhancedcore.common.config.ForeignMachineMixinPolicy;
import com.gtl.enhancedcore.common.config.MachineOverrideConfig.Settings;
import com.gtl.enhancedcore.common.recipe.PrimitiveVoidOreRouting;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Pins the three circuit routes to the installed GTLCore and GTCEu ABI. */
final class PrimitiveVoidOreRoutingRegression {
    private static int assertions;
    private static final String GTL = "libs/gtlcore-1.2.3.3-fix3.jar";
    private static final String GTCEU = "libs/gtceu-1.20.1-1.4.4.jar";

    static int run() throws Exception {
        assertions = 0;
        var data = new CompoundTag();
        for (int circuit = 1; circuit <= 3; circuit++) {
            data.putInt(PrimitiveVoidOreRouting.TARGET_DATA, circuit);
            var recipe = recipe(data.copy());
            check(PrimitiveVoidOreRouting.target(recipe) == circuit, "Recipe data retains circuit route " + circuit);
        }
        check(PrimitiveVoidOreRouting.target(null) == 0, "No active recipe has no mining route");
        data.putInt(PrimitiveVoidOreRouting.TARGET_DATA, 4);
        check(PrimitiveVoidOreRouting.target(recipe(data)) == 0, "Out-of-range route cannot select a pool");

        try (ZipFile gtl = new ZipFile(GTL); ZipFile gtceu = new ZipFile(GTCEU)) {
            var misc = read(gtl.getInputStream(gtl.getEntry(
                    "org/gtlcore/gtlcore/data/recipe/Misc.class")));
            var registration = method(misc, "init", "(Ljava/util/function/Consumer;)V");
            int saves = 0;
            boolean inside = false;
            for (var instruction = registration.instructions.getFirst(); instruction != null; instruction = instruction.getNext()) {
                if (instruction instanceof FieldInsnNode field && field.owner.equals("org/gtlcore/gtlcore/common/data/GTLRecipeTypes")) {
                    if (field.name.equals("PRIMITIVE_VOID_ORE_RECIPES")) inside = true;
                    if (field.name.equals("WOOD_DISTILLATION_RECIPES")) inside = false;
                }
                if (inside && instruction instanceof MethodInsnNode call && call.owner.equals("com/gregtechceu/gtceu/data/recipe/builder/GTRecipeBuilder")
                        && call.name.equals("save") && call.desc.equals("(Ljava/util/function/Consumer;)V")) saves++;
            }
            check(saves == 1, "Upstream primitive recipe save is uniquely isolated before wood distillation");

            var primitive = read(gtl.getInputStream(gtl.getEntry(
                    "org/gtlcore/gtlcore/common/machine/multiblock/noenergy/PrimitiveOreMachine.class")));
            var work = method(primitive, "onWorking", "()Z");
            check(calls(work, "net/minecraft/world/level/Level", "m_46472_") == 3,
                    "Upstream random output branches on exactly three Level.dimension calls");

            var definition = read(gtl.getInputStream(gtl.getEntry(
                    "org/gtlcore/gtlcore/common/data/machines/MultiBlockMachineB.class")));
            var pattern = method(definition, "lambda$static$12",
                    "(Lcom/gregtechceu/gtceu/api/machine/MultiblockMachineDefinition;)Lcom/gregtechceu/gtceu/api/pattern/BlockPattern;");
            check(calls(pattern, "com/gregtechceu/gtceu/api/pattern/Predicates", "abilities") == 2,
                    "Primitive controller X predicate retains output and fluid input ability branches");

            var predicates = read(gtceu.getInputStream(gtceu.getEntry("com/gregtechceu/gtceu/api/pattern/Predicates.class")));
            var ability = method(predicates, "abilities", "([Lcom/gregtechceu/gtceu/api/machine/multiblock/PartAbility;)Lcom/gregtechceu/gtceu/api/pattern/TraceabilityPredicate;");
            check(calls(ability, "java/util/stream/Stream", "flatMap") == 1,
                    "GTCEu abilities varargs flatten to one selectable predicate candidate set");
        }

        var mixins = JsonParser.parseString(Files.readString(Path.of("src/main/resources/gtl_enhancedcore.mixins.json")))
                .getAsJsonObject().getAsJsonArray("mixins");
        for (String name : List.of("RecipeMixin", "DimensionMixin", "RecipeTypeMixin", "StructureMixin", "TooltipMixin")) {
            String id = "gtlcore.PrimitiveVoidOre" + name;
            check(mixins.asList().stream().filter(value -> id.equals(value.getAsString())).count() == 1,
                    "Route mixin registered once: " + id);
            String className = "com/gtl/enhancedcore/mixin/" + id.replace('.', '/') + ".class";
            check(PrimitiveVoidOreRoutingRegression.class.getClassLoader().getResource(className) != null,
                    "Route mixin class is compiled: " + id);
            check(ForeignMachineMixinPolicy.shouldApply(new Settings(true, true),
                            "com.gtl.enhancedcore.mixin." + id), "Route follows enhancement-on setting: " + id);
            check(!ForeignMachineMixinPolicy.shouldApply(new Settings(true, false),
                            "com.gtl.enhancedcore.mixin." + id), "Route follows enhancement-off setting: " + id);
        }
        return assertions;
    }

    private static GTRecipe recipe(CompoundTag data) {
        return new GTRecipe(null, new ResourceLocation("gtlcore", "primitive_void_ore/fixture"),
                Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                List.of(), List.of(), data, 200, false);
    }

    private static ClassNode read(InputStream input) throws Exception {
        try (input) { var node = new ClassNode(); new ClassReader(input).accept(node, 0); return node; }
    }

    private static MethodNode method(ClassNode owner, String name, String descriptor) {
        var found = owner.methods.stream().filter(candidate -> candidate.name.equals(name) && candidate.desc.equals(descriptor)).toList();
        check(found.size() == 1, "ABI method resolves uniquely: " + owner.name + "." + name + descriptor);
        return found.getFirst();
    }

    private static int calls(MethodNode method, String owner, String name) {
        int count = 0;
        for (var instruction = method.instructions.getFirst(); instruction != null; instruction = instruction.getNext())
            if (instruction instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name)) count++;
        return count;
    }

    private static void check(boolean valid, String message) {
        if (!valid) throw new AssertionError(message);
        assertions++;
    }
}
