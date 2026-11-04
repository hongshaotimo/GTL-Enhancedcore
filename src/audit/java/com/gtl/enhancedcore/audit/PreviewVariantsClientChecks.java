package com.gtl.enhancedcore.audit;

import com.google.gson.GsonBuilder;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gtl.enhancedcore.client.preview.ChamberPreviewOverlay;
import com.lowdragmc.lowdraglib.gui.util.ClickData;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.SceneWidget;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import org.gtlcore.gtlcore.api.gui.PatternPreviewWidget;

/** Registered native shape suppliers and real preview button callbacks; no player world. */
@Mod("enhancedcore_audit")
public final class PreviewVariantsClientChecks {
    private boolean done;
    private final List<String> checks = new ArrayList<>();
    public PreviewVariantsClientChecks() { MinecraftForge.EVENT_BUS.addListener(this::tick); }
    private void check(boolean valid, String label) {
        if (!valid) throw new AssertionError(label);
        checks.add(label);
    }
    private void tick(TickEvent.ClientTickEvent event) {
        var mc = Minecraft.getInstance();
        if (done || event.phase != TickEvent.Phase.END || !Boolean.getBoolean("gtl.enhancedcore.previewVariantsAudit")
                || mc.getOverlay() != null || mc.screen == null) return;
        done = true;
        Path directory = mc.gameDirectory.toPath().toAbsolutePath().normalize();
        var result = new LinkedHashMap<String,Object>();
        try {
            check(directory.startsWith(Path.of("C:/IDEA/GTL-Enhancedcore/_audit/preview-variants-fix-20261029"))
                    && Files.isRegularFile(directory.resolve("preview-variants.marker"))
                    && mc.level == null && mc.getSingleplayerServer() == null, "Marked title fixture; no player world");
            ChamberPreviewUiChecks.run(this::check);
            variants(directory);
            result.put("status", "PASS");
        } catch (Throwable failure) {
            result.put("status", "FAIL"); result.put("error", failure.toString()); failure.printStackTrace();
        }
        result.put("checks", checks);
        try { Files.writeString(directory.resolve("preview-variants.json"), new GsonBuilder().setPrettyPrinting().create().toJson(result)); }
        catch (Exception failure) { failure.printStackTrace(); }
        mc.stop();
    }
    private void variants(Path directory) throws Exception {
        var fixture = StellarIvGuiChecks.fixture("gtceu:suprachronal_assembly_line", true, false, false);
        var level = PatternPreviewWidget.class.getDeclaredField("LEVEL"); level.setAccessible(true);
        Object previous = level.get(null);
        try {
            var world = (com.lowdragmc.lowdraglib.utils.TrackedDummyWorld)fixture.player().level();
            ChamberPreviewUiChecks.seedWorld(world); level.set(null, world);
            for (String id : List.of("gtceu:advanced_assembly_line", "gtceu:electric_blast_furnace")) {
                var definition = (MultiblockMachineDefinition)GTRegistries.MACHINES.get(new ResourceLocation(id));
                var shapes = definition.getMatchingShapes();
                checks.add(id + " native shapes=" + shapes.size());
                check(shapes.size() > 1, id + " retains multiple native length/tier shapes");
                var widget = PatternPreviewWidget.getPatternWidget(definition);
                var scene = (SceneWidget)field(widget, "sceneWidget");
                var page = button(widget, 30);
                var toggle = button(widget, 70);
                press(toggle);
                check(ChamberPreviewOverlay.enabled(scene), id + " chamber overlay enables");
                press(page);
                check((Integer)field(widget, "index") == 1, id + " original page button advances to variant 1");
                check(ChamberPreviewOverlay.enabled(scene), id + " chamber overlay survives variant switch");
                var controls = widget.getPreviewControls();
                for (int[] size : new int[][]{{320,180},{960,540}}) {
                    controls.enter(size[0], size[1]);
                    check(page.getSelfPositionY() + page.getSizeHeight() <= toggle.getSelfPositionY(),
                            id + " native page and chamber controls do not overlap " + size[0]);
                    press(page);
                    check((Integer)field(widget, "index") == (size[0] == 320 ? 2 : 3) % shapes.size(),
                            id + " native page click remains functional in fullscreen " + size[0]);
                    controls.exit();
                }
                widget.setPage(shapes.size() - 1, null); press(page);
                check((Integer)field(widget, "index") == 0, id + " page button wraps to first native variant");
                scene.releaseCacheBuffer(); PatternPreviewWidget.clearSharedPreviewState(); level.set(null,world);
            }
            var custom = (MultiblockMachineDefinition)GTRegistries.MACHINES.get(new ResourceLocation("gtceu:component_assembly_line"));
            check(custom.getMatchingShapes().size() == 1, "Replaced component assembly blueprint retains its matching single shape");
        } finally {
            PatternPreviewWidget.clearSharedPreviewState(); level.set(null,previous);
            fixture.holders().forEach(holder -> holder.setLevel(null));
        }
    }
    private static ButtonWidget button(PatternPreviewWidget widget, int y) {
        return widget.widgets.stream().filter(ButtonWidget.class::isInstance).map(ButtonWidget.class::cast)
                .filter(b -> b.getSelfPositionY() == y).findFirst().orElseThrow();
    }
    @SuppressWarnings("unchecked")
    private static void press(ButtonWidget button) throws Exception {
        ((java.util.function.Consumer<ClickData>)field(button, "onPressCallback")).accept(new ClickData());
    }
    private static Object field(Object object, String name) throws Exception {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try { var field = type.getDeclaredField(name); field.setAccessible(true); return field.get(object); }
            catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(name);
    }
}
