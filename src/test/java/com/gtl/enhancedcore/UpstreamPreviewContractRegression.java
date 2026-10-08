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

/** Preview/terminal packaging and ownership contract; runtime behavior is checked by the audit. */
public final class UpstreamPreviewContractRegression {
    private static final Path MAIN = Path.of("src/main/java/com/gtl/enhancedcore");
    private static final Path RESOURCES = Path.of("src/main/resources");
    private static int assertions;

    private UpstreamPreviewContractRegression() {}

    public static void main(String[] args) throws Exception {
        System.out.println("Upstream preview contract passed: " + run() + " assertions (packaging and ownership only)");
    }

    static int run() throws Exception {
        assertions = 0;
        localOwnership();
        upstreamPackaging();
        return assertions;
    }

    private static void localOwnership() throws Exception {
        try (var files = Files.list(MAIN.resolve("client/preview"))) {
            var names = files.filter(Files::isRegularFile).map(path -> path.getFileName().toString()).toList();
            check(names.equals(List.of("PreviewEffectAnchors.java")),
                    "Only the custom neutron-star effect anchor index remains in the local preview package");
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
