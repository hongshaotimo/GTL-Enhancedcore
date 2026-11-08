package com.gtl.enhancedcore;

import com.google.gson.JsonParser;
import com.gtl.enhancedcore.common.integration.AddConfigFieldCompatibility;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Whole-ADD access audit and executable mutable-config check without initializing either dependency. */
public final class AddConfigFieldCompatibilityRegression {
    private static int assertions;

    public static int run() throws Exception {
        assertions = 0;
        var dependencies = JsonParser.parseString(Files.readString(Path.of("gradle/local-dependencies.json"))).getAsJsonObject();
        List<String> jars = dependencies.getAsJsonArray("implementation").asList().stream().map(value -> value.getAsString()).toList();
        String core = jars.stream().filter(value -> value.startsWith("gtlcore-")).findFirst().orElseThrow();
        String add = jars.stream().filter(value -> value.startsWith("gtladditions-")).findFirst().orElseThrow();
        ClassNode config = read(core, AddConfigFieldCompatibility.CONFIG);
        ClassNode content = read(core, AddConfigFieldCompatibility.CONTENT);
        Set<String> targets = new HashSet<>();
        int replacements = 0;
        try (ZipFile zip = new ZipFile(Path.of("libs", add).toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (!entry.getName().endsWith(".class")) continue;
                ClassNode node = new ClassNode();
                try (InputStream input = zip.getInputStream(entry)) { new ClassReader(input).accept(node, 0); }
                if (!oldAccess(node)) continue;
                targets.add(node.name);
                int before = node.methods.stream().mapToInt(method -> method.instructions.size()).sum();
                int count = AddConfigFieldCompatibility.transform(node, config, content);
                replacements += count;
                check(!oldAccess(node), "Every old flat field read is removed from " + node.name);
                check(node.methods.stream().mapToInt(method -> method.instructions.size()).sum() == before + count,
                        "Only one category read is added for each obsolete field in " + node.name);
                for (var method : node.methods) for (var instruction : method.instructions) {
                    if (!(instruction instanceof FieldInsnNode field) || !field.owner.equals(AddConfigFieldCompatibility.CONTENT)) continue;
                    check(field.getPrevious() instanceof FieldInsnNode category
                            && category.owner.equals(AddConfigFieldCompatibility.CONFIG) && category.name.equals("content")
                            && category.getOpcode() == Opcodes.GETFIELD,
                            "Migrated field reads its current content object directly: " + field.name + field.desc);
                }
                fails(() -> AddConfigFieldCompatibility.transform(node, config, content), "Already-transformed or missing access counts fail visibly");
            }
        }
        check(targets.size() == 7 && replacements == 13, "Whole ADD jar contains exactly seven affected classes and thirteen migrated accesses");
        var mixins = JsonParser.parseString(Files.readString(Path.of("src/main/resources/gtl_enhancedcore.mixins.json"))).getAsJsonObject();
        check(mixins.getAsJsonArray("mixins").asList().stream().anyMatch(value -> value.getAsString().equals("gtladditions.AddConfigFieldCompatibilityMixin")),
                "Config compatibility marker is registered");
        ClassNode marker = resource(AddConfigFieldCompatibility.MARKER.replace('.', '/'));
        var annotation = marker.invisibleAnnotations.stream().filter(value -> value.desc.endsWith("/Mixin;")).findFirst().orElseThrow();
        int priority = annotation.values.indexOf("priority");
        check(priority >= 0 && annotation.values.get(priority + 1).equals(500), "Compatibility marker runs after standard upstream mixins");
        int targetIndex = annotation.values.indexOf("targets");
        @SuppressWarnings("unchecked") var declared = (List<String>) annotation.values.get(targetIndex + 1);
        check(new HashSet<>(declared.stream().map(value -> value.replace('.', '/')).toList()).equals(targets), "Marker target inventory exactly matches the whole-jar audit");
        liveReads(config, content);
        ClassNode malformed = read(add, "com/gtladd/gtladditions/common/modify/RecipesModify");
        for (var method : malformed.methods) for (var instruction : method.instructions)
            if (instruction instanceof FieldInsnNode field && field.name.equals("enableSkyBlokeMode")) field.desc = "I";
        fails(() -> AddConfigFieldCompatibility.transform(malformed, config, content), "Descriptor drift is rejected before mutation");
        content.fields.removeIf(field -> field.name.equals("oreMultiplier"));
        fails(() -> AddConfigFieldCompatibility.transform(read(add, "com/gtladd/gtladditions/common/modify/RecipesModify"), config, content),
                "Missing new-schema fields fail visibly");
        return assertions;
    }

