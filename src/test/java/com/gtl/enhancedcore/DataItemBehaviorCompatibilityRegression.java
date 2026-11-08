package com.gtl.enhancedcore;

import com.google.gson.JsonParser;
import com.gtl.enhancedcore.common.integration.DataItemBehaviorCompatibility;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Pins the actual upstream bytecode behind the narrowly scoped priority compatibility change. */
public final class DataItemBehaviorCompatibilityRegression {
    private static int assertions;

    public static int run() throws Exception {
        assertions = 0;
        var config = JsonParser.parseString(Files.readString(Path.of("gradle/local-dependencies.json"))).getAsJsonObject();
        List<String> jars = config.getAsJsonArray("implementation").asList().stream().map(value -> value.getAsString()).toList();
        String coreJar = jars.stream().filter(value -> value.startsWith("gtlcore-")).findFirst().orElseThrow();
        String addJar = jars.stream().filter(value -> value.startsWith("gtladditions-")).findFirst().orElseThrow();
        ClassNode core = readJar(coreJar, "org/gtlcore/gtlcore/mixin/gtm/item/DataItemBehaviorMixin");
        ClassNode add = readJar(addJar, "com/gtladd/gtladditions/mixin/gtceu/common/item/DataItemBehaviorMixin");
        MethodNode reader = method(core, "gtlcore$readTooltipData");
        check(core.methods.size() == 2, "Core compatibility changes only its sole read-only redirect");
        check(reader.visibleAnnotations.stream().anyMatch(annotation -> annotation.desc.endsWith("/Redirect;")),
                "Core reader remains a redirect");
        check(calls(reader, "net/minecraft/world/item/ItemStack", "getTag", "m_41783_") == 1,
                "Core reader uses nullable non-mutating getTag");
        check(calls(reader, "net/minecraft/world/item/ItemStack", "getOrCreateTag", "m_41784_") == 0,
                "Core reader does not allocate attached NBT");
        MethodNode tooltip = method(add, "appendHoverText");
        check(tooltip.visibleAnnotations.stream().anyMatch(annotation -> annotation.desc.endsWith("/Overwrite;")),
                "ADD tooltip remains its complete overwrite");
        check(calls(tooltip, "net/minecraft/world/item/ItemStack", "getOrCreateTag", "m_41784_") == 4,
                "All four ADD reads are covered by Core's redirect");
        for (String key : List.of("gtceu.tooltip.proxy_bind", "gtceu.tooltip.super_proxy_bind",
                "behavior.data_item.assemblyline.title", "behavior.data_item.assemblyline.data")) {
            check(Arrays.stream(tooltip.instructions.toArray()).anyMatch(instruction ->
                    instruction instanceof org.objectweb.asm.tree.LdcInsnNode constant && key.equals(constant.cst)),
                    "ADD tooltip keeps " + key);
        }
        ClassNode context = readResource("org/spongepowered/asm/mixin/transformer/MixinTargetContext");
        check(calls(method(context, "getPriority"), "org/spongepowered/asm/mixin/transformer/MixinInfo", "getPriority") == 1,
                "Prepared contexts consult the live MixinInfo priority");
        var mixinConfig = JsonParser.parseString(Files.readString(Path.of("src/main/resources/gtl_enhancedcore.mixins.json"))).getAsJsonObject();
        check(mixinConfig.getAsJsonArray("mixins").asList().stream()
                .anyMatch(value -> value.getAsString().equals("gtceu.DataItemBehaviorCompatibilityMixin")),
                "Compatibility marker is registered for common/client loading");
        ClassNode unrelated = new ClassNode();
        unrelated.name = "unrelated/Target";
        DataItemBehaviorCompatibility.preApply("unrelated.Target", unrelated, "unrelated.Mixin");
        check(unrelated.methods.isEmpty(), "Unrelated transforms are unchanged");
        return assertions;
    }

    private static long calls(MethodNode method, String owner, String... names) {
        return Arrays.stream(method.instructions.toArray()).filter(instruction -> instruction instanceof MethodInsnNode call
                && call.owner.equals(owner) && Arrays.asList(names).contains(call.name)).count();
    }

    private static MethodNode method(ClassNode node, String name) {
        return node.methods.stream().filter(method -> method.name.equals(name)).findFirst().orElseThrow();
    }

    private static ClassNode readJar(String jar, String name) throws Exception {
        try (ZipFile zip = new ZipFile(Path.of("libs", jar).toFile()); InputStream stream = zip.getInputStream(zip.getEntry(name + ".class"))) {
            ClassNode result = new ClassNode();
            new ClassReader(stream).accept(result, 0);
            return result;
        }
    }

    private static ClassNode readResource(String name) throws Exception {
        try (InputStream stream = DataItemBehaviorCompatibilityRegression.class.getClassLoader().getResourceAsStream(name + ".class")) {
            ClassNode result = new ClassNode();
            new ClassReader(stream).accept(result, 0);
            return result;
        }
    }

    private static void check(boolean condition, String description) {
        assertions++;
        if (!condition) throw new AssertionError(description);
    }
}
