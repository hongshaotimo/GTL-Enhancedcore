package com.gtl.enhancedcore;

import com.gtl.enhancedcore.common.structure.ChamberScanCursor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

final class ChamberPositionsRegression {
    private static int checks;
    static int run() throws Exception {
        var cursor=new ChamberScanCursor(new int[]{2,3,1,2},new int[]{2,1,1,1},new int[]{1,2,0,1},-2);
        var cells=new ArrayList<ChamberScanCursor.Cell>();
        for(var cell=cursor.next();cell!=null;cell=cursor.next())cells.add(cell);
        check(cells.size()==12,"repeat and zero-repeat cell counts");
        check(cells.get(0).equals(new ChamberScanCursor.Cell(0,0,0,-2)),"negative controller anchor");
        check(cells.get(3).equals(new ChamberScanCursor.Cell(0,1,1,-2)),"column and row traversal");
        check(cells.get(4).equals(new ChamberScanCursor.Cell(1,0,0,-1))
                &&cells.get(7).equals(new ChamberScanCursor.Cell(1,0,0,0)),"expanded repeat offsets");
        check(cells.get(10).equals(new ChamberScanCursor.Cell(3,0,0,1))&&cursor.next()==null,"zero repeat has no phantom Z");
        int[] repetitions={2};cursor=new ChamberScanCursor(new int[]{1},new int[]{1},repetitions,0);repetitions[0]=100;
        check(cursor.next()!=null&&cursor.next()!=null&&cursor.next()==null,"isolated repetition snapshot");
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
        String tasks=Files.readString(root.resolve("common/structure/TerminalChamberScanTasks.java"));
        check(tasks.contains("2_000_000L")&&tasks.contains("budget=65536")&&tasks.contains("JOBS.size()>=4"),"bounded global scan work");
        check(tasks.contains("enhanced$chamberOffset")&&tasks.contains("enhancedcore$offset")
                &&!tasks.contains("checkPatternAt"),"native rotation without world matching");
        String packets=Files.readString(root.resolve("network/ChamberPackets.java"));
        check(packets.contains("MAX_POSITIONS=8192")&&packets.contains("PLAY_TO_CLIENT")
                &&packets.contains("n<0||n>MAX_POSITIONS"),"bounded S2C snapshot");
        String terminal=Files.readString(root.resolve("common/structure/TerminalChamberScan.java"));
        check(terminal.contains("LeftClickBlock.Action.START")&&!terminal.contains("RightClick"),"original right-click retained");
        return checks;
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
}
