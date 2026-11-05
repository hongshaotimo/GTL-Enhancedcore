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
