package com.gtl.enhancedcore;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import com.gtl.enhancedcore.common.integration.MultiblockPreviewLaziness;

/** Preview ownership, packaging, and real GTCEu state cleanup; GUI injection needs the runtime audit. */
public final class UpstreamPreviewContractRegression {
    private static final Path MAIN = Path.of("src/main/java/com/gtl/enhancedcore");
    private static final Path RESOURCES = Path.of("src/main/resources");
    private static int assertions;

    private UpstreamPreviewContractRegression() {}

    public static void main(String[] args) throws Exception {
        System.out.println("Upstream preview contract passed: " + run() + " assertions (ownership, packaging and nullable state cleanup)");
    }

    static int run() throws Exception {
        assertions = 0;
        localOwnership();
        upstreamPackaging();
        lazyPostApply();
        previewStateCleanup();
        return assertions;
    }

    private static void previewStateCleanup() throws Exception {
        var state = new com.gregtechceu.gtceu.api.pattern.MultiblockState(null, null);
        check(state.cache == null && state.getGlobalCount() == null && state.getLayerCount() == null,
                "GTCEu preview states legitimately have no caches before pattern checking");
        state.getMatchContext().set("retainedPreview", new Object());
        com.gtl.enhancedcore.client.preview.PreviewPatternMemory.releaseState(state);
        check(!state.getMatchContext().containsKey("retainedPreview"),
                "Closing an unchecked preview still releases its match context");
        check(state.cache == null && state.getGlobalCount() == null && state.getLayerCount() == null,
                "Cleanup does not allocate absent pattern-check caches");
        com.gtl.enhancedcore.client.preview.PreviewPatternMemory.releaseState(state);
        com.gtl.enhancedcore.client.preview.PreviewPatternMemory.releaseState(null);

        // Exercise initialized, partially initialized, and repeated cleanup using real GTCEu state.
        state.cache = new it.unimi.dsi.fastutil.longs.LongOpenHashSet(16_384);
        state.cache.add(42L);
        var globalField = state.getClass().getDeclaredField("globalCount");
        globalField.setAccessible(true);
        globalField.set(state, new java.util.HashMap<>());
        state.getGlobalCount().put(null, 7);
        com.gtl.enhancedcore.client.preview.PreviewPatternMemory.releaseState(state);
        check(state.cache.isEmpty() && state.getGlobalCount().isEmpty() && state.getLayerCount() == null,
                "Partial initialization releases existing caches and tolerates the absent layer map");
        var layerField = state.getClass().getDeclaredField("layerCount");
        layerField.setAccessible(true);
        layerField.set(state, new java.util.HashMap<>());
        state.getLayerCount().put(null, 3);
        state.cache.add(99L);
        state.getMatchContext().set("retainedPreview", new Object());
        com.gtl.enhancedcore.client.preview.PreviewPatternMemory.releaseState(state);
        com.gtl.enhancedcore.client.preview.PreviewPatternMemory.releaseState(state);
        check(state.cache.isEmpty() && state.getGlobalCount().isEmpty() && state.getLayerCount().isEmpty()
                        && !state.getMatchContext().containsKey("retainedPreview"),
                "Repeated cleanup of a checked preview releases every retained collection");
    }