    private static void liveReads(ClassNode actualConfig, ClassNode actualContent) throws Exception {
        String rootName = AddConfigFieldCompatibility.CONFIG, contentName = AddConfigFieldCompatibility.CONTENT;
        ClassNode config = plain(rootName);
        config.fields.add(new FieldNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "INSTANCE", "L" + rootName + ";", null, null));
        config.fields.add(new FieldNode(Opcodes.ACC_PUBLIC, "content", "L" + contentName + ";", null, null));
        ClassNode content = plain(contentName);
        content.fields.add(new FieldNode(Opcodes.ACC_PUBLIC, "enableSkyBlokeMode", "Z", null, null));
        ClassNode reader = plain("com/gtladd/gtladditions/common/modify/RecipesModify");
        reader.fields.add(new FieldNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "result", "Z", null, null));
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "init", "()V", null, null);
        method.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, rootName, "INSTANCE", "L" + rootName + ";"));
        method.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, rootName, "enableSkyBlokeMode", "Z"));
        method.instructions.add(new FieldInsnNode(Opcodes.PUTSTATIC, reader.name, "result", "Z"));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        reader.methods.add(method);
        AddConfigFieldCompatibility.transform(reader, actualConfig, actualContent);
        var loader = new TestLoader();
        Class<?> contentClass = loader.add(content), configClass = loader.add(config), readerClass = loader.add(reader);
        Object root = configClass.getConstructor().newInstance(), first = contentClass.getConstructor().newInstance();
        configClass.getField("INSTANCE").set(null, root); configClass.getField("content").set(root, first);
        readerClass.getMethod("init").invoke(null);
        check(!readerClass.getField("result").getBoolean(null), "Migrated read observes initial false");
        contentClass.getField("enableSkyBlokeMode").setBoolean(first, true);
        readerClass.getMethod("init").invoke(null);
        check(readerClass.getField("result").getBoolean(null), "Migrated read observes in-place config mutation");
        configClass.getField("content").set(root, contentClass.getConstructor().newInstance());
        readerClass.getMethod("init").invoke(null);
        check(!readerClass.getField("result").getBoolean(null), "Migrated read observes replacement category without stale caching");
    }

    private static ClassNode plain(String name) {
        ClassNode node = new ClassNode(); node.version = Opcodes.V17; node.access = Opcodes.ACC_PUBLIC; node.name = name; node.superName = "java/lang/Object";
        MethodNode constructor = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        constructor.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false));
        constructor.instructions.add(new InsnNode(Opcodes.RETURN)); node.methods.add(constructor);
        return node;
    }
    private static final class TestLoader extends ClassLoader {
        TestLoader() { super(null); }
        Class<?> add(ClassNode node) { ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer); byte[] bytes = writer.toByteArray(); return defineClass(node.name.replace('/', '.'), bytes, 0, bytes.length); }
    }
    private static boolean oldAccess(ClassNode node) {
        for (var method : node.methods) for (var instruction : method.instructions)
            if (instruction instanceof FieldInsnNode field && field.owner.equals(AddConfigFieldCompatibility.CONFIG)
                    && Set.of("enableSkyBlokeMode", "oreMultiplier", "durationMultiplier").contains(field.name)) return true;
        return false;
    }
    private static ClassNode read(String jar, String name) throws Exception {
        try (ZipFile zip = new ZipFile(Path.of("libs", jar).toFile()); InputStream input = zip.getInputStream(zip.getEntry(name + ".class"))) {
            ClassNode node = new ClassNode(); new ClassReader(input).accept(node, 0); return node;
        }
    }
    private static ClassNode resource(String name) throws Exception {
        try (InputStream input = AddConfigFieldCompatibilityRegression.class.getClassLoader().getResourceAsStream(name + ".class")) {
            ClassNode node = new ClassNode(); new ClassReader(input).accept(node, 0); return node;
        }
    }
    private interface Checked { void run() throws Exception; }
    private static void fails(Checked action, String message) throws Exception {
        boolean failed = false;
        try { action.run(); } catch (IllegalStateException expected) { failed = true; }
        check(failed, message);
    }
    private static void check(boolean condition, String message) { assertions++; if (!condition) throw new AssertionError(message); }
}
