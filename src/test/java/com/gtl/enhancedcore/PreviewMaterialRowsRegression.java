package com.gtl.enhancedcore;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

public final class PreviewMaterialRowsRegression {
    private static int assertions;

    public static void main(String[] args) throws Exception {
        System.out.println("Preview material contracts passed: " + run() + " assertions");
        UpstreamPreviewContractRegression.main(args);
        StagedPreviewContractRegression.main(args);
    }

    static int run() throws Exception {
        assertions = 0;
        try (var jar = new ZipFile("libs/gtlcore-1.2.3.2-fix4.jar")) {
            var pattern = read(jar, "org/gtlcore/gtlcore/api/gui/PatternPreviewWidget$MBPattern");
            for (var field : new String[]{"blockMap", "predicateMap", "controllerBase", "moduleOnlyBlocks"}) {
                check(pattern.fields.stream().anyMatch(candidate -> candidate.name.equals(field)),
                        "Installed pattern exposes " + field);
            }
            var widget = read(jar, "org/gtlcore/gtlcore/api/gui/PatternPreviewWidget");
            check(widget.methods.stream().anyMatch(method -> method.name.equals("setPage") && method.desc.equals(
                    "(ILcom/lowdragmc/lowdraglib/gui/util/ClickData;)V")), "Installed page hook exists");
            check(widget.methods.stream().anyMatch(method -> method.name.equals("setupScene") && method.desc.equals(
                    "(Lorg/gtlcore/gtlcore/api/gui/PatternPreviewWidget$MBPattern;)V")), "Installed scene hook exists");
        }
        var resources = Path.of("src/main/resources");
        var config = JsonParser.parseString(Files.readString(resources.resolve("gtl_enhancedcore.mixins.json"))).getAsJsonObject();
        check(config.getAsJsonArray("client").asList().stream().anyMatch(entry -> entry.getAsString().equals(
                "gtlcore.client.PreviewMaterialRowsMixin")), "Material UI Mixin is client-only");
        for (var locale : new String[]{"zh_cn", "en_us"}) {
            var lang = JsonParser.parseString(Files.readString(resources.resolve(
                    "assets/gtl_enhancedcore/lang/" + locale + ".json"))).getAsJsonObject();
            check(lang.has("gtl_enhancedcore.preview.expand_material"), "Expand hint translated for " + locale);
        }
        var source = Files.readString(Path.of("src/main/java/com/gtl/enhancedcore/client/preview/PreviewMaterialRows.java"));
        check(source.contains("predicates.addAll(predicate.limited)") && source.contains("predicate.common"),
                "Both limited and common candidates are considered");
        check(source.contains("options.contains(item)"), "Alternatives stay within the selected predicate group");
        check(source.contains("record Material(AEItemKey item, List<AEItemKey> alternatives, int section)"),
                "Equal items with different alternatives are not merged");
        check(source.contains("material.section() == 1 ? List.of() : super.getXEIIngredients()"),
                "JEI native recipe lookup cannot steal the expand click");
        check(source.contains("return super.getXEIIngredientOverMouse(mouseX, mouseY);")
                        && !source.contains("return material.section() == 1 ? null")
                        && !source.contains("getXEIIngredientOverMouse(double mouseX, double mouseY) { return null; }"),
                "Material and candidate hover ingredients remain available to JEI bookmark keys");
        check(!source.contains("replacePatternMaterial") && !source.contains("materialReplacements"),
                "Candidate expansion does not replace the preview");
        check(config.getAsJsonArray("client").asList().stream().anyMatch(entry -> entry.getAsString().equals(
                "gtlcore.client.PreviewVisibilityMixin")), "Visibility Mixin is client-only");
        var visibility = Files.readString(Path.of(
                "src/main/java/com/gtl/enhancedcore/mixin/gtlcore/client/PreviewVisibilityMixin.java"));
        check(visibility.contains("(enhanced$visibilityMode + 1) % 3"), "Visibility cycles through three modes");
        check(visibility.contains("new ButtonWidget(138, 70, 18, 18"), "Visibility button is below the layer button");
        check(visibility.contains("List.copyOf(sceneWidget.getCore())"), "Visibility preserves native layer and render-mask filtering");
        check(visibility.contains("sceneWidget.setRenderedCore(visible, null)"), "Rendering and picking share the filtered core");
        check(visibility.contains("sceneWidget.needCompileCache()"), "Visibility changes invalidate cached scene geometry");
        check(!visibility.contains("setPage(") && !visibility.contains("setCameraYawAndPitch"),
                "Visibility changes do not reset the page, layer or camera");
        check(visibility.contains("method = \"onPosSelected\"") && visibility.contains(".position(enhanced$pattern.enhanced$predicates().get(pos))"),
                "Scene selection supplies the exact position predicate to material highlighting");
        check(source.contains("borderColor(material.item())") && source.contains("borderColor(item)"),
                "Both material rows share the highlight color policy");
        check(source.contains("if (allowed.contains(item)) return 0xffff4040;")
                        && source.contains("return item.equals(selected) ? 0xff80ff80 : 0;"),
                "Position candidates are red before falling back to green material selection");
        check(visibility.contains("enhanced$selectedPositions.contains(pos)"), "Selected material filters placeable positions");
        check(visibility.contains("if (enhanced$pattern != access) enhanced$materialSelection().clear()"),
                "Page changes reset stale selections");
        check(source.contains("item.equals(selected) ? null : item"), "Clicking the same material clears selection");
        check(source.contains("countLabel(simple.minCount, simple.maxCount)"), "Badges use GTL global count limits");
        check(source.contains("lower == maximum ? String.valueOf(lower)"), "Exact counts use a single number");
        check(source.contains("simple.minLayerCount") && source.contains("simple.maxLayerCount"), "Layer limits remain in tooltips");
        var scene = Files.readString(Path.of("src/main/java/com/gtl/enhancedcore/mixin/ldlib/client/SceneWidgetRenderFilterMixin.java"));
        check(scene.contains("button != 1") && scene.contains(".position(null)") && scene.contains("selectedPosFace = null"),
                "Right-click clears scene selection without toggling the material selection");
        var layout = Files.readString(Path.of("src/main/java/com/gtl/enhancedcore/mixin/gtlcore/client/PreviewMaterialLayoutMixin.java"));
        var rows = Files.readString(Path.of("src/main/java/com/gtl/enhancedcore/mixin/gtlcore/client/PreviewMaterialRowsMixin.java"));
        check(rows.contains("setSelfPosition(4, height - 50)") && rows.contains("setSelfPosition(4, height - 26)"),
                "Fullscreen material and candidate rows occupy separate tracks");
        check(layout.contains("screenHeight - scene.getSelfPosition().y - 54"),
                "Fullscreen scene reserves space for both material rows");
        check(layout.contains("if (!fullscreen") && !layout.contains("setVisible"),
                "Fullscreen layout preserves candidate expansion state");
        check(config.getAsJsonArray("client").asList().stream().anyMatch(entry -> entry.getAsString().equals(
                "gtlcore.client.PreviewMaterialLayoutMixin")), "Fullscreen layout Mixin is client-only");
        try (var jar = new ZipFile("libs/gtlcore-1.2.3.2-fix4.jar")) {
            var controls = read(jar, "org/gtlcore/gtlcore/client/preview/PreviewControls");
            check(controls.methods.stream().anyMatch(method -> method.name.equals("layout") && method.desc.equals("()V")),
                    "Installed fullscreen layout hook exists");
            for (var field : new String[]{"widget", "scene", "fullscreen", "width", "screenHeight"}) {
                check(controls.fields.stream().anyMatch(candidate -> candidate.name.equals(field)),
                        "Installed controls expose " + field);
            }
        }
        return assertions;
    }

    private static ClassNode read(ZipFile jar, String name) throws Exception {
        var node = new ClassNode();
        try (var stream = jar.getInputStream(jar.getEntry(name + ".class"))) {
            new ClassReader(stream).accept(node, ClassReader.SKIP_CODE);
        }
        return node;
    }

    private static void check(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }
}