    private static void localOwnership() throws Exception {
        try (var files = Files.list(MAIN.resolve("client/preview"))) {
            var names = files.filter(Files::isRegularFile).map(path -> path.getFileName().toString()).toList();
            check(Set.copyOf(names).equals(Set.of("PreviewEffectAnchors.java",
                            "LazyMultiblockPreviewWidget.java", "PreviewPatternMemory.java", "ChamberPreviewOverlay.java")),
                    "Local preview helpers provide effect anchors, lazy lifecycle and requested chamber positions");
        }
        var local = JsonParser.parseString(Files.readString(RESOURCES.resolve("gtl_enhancedcore.mixins.json"))).getAsJsonObject();
        var registered = entries(local);
        check(registered.size() == local.getAsJsonArray("mixins").size() + local.getAsJsonArray("client").size(),
                "Local Mixin registration contains no duplicates");
        check(registered.contains("ldlib.client.PreviewEffectAnchorsMixin"), "The custom effect anchor hooks remain registered");
        check(Files.isRegularFile(MAIN.resolve("mixin/ldlib/client/PreviewEffectAnchorsMixin.java")),
                "The retained effect anchor registration has a source implementation");
        check(Files.readString(MAIN.resolve("client/renderer/machine/NeutronStarInfinityRenderer.java"))
                        .contains("PreviewEffectAnchors.positions(world)"),
                "The custom machine renderer retains its preview effect anchor integration");
        for (String removed : List.of(
                "ae2.client.PreviewMaterialHighlightMixin", "gtlcore.client.PreviewMaterialSlotMixin",
                "jei.client.PreviewHeaderBoundsMixin", "gtceu.client.PreviewShapesMixin",
                "gtceu.client.WorldPreviewAccessor", "gtceu.client.WorldPreviewPerformanceMixin",
                "gtlcore.client.PatternPreviewPerformanceMixin", "gtlcore.client.PatternPreviewControlsMixin",
                "gtlcore.client.PreviewPatternHeightMixin", "gtlcore.client.PreviewWidgetCacheAccessor",
                "ldlib.client.PreviewSceneRendererMixin", "ldlib.client.PreviewSceneWidgetMixin",
                "ldlib.client.PreviewPickingMixin", "ldlib.client.PreviewWorldStorageMixin",
                "mc.client.PreviewReloadMixin", "ldlib.BlockInfoAccessor", "ldlib.MachineBlockInfoMixin",
                "gtceu.StableBlockCandidatesMixin", "gtceu.TerminalMultiblockStateAccessor",
                "gtlcore.TerminalPatternAccessor", "gtlcore.TerminalPatternPerformanceMixin")) {
            check(!registered.contains(removed), "Upstream-owned Mixin is not registered locally: " + removed);
            check(!Files.exists(MAIN.resolve("mixin/" + removed.replace('.', '/') + ".java")),
                    "Upstream-owned Mixin implementation is removed locally: " + removed);
        }
        for (String helper : List.of("LatestTask", "WeightedCache")) {
            check(!Files.exists(MAIN.resolve("common/util/" + helper + ".java")),
                    "Unused local preview helper is removed: " + helper);
        }
        check(!Files.exists(MAIN.resolve("integration/terminal/StableBlockCandidates.java")),
                "The duplicated local terminal candidate registry is removed");
        for (String source : List.of("integration/terminal/LampPlacement.java",
                "common/structure/IntegratedFactoryStructure.java", "common/structure/GtlMegastructurePatterns.java")) {
            String text = Files.readString(MAIN.resolve(source));
            check(text.contains("org.gtlcore.gtlcore.integration.terminal.StableBlockCandidates")
                            && !text.contains("com.gtl.enhancedcore.integration.terminal.StableBlockCandidates"),
                    "Custom lamp candidates use the upstream fixed-supplier registry: " + source);
        }
        String entry = Files.readString(MAIN.resolve("GTLEnhancedcore.java"));
        check(!entry.contains("PreviewSettings") && !entry.contains("GTL-Enhancedcore/client.toml"),
                "The retired local preview config is no longer registered");
        for (String language : List.of("zh_cn", "en_us")) {
            var translations = JsonParser.parseString(Files.readString(
                    RESOURCES.resolve("assets/gtl_enhancedcore/lang/" + language + ".json"))).getAsJsonObject();
            check(translations.keySet().stream().noneMatch(key -> key.startsWith("gui.gtl_enhancedcore.preview.")),
                    "No retired local preview translation remains: " + language);
        }
        for (String icon : List.of("expand", "restore")) {
            check(!Files.exists(RESOURCES.resolve("assets/gtl_enhancedcore/textures/gui/preview/" + icon + ".png")),
                    "No duplicate local fullscreen icon remains: " + icon);
        }
    }

