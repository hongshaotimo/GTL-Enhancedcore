package com.gtl.enhancedcore;

import java.nio.file.Files;
import java.nio.file.Path;

final class ChamberPositionsRegression {
    private static int checks;
    static int run() throws Exception {
        Path root=Path.of("src/main/java/com/gtl/enhancedcore");
        String classifier=Files.readString(root.resolve("common/structure/ChamberPositions.java"));
        check(classifier.contains("p.common.stream()")&&classifier.contains("p.limited.stream()")
                &&classifier.contains("instanceof MultiblockPartMachine"),"actual part type from all alternatives");
        String memory=Files.readString(root.resolve("mixin/gtlcore/client/PreviewPatternMemoryMixin.java"));
        check(memory.contains("predicateMap.entrySet()")&&memory.contains("enhanced$chambers = null"),"cached real preview coordinates and release");
        String scene=Files.readString(root.resolve("client/preview/ChamberPreviewOverlay.java"));
        check(scene.contains("filter(scene.getCore()::contains)"),"current layer/module filter");
        String preview=Files.readString(root.resolve("mixin/gtlcore/client/ChamberPreviewWidgetMixin.java"));
        check(preview.contains("private boolean enhanced$showChambers;")
                && preview.contains("last.getSelfPositionX()")
                && preview.contains("last.getSizeWidth(), last.getSizeHeight()")
                && preview.contains("last.getSelfPositionY() + last.getSizeHeight() + gap"),
                "Preview chamber toggle defaults off and follows native column geometry and optional module control");
        check(preview.contains("enhanced$showChambers = !enhanced$showChambers")
                && preview.contains("ChamberPreviewOverlay.setEnabled(sceneWidget, enhanced$showChambers)"),
                "Preview switch updates the scene on click");
        check(preview.contains("enhanced$showChambers);"), "Page/layer scene rebuilds retain the preview-local toggle state");
        check(preview.contains("gtl_enhancedcore.chambers.preview_on") && preview.contains("gtl_enhancedcore.chambers.preview_off")
                && preview.contains("gtl_enhancedcore.chambers.preview_toggle"),
                "Button label reflects the current opt-in state");
        check(scene.contains("boolean enabled;") && scene.contains("state.enabled = enabled")
                && scene.contains("if (state != null && state.enabled)")
                && scene.contains("SCENES.remove(scene)"), "Overlay is opt-in and scene release drops its state");
        String hook=Files.readString(root.resolve("mixin/ldlib/client/ChamberSceneOverlayMixin.java"));
        check(hook.contains("renderBeforeBatchEnd")&&hook.contains("At(\"TAIL\")")&&!hook.contains("setBefore"),"append without callback replacement");
        return checks;
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
}
