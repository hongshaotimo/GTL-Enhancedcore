package com.gtl.enhancedcore.common.integration;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;

/** Adapts the thirteen ADD 3.3.1Custom reads to GTLCore's categorized, live configuration. */
public final class AddConfigFieldCompatibility {
    public static final String MARKER = "com.gtl.enhancedcore.mixin.gtladditions.AddConfigFieldCompatibilityMixin";
    public static final String CONFIG = "org/gtlcore/gtlcore/config/ConfigHolder";
    public static final String CONTENT = CONFIG + "$ContentOptions";
    private static final String PREFIX = "com/gtladd/gtladditions/";
    private static final String CONSUMER = "(Ljava/util/function/Consumer;)V";
    private static final Map<String, Map<String, Integer>> EXPECTED = Map.of(
            PREFIX + "common/machine/multiblock/MultiBlockMachine", Map.of("<clinit>()V/enableSkyBlokeMode:Z", 3),
            PREFIX + "common/modify/RecipesModify", Map.of("init()V/enableSkyBlokeMode:Z", 1),
            PREFIX + "common/modify/SkyTearsAndGregHeart", Map.of("init" + CONSUMER + "/enableSkyBlokeMode:Z", 1,
                    "<clinit>()V/durationMultiplier:D", 1),
            PREFIX + "data/recipes/IntegratedOreProcessor", Map.of("<clinit>()V/oreMultiplier:I", 1),
            PREFIX + "data/recipes/Misc", Map.of("init" + CONSUMER + "/enableSkyBlokeMode:Z", 2),
            PREFIX + "data/recipes/NewMultiBlockMachineController", Map.of("init" + CONSUMER + "/enableSkyBlokeMode:Z", 2,
                    "init$lambda$44(Lcom/gregtechceu/gtceu/api/recipe/ResearchRecipeBuilder$StationRecipeBuilder;)"
                            + "Lcom/gregtechceu/gtceu/api/recipe/ResearchRecipeBuilder$StationRecipeBuilder;/enableSkyBlokeMode:Z", 1),
            PREFIX + "utils/ThreadMultiplierStrategy", Map.of(
                    "getAdditionalMultiplier(Lcom/gregtechceu/gtceu/api/machine/MultiblockMachineDefinition;)I/durationMultiplier:D", 1));
    private static final Map<String, String> FIELDS = Map.of("enableSkyBlokeMode", "Z", "oreMultiplier", "I", "durationMultiplier", "D");

    private AddConfigFieldCompatibility() {}

    public static void postApply(String target, ClassNode node, String mixin) {
        if (!MARKER.equals(mixin)) return;
        if (!target.replace('.', '/').equals(node.name)) throw new IllegalStateException("ADD config target mismatch: " + target);
        transform(node, read(CONFIG), read(CONTENT));
    }

    /** Bytecode-only entry used by the dependency regression; never reads or caches configuration values. */
    public static int transform(ClassNode node, ClassNode config, ClassNode content) {
        Map<String, Integer> expected = EXPECTED.get(node.name);
        if (expected == null) throw new IllegalStateException("Unexpected ADD config target: " + node.name);
        if (!CONFIG.equals(config.name) || !CONTENT.equals(content.name)) throw new IllegalStateException("Unexpected GTLCore config schema");
        if (config.fields.stream().noneMatch(value -> value.name.equals("INSTANCE") && value.desc.equals("L" + CONFIG + ";")
                && (value.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC)) == (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC)))
            throw new IllegalStateException("GTLCore config singleton field changed");
        if (!field(config, "content", "L" + CONTENT + ";")) throw new IllegalStateException("Missing GTLCore content category");
        for (var entry : FIELDS.entrySet()) {
            if (config.fields.stream().anyMatch(value -> value.name.equals(entry.getKey())))
                throw new IllegalStateException("Unexpected flat GTLCore config field: " + entry.getKey());
            if (!field(content, entry.getKey(), entry.getValue()))
                throw new IllegalStateException("GTLCore content field changed: " + entry.getKey());
        }
        Map<String, Integer> actual = new LinkedHashMap<>();
        for (var method : node.methods) for (var instruction : method.instructions) {
            if (!(instruction instanceof FieldInsnNode access) || !CONFIG.equals(access.owner) || access.name.equals("INSTANCE")) continue;
            if (access.getOpcode() != Opcodes.GETFIELD || !access.desc.equals(FIELDS.get(access.name)))
                throw new IllegalStateException("Unexpected ADD config access: " + access.name + access.desc);
            actual.merge(method.name + method.desc + "/" + access.name + ":" + access.desc, 1, Integer::sum);
        }
        if (!expected.equals(actual)) throw new IllegalStateException("ADD config accesses changed: " + node.name + " " + actual);
        int changed = 0;
        for (var method : node.methods) for (var instruction : method.instructions.toArray()) {
            if (!(instruction instanceof FieldInsnNode access) || !CONFIG.equals(access.owner) || !FIELDS.containsKey(access.name)) continue;
            method.instructions.insertBefore(access, new FieldInsnNode(Opcodes.GETFIELD, CONFIG, "content", "L" + CONTENT + ";"));
            access.owner = CONTENT;
            changed++;
        }
        return changed;
    }

    private static boolean field(ClassNode node, String name, String descriptor) {
        return node.fields.stream().anyMatch(value -> value.name.equals(name) && value.desc.equals(descriptor)
                && (value.access & Opcodes.ACC_PUBLIC) != 0 && (value.access & Opcodes.ACC_STATIC) == 0);
    }

    private static ClassNode read(String name) {
        try (InputStream input = AddConfigFieldCompatibility.class.getClassLoader().getResourceAsStream(name + ".class")) {
            if (input == null) throw new IllegalStateException("Missing GTLCore config bytecode: " + name);
            ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return node;
        } catch (IOException failure) { throw new IllegalStateException("Cannot read GTLCore config bytecode", failure); }
    }
}