    private static void upstreamPackaging() throws Exception {
        var dependencies = JsonParser.parseString(Files.readString(Path.of("gradle/local-dependencies.json"))).getAsJsonObject();
        var cores = new java.util.ArrayList<String>();
        for (var entry : dependencies.getAsJsonArray("implementation")) {
            String name = entry.getAsString();
            if (name.startsWith("gtlcore-")) cores.add(name);
        }
        check(cores.size() == 1, "Exactly one GTLCore implementation dependency owns the shared preview");
        try (var jar = new ZipFile(Path.of("libs", cores.getFirst()).toFile())) {
            var config = JsonParser.parseString(new String(jar.getInputStream(
                    jar.getEntry("gtlcore.mixin.json")).readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            var registered = entries(config);
            String mixinPackage = config.get("package").getAsString().replace('.', '/');
            for (String mixin : List.of("ldlib.MachineBlockInfoMixin", "ldlib.BlockInfoAccessor",
                    "ae2.integration.EncodePatternErrorRendererMixin", "gtm.client.PreviewShapesMixin",
                    "gtm.client.WorldPreviewPerformanceMixin", "ldlib.client.PreviewSceneAccessor",
                    "ldlib.client.PreviewSceneWidgetMixin", "ldlib.client.PreviewPickingMixin",
                    "ldlib.client.PreviewWorldStorageMixin", "mc.client.PreviewReloadMixin",
                    "gtm.StableBlockCandidatesMixin", "gtm.api.machine.IMultiblockStateInvoker",
                    "gtmt.TerminalPatternAccessor", "gtmt.TerminalPatternPerformanceMixin")) {
                check(registered.contains(mixin), "Upstream preview integration is registered: " + mixin);
                check(jar.getEntry(mixinPackage + "/" + mixin.replace('.', '/') + ".class") != null,
                        "Upstream preview integration is packaged: " + mixin);
            }
            for (String type : List.of("FullscreenPreviewScreen", "PreviewControls", "PreviewPan",
                    "PreviewMesh", "PreviewScenes", "PreviewLifecycle", "PreviewMaterialHighlights", "WorldPreview")) {
                check(jar.getEntry("org/gtlcore/gtlcore/client/preview/" + type + ".class") != null,
                        "Upstream owns the required preview component: " + type);
            }
            checkGtlCoreFactoryStub(jar);
            checkReference(jar, "api/gui/PatternPreviewWidget", "client/preview/PreviewControls");
            checkReference(jar, "api/gui/PatternPreviewWidget", "client/preview/PreviewScenes");
            checkReference(jar, "client/preview/FullscreenPreviewScreen", "client/preview/PreviewPan");
            checkReference(jar, "integration/jei/SlotRecipeWidget", "client/preview/PreviewMaterialHighlights");
            checkReference(jar, "api/pattern/AdvancedBlockPattern", "integration/terminal/StableBlockCandidates");
            checkReference(jar, "mixin/gtmt/TerminalPatternPerformanceMixin", "integration/terminal/StableBlockCandidates");
            for (String icon : List.of("expand", "restore")) {
                check(jar.getEntry("assets/gtlcore/textures/gui/preview/" + icon + ".png") != null,
                        "Upstream packages the fullscreen icon: " + icon);
            }
        }
    }

    /** GTCEu's wrapper constructor still invokes its old factory before GTLCore's
     * ModifyArg hook. The fix4 mixin deliberately turns that call into null, so
     * this contract prevents a dependency upgrade from reintroducing eager work. */
    private static void checkGtlCoreFactoryStub(ZipFile jar) throws Exception {
        var entry = jar.getEntry("org/gtlcore/gtlcore/mixin/gtm/gui/PatternPreviewWidgetMixin.class");
        check(entry != null, "GTLCore packages the GTCEu preview factory guard");
        var node = new ClassNode();
        new ClassReader(jar.getInputStream(entry).readAllBytes()).accept(node,
                ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        var guards = node.methods.stream().filter(method -> "getPatternWidget".equals(method.name)
                && method.desc.equals("(Lcom/gregtechceu/gtceu/api/machine/MultiblockMachineDefinition;)"
                        + "Lcom/gregtechceu/gtceu/api/gui/widget/PatternPreviewWidget;")).toList();
        check(guards.size() == 1, "GTLCore declares exactly one GTCEu preview factory guard");
        var instructions = new java.util.ArrayList<Integer>();
        for (var instruction : guards.getFirst().instructions)
            if (instruction.getOpcode() >= 0) instructions.add(instruction.getOpcode());
        check(instructions.equals(List.of(Opcodes.ACONST_NULL, Opcodes.ARETURN)),
                "GTLCore fix4 preview factory guard returns null without constructing GT previews");
    }

    private static void lazyPostApply() {
        String marker = MultiblockPreviewLaziness.MARKER_MIXIN;
        String wrapper = "com.gregtechceu.gtceu.integration.jei.multipage.MultiblockInfoWrapper";
        String definition = "Lcom/gregtechceu/gtceu/api/machine/MultiblockMachineDefinition;";
        var node = new ClassNode();
        var method = mergedMethod("handler$gtlcore$MultiblockInfoWrapper", "(Lcom/lowdragmc/lowdraglib/gui/widget/Widget;"
                + definition + ")Lcom/lowdragmc/lowdraglib/gui/widget/Widget;",
                "org.gtlcore.gtlcore.mixin.gtm.MultiblockInfoWrapperMixin");
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        var call = new MethodInsnNode(Opcodes.INVOKESTATIC, "org/gtlcore/gtlcore/api/gui/PatternPreviewWidget",
                "getPatternWidget", "(" + definition + ")Lorg/gtlcore/gtlcore/api/gui/PatternPreviewWidget;", false);
        method.instructions.add(call);
        method.instructions.add(new InsnNode(Opcodes.ARETURN));
        node.methods.add(method);
        MultiblockPreviewLaziness.postApply(wrapper, node, "some.other.Mixin");
        check(call.owner.startsWith("org/gtlcore/"), "Only the dedicated marker can change preview factories");
        MultiblockPreviewLaziness.postApply(wrapper, node, marker);
        check(call.owner.equals("com/gtl/enhancedcore/client/preview/LazyMultiblockPreviewWidget")
                        && call.name.equals("create") && call.desc.endsWith("Lcom/lowdragmc/lowdraglib/gui/widget/Widget;"),
                "GTLCore's merged wrapper factory is precisely changed to the lightweight widget");
        var missing = new ClassNode();
        expectRejected(() -> MultiblockPreviewLaziness.postApply(wrapper, missing, marker),
                "A missing GTLCore merged factory fails instead of silently loading eager previews");
        var wrongOwner = new ClassNode();
        wrongOwner.methods.add(mergedMethod("handler$gtlcore$MultiblockInfoWrapper", method.desc, "some.other.Mixin"));
        expectRejected(() -> MultiblockPreviewLaziness.postApply(wrapper, wrongOwner, marker),
                "A method name without GTLCore provenance cannot be changed");

        var category = new ClassNode();
        String categoryDescriptor = "(Lmezz/jei/api/gui/builder/IRecipeLayoutBuilder;"
                + "Lcom/gregtechceu/gtceu/integration/jei/multipage/MultiblockInfoWrapper;Lmezz/jei/api/recipe/IFocusGroup;)V";
        var setRecipe = mergedMethod("setRecipe", categoryDescriptor,
                "org.gtlcore.gtlcore.mixin.gtm.MultiblockInfoCategoryMixin");
        setRecipe.instructions.add(new InsnNode(Opcodes.RETURN));
        category.methods.add(setRecipe);
        var bridge = mergedMethod("setRecipe", "(Lmezz/jei/api/gui/builder/IRecipeLayoutBuilder;Ljava/lang/Object;"
                + "Lmezz/jei/api/recipe/IFocusGroup;)V", "org.gtlcore.gtlcore.mixin.gtm.MultiblockInfoCategoryMixin");
        bridge.instructions.add(new InsnNode(Opcodes.RETURN));
        category.methods.add(bridge);
        MultiblockPreviewLaziness.postApply("com.gregtechceu.gtceu.integration.jei.multipage.MultiblockInfoCategory",
                category, marker);
        check(setRecipe.instructions.size() == 7 && bridge.instructions.size() == 1,
                "Only the exact typed category method receives preparation and indexing early return");
        check(setRecipe.instructions.get(2) instanceof MethodInsnNode prepare
                        && prepare.name.equals("prepare") && prepare.desc.equals("(Ljava/lang/Object;Ljava/lang/Object;)Z"),
                "Preparation gets the actual builder and wrapper without creating client layouts");
    }

    private static MethodNode mergedMethod(String name, String descriptor, String owner) {
        var method = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, name, descriptor, null, null);
        var annotation = new AnnotationNode("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;");
        annotation.values = new java.util.ArrayList<>(List.of("mixin", owner));
        method.visibleAnnotations = new java.util.ArrayList<>(List.of(annotation));
        return method;
    }

    private static void expectRejected(Runnable task, String message) {
        try { task.run(); }
        catch (IllegalStateException expected) { check(true, message); return; }
        check(false, message);
    }

    private static void checkReference(ZipFile jar, String owner, String dependency) throws Exception {
        String prefix = "org/gtlcore/gtlcore/";
        String constants = new String(jar.getInputStream(jar.getEntry(prefix + owner + ".class")).readAllBytes(),
                StandardCharsets.ISO_8859_1);
        check(constants.contains(prefix + dependency), "Upstream component is connected: " + owner + " -> " + dependency);
    }

    private static Set<String> entries(JsonObject config) {
        var result = new HashSet<String>();
        for (String section : List.of("mixins", "client")) {
            JsonArray values = config.getAsJsonArray(section);
            for (var value : values) result.add(value.getAsString());
        }
        return result;
    }

    private static void check(boolean valid, String message) {
        if (!valid) throw new AssertionError(message);
        assertions++;
    }
}
