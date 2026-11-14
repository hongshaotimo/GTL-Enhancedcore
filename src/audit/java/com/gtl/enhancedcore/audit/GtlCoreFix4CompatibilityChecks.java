package com.gtl.enhancedcore.audit;

import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Checks only the changed integration points, using the actual dependency and compiled classes. */
public final class GtlCoreFix4CompatibilityChecks {
    private static final String CORE = "org/gtlcore/gtlcore/";
    private static final String LOCAL = "com/gtl/enhancedcore/";
    private static final String FORMATION = "(Lnet/minecraft/server/level/ServerLevel;ZLjava/lang/String;)Z";

    public static void main(String[] args) throws Exception {
        try (var core = new ZipFile("libs/gtlcore-1.2.3.3-fix4.jar");
                var gtceu = new ZipFile("libs/gtceu-1.20.1-1.4.4.jar")) {
            var detection = read(core, CORE + "api/pattern/StructureDetection");
            var detect = method(detection, "detect", "(Lnet/minecraft/world/entity/player/Player;"
                    + "Lcom/gregtechceu/gtceu/api/machine/feature/multiblock/IMultiController;ZI)V");
            var check = method(detection, "check", "(Lcom/gregtechceu/gtceu/api/machine/feature/multiblock/IMultiController;"
                    + "Lcom/gregtechceu/gtceu/api/pattern/BlockPattern;ZI)Ljava/util/List;");
            require((detect.access & Opcodes.ACC_STATIC) != 0 && (check.access & Opcodes.ACC_STATIC) != 0,
                    "Structure detection must remain static");
            require(calls(check, "com/gregtechceu/gtceu/api/pattern/BlockPattern", "checkPatternAt") == 1,
                    "Structure detection matcher injection changed");
            var detectionMixin = local("mixin/gtlcore/StructureDetectNormalModeMixin");
            for (var m : detectionMixin.methods) {
                if (m.name.startsWith("gtlEnhancedcore$"))
                    require((m.access & Opcodes.ACC_STATIC) != 0, "Non-static structure detection handler");
            }

            var controller = read(core, CORE + "mixin/gtm/api/machine/MultiblockControllerMachineMixin");
            var update = method(controller, "gtlcore$updateStructure", FORMATION);
            require((update.access & Opcodes.ACC_STATIC) == 0, "Formation helper must be an instance method");
            var invoker = local("mixin/gtceu/StructureSearchFormationInvoker");
            method(invoker, "enhancedcore$updateStructure", FORMATION);
            var confirm = local("common/performance/structure/StructureSearchScheduler").methods.stream()
                    .filter(m -> m.name.equals("confirm")).findFirst().orElseThrow();
            require(calls(confirm, LOCAL + "mixin/gtceu/StructureSearchFormationInvoker", "enhancedcore$updateStructure") == 1,
                    "Final formation must use the synchronous native helper");
            require(calls(confirm, CORE + "api/pattern/PatternRetryController", "gtlcore$queueFormationCheck") == 0,
                    "Final formation must not escape the confirmation scope through another queued task");
            var index = local("mixin/gtceu/StructureWorldIndexMixin");
            require(index.methods.stream().mapToInt(m -> calls(m, CORE + "api/pattern/PatternRetryIndex", "watch")).sum() == 1,
                    "Native retry lifecycle activation missing");
            require(index.methods.stream().mapToInt(m -> calls(m, CORE + "api/pattern/PatternRetryIndex", "remove")).sum() == 1,
                    "Native retry lifecycle removal missing");

            var amount = read(gtceu, "com/gregtechceu/gtceu/integration/ae2/gui/widget/AmountSetWidget");
            method(amount, "<init>", "(IILcom/gregtechceu/gtceu/integration/ae2/gui/widget/ConfigWidget;)V");
            var amountMixin = local("mixin/gtceu/ServerAmountSetWidgetMixin");
            var validation = amountMixin.methods.stream().filter(m -> m.name.contains("preserveLongAmountValidation"))
                    .findFirst().orElseThrow();
            require(calls(validation, "com/lowdragmc/lowdraglib/gui/widget/TextFieldWidget", "setNumbersOnly") == 1,
                    "Server amount validation missing");
            require(validation.instructions.iterator().hasNext(), "Empty server amount validation");
            for (var instruction : validation.instructions) {
                if (instruction instanceof MethodInsnNode call && call.name.equals("setNumbersOnly"))
                    require(call.desc.startsWith("(JJ)"), "Server validation must use long limits");
            }
            var plugin = local("common/performance/PerformanceMixinPlugin");
            var applies = method(plugin, "shouldApplyMixin", "(Ljava/lang/String;Ljava/lang/String;)Z");
            require(calls(applies, "org/spongepowered/asm/mixin/MixinEnvironment", "getSide") == 1,
                    "Server amount mixin needs a physical-side filter");
            boolean server = false;
            for (var instruction : applies.instructions)
                if (instruction instanceof FieldInsnNode field && field.name.equals("SERVER")
                        && field.owner.equals("org/spongepowered/asm/mixin/MixinEnvironment$Side")) server = true;
            require(server, "Server amount mixin must be dedicated-server-only");

            var json = JsonParser.parseString(new String(core.getInputStream(core.getEntry("gtlcore.mixin.json"))
                    .readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            for (String name : List.of("gtm.gui.AmountSetWidgetMixin", "gtm.MultiblockInfoCategoryMixin")) {
                require(json.getAsJsonArray("client").asList().stream().anyMatch(e -> e.getAsString().equals(name)),
                        "Expected client-only upstream GUI mixin: " + name);
                require(json.getAsJsonArray("mixins").asList().stream().noneMatch(e -> e.getAsString().equals(name)),
                        "Upstream GUI mixin is still common: " + name);
            }
        }
        System.out.println("GTLCore fix4 targeted compatibility checks passed (dependency bytecode and compiled integration points).");
    }

    private static ClassNode local(String name) throws Exception {
        return parse(Files.readAllBytes(Path.of("build/classes/java/main", LOCAL + name + ".class")));
    }

    private static ClassNode read(ZipFile jar, String name) throws Exception {
        return parse(jar.getInputStream(jar.getEntry(name + ".class")).readAllBytes());
    }

    private static ClassNode parse(byte[] data) {
        var node = new ClassNode();
        new ClassReader(data).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return node;
    }

    private static MethodNode method(ClassNode node, String name, String descriptor) {
        return node.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(descriptor))
                .findFirst().orElseThrow(() -> new AssertionError(node.name + "." + name + descriptor));
    }

    private static int calls(MethodNode method, String owner, String name) {
        int count = 0;
        for (var instruction : method.instructions)
            if (instruction instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name)) count++;
        return count;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
