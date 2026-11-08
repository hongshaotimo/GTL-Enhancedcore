package com.gtl.enhancedcore.audit;

import appeng.api.stacks.AEItemKey;
import com.gtl.enhancedcore.client.preview.PreviewMaterialAccess;
import com.gtl.enhancedcore.client.preview.PreviewMaterialRows;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.DraggableScrollableWidgetGroup;
import com.lowdragmc.lowdraglib.gui.widget.SceneWidget;
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import org.gtlcore.gtlcore.api.gui.PatternPreviewWidget;
import org.joml.Vector3f;

/** Exercise the merged PR through the real screen and rendered native preview. */
public final class PreviewMaterialRuntimeChecks {
    private final List<String> checks;
    private final int size, offsetX, offsetY;
    private int step, frames, pick;
    private ButtonWidget visibility;
    private Set<BlockPos> full;
    private Vector3f center;
    private float zoom;
    private boolean done;

    PreviewMaterialRuntimeChecks(List<String> checks, int size, IRecipeLayoutDrawable<?> layout) {
        this.checks = checks;
        this.size = size;
        offsetX = layout.getRect().getX();
        offsetY = layout.getRect().getY();
    }

    boolean done() { return done; }

    void tick(PatternPreviewWidget preview) throws Exception {
        if (++frames < 8) return;
        var controls = preview.getPreviewControls();
        var scene = controls.scene();
        var pattern = pattern(preview);
        var interaction = (PreviewMaterialAccess.Interaction) preview;
        var rows = preview.widgets.stream().filter(DraggableScrollableWidgetGroup.class::isInstance)
                .map(DraggableScrollableWidgetGroup.class::cast).sorted((a, b) ->
                        Integer.compare(a.getSelfPositionY(), b.getSelfPositionY())).toList();
        require(rows.size() == 2, "Exactly two independent material rows");
        var primary = rows.get(0);
        var candidates = rows.get(1);
        switch (step) {
            case 0 -> {
                require(pattern.enhanced$materials() == pattern.enhanced$materials()
                                && !pattern.enhanced$materials().isEmpty(), "Prepared material index is reused");
                coilCounts(pattern);
                compact(scene, primary, candidates);
                full = new HashSet<>(scene.getCore());
                require(full.size() > 1, "Default mode renders the complete preview");
                visibility = preview.widgets.stream().filter(ButtonWidget.class::isInstance)
                        .map(ButtonWidget.class::cast).filter(button -> button.getSelfPositionY() == 70)
                        .findFirst().orElseThrow();
                scene.setCenter(new Vector3f(scene.getCenter()).add(0.25f, 0.5f, 0));
                scene.setZoom(scene.getZoom() * 1.15f);
                center = new Vector3f(scene.getCenter());
                zoom = scene.getZoom();
                click(preview, visibility);
            }
            case 1 -> {
                require(scene.getCore().equals(Set.of(pattern.enhanced$controllerPosition())),
                        "Root-screen display button selects controller-only mode");
                camera(scene);
                capture("controller");
                click(preview, visibility);
            }
            case 2 -> {
                var expected = new HashSet<>(full);
                expected.retainAll(pattern.enhanced$replaceablePositions());
                expected.add(pattern.enhanced$controllerPosition());
                require(scene.getCore().equals(expected), "Third display mode retains controller and replaceable positions");
                camera(scene);
                click(preview, expandable(primary));
            }
            case 3 -> {
                require(candidates.isVisible() && !candidates.widgets.isEmpty(),
                        "Root-screen material click expands allowed candidate blocks");
                require(interaction.enhanced$materialSelection().selected() != null,
                        "Material click records its selected ingredient");
                camera(scene);
                capture("compact-expanded");
                click(preview, controls.expandButton());
                require(controls.fullscreen(), "Native fullscreen button opens the actual fullscreen screen");
            }
            case 4 -> {
                fullscreen(scene, primary, candidates);
                duplicateHighlights(preview);
                capture("fullscreen-expanded");
                var patterns = (Object[]) field(PatternPreviewWidget.class, "patterns").get(preview);
                require(patterns.length > 1, "Fullscreen fixture includes multiple native structure variants");
                preview.setPage(patterns.length - 1, null);
            }
            case 5 -> {
                fullscreen(scene, primary, candidates);
                click(preview, expandable(primary));
                require(candidates.isVisible(), "New fullscreen variant can expand its candidates");
                capture("fullscreen-last-variant");
                Minecraft.getInstance().screen.onClose();
                require(!controls.fullscreen(), "Native screen close exits fullscreen");
            }
            case 6 -> {
                compact(scene, primary, candidates);
                require(candidates.isVisible() && !candidates.widgets.isEmpty(),
                        "Expanded row remains usable after fullscreen page change and exit");
                capture("restored-expanded");
                click(preview, candidates.widgets.stream().filter(SlotWidget.class::isInstance).findFirst().orElseThrow());
            }
            case 7 -> {
                AEItemKey selected = interaction.enhanced$materialSelection().selected();
                require(selected != null, "Root-screen candidate click selects a material");
                require(scene.getCore().contains(pattern.enhanced$controllerPosition()),
                        "Filtered candidate keeps its actual controller anchor");
                require(scene.getCore().stream().allMatch(pos -> pos.equals(pattern.enhanced$controllerPosition())
                                || pattern.enhanced$candidates(pattern.enhanced$predicates().get(pos)).contains(selected)),
                        "Every displayed candidate position allows the selected ingredient");
                capture("candidate-filter");
            }
            case 8 -> {
                // Rendering the real screen supplies its native ray picker with deterministic mouse coordinates.
                int localX = scene.getPositionX() + 4 + (pick % 12) * Math.max(1, (scene.getSizeWidth() - 8) / 12);
                int localY = scene.getPositionY() + 4 + (pick / 12) * Math.max(1, (scene.getSizeHeight() - 8) / 12);
                int x = localX + offsetX, y = localY + offsetY;
                var mc = Minecraft.getInstance();
                var graphics = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
                mc.screen.render(graphics, x, y, 0);
                graphics.flush();
                if (scene.getHoverPosFace() == null) {
                    require(++pick < 144, "Native ray picker finds a rendered preview block");
                    frames = 7;
                    return;
                }
                require(mc.screen.mouseClicked(x, y, 0), "Root-screen left click selects a rendered block");
                mc.screen.mouseReleased(x, y, 0);
                require(scene.getSelectedPosFace() != null, "Native 3D scene records the selected block face");
                var allowed = pattern.enhanced$candidates(pattern.enhanced$predicates().get(scene.getSelectedPosFace().pos));
                require(!allowed.isEmpty() && allowed.stream().allMatch(item ->
                                interaction.enhanced$materialSelection().borderColor(item) == 0xffff4040),
                        "3D selection marks every allowed material red with priority over green");
                require(mc.screen.mouseClicked(x, y, 1), "Root-screen right click cancels 3D selection");
                require(scene.getSelectedPosFace() == null && allowed.stream().noneMatch(item ->
                                interaction.enhanced$materialSelection().borderColor(item) == 0xffff4040),
                        "Right click clears native selection and corresponding red material borders");
            }
            case 9 -> {
                capture("selection-cleared");
                done = true;
            }
            default -> throw new AssertionError("Unknown material audit step " + step);
        }
        step++;
        frames = 0;
    }

    private SlotWidget expandable(DraggableScrollableWidgetGroup row) {
        return row.widgets.stream().filter(SlotWidget.class::isInstance).map(SlotWidget.class::cast)
                .filter(slot -> slot.getXEIIngredients().isEmpty()).findFirst().orElseThrow();
    }

    private void duplicateHighlights(PatternPreviewWidget preview) throws Exception {
        var controls = preview.getPreviewControls();
        var toggle = (Widget) field(controls.getClass(), "replaceableToggle").get(controls);
        require(!preview.widgets.contains(toggle), "Upstream duplicate replaceable-highlight button is absent");
        // Force the upstream mode on, then invoke the merged render entry with a null pose.
        // The retained native body would reach the pose stack for this real coil preview;
        // our precise HEAD cancellation must return before any highlight rendering begins.
        var mode = field(controls.getClass(), "highlightReplaceable");
        mode.setBoolean(controls, true);
        var render = PatternPreviewWidget.class.getDeclaredMethod("renderReplaceableHighlights",
                com.mojang.blaze3d.vertex.PoseStack.class, BlockPos.class);
        render.setAccessible(true);
        try {
            render.invoke(preview, null, ((PreviewMaterialAccess) pattern(preview)).enhanced$controllerPosition());
            require(true, "Merged upstream replaceable-highlight drawing is disabled independently of fullscreen");
        } finally { mode.setBoolean(controls, false); }
    }

    private void coilCounts(PreviewMaterialAccess pattern) {
        var coils = pattern.enhanced$materials().entrySet().stream()
                .filter(entry -> net.minecraft.core.registries.BuiltInRegistries.ITEM
                        .getKey(entry.getValue().getItem()).getPath().contains("coil"))
                .collect(java.util.stream.Collectors.groupingBy(java.util.Map.Entry::getValue,
                        java.util.stream.Collectors.toList()));
        require(!coils.isEmpty(), "Real blast furnace preview contains heating coils");
        coils.forEach((item, positions) -> {
            int count = positions.size();
            require(count > 1 && PreviewMaterialRows.requirement(pattern, item, count).equals(String.valueOf(count)),
                    "Unbounded heating-coil group displays its actual count: " + count);
            var predicate = pattern.enhanced$predicates().get(positions.get(0).getKey());
            var options = pattern.enhanced$candidates(predicate);
            require(options.size() > 1, "Heating-coil count also covers replaceable alternatives");
            options.forEach(option -> require(PreviewMaterialRows.requirement(pattern, option, count)
                            .equals(String.valueOf(count)),
                    "Alternative coil retains the complete group count: " + count));
        });
    }

    private void compact(SceneWidget scene, Widget primary, Widget candidates) {
        require(primary.getSelfPositionY() == 110 && candidates.getSelfPositionY() == 134,
                "Compact material rows keep separate 110/134 positions");
        require(scene.getSizeWidth() == 150 && scene.getSizeHeight() == 104,
                "Compact scene restores its original viewport after fullscreen");
    }

    private void fullscreen(SceneWidget scene, Widget primary, Widget candidates) {
        var window = Minecraft.getInstance().getWindow();
        require(primary.getSelfPositionY() == window.getGuiScaledHeight() - 50
                        && candidates.getSelfPositionY() == window.getGuiScaledHeight() - 26,
                "Fullscreen material rows occupy separate lower strips");
        require(scene.getSelfPositionY() + scene.getSizeHeight() <= primary.getSelfPositionY(),
                "Fullscreen 3D viewport does not overlap its material strips");
    }

    private void camera(SceneWidget scene) {
        require(scene.getCenter().equals(center) && scene.getZoom() == zoom,
                "Visibility and material filtering preserve pan and zoom");
    }

    private void click(PatternPreviewWidget preview, Widget widget) {
        boolean fullscreen = preview.getPreviewControls().fullscreen();
        double x = widget.getPositionX() + widget.getSizeWidth() / 2.0 + (fullscreen ? 0 : offsetX);
        double y = widget.getPositionY() + widget.getSizeHeight() / 2.0 + (fullscreen ? 0 : offsetY);
        var screen = Minecraft.getInstance().screen;
        require(screen.mouseClicked(x, y, 0), "Actual screen accepts material/control click at " + step);
        Minecraft.getInstance().screen.mouseReleased(x, y, 0);
    }

    private void capture(String name) throws Exception {
        var mc = Minecraft.getInstance();
        try (var image = net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            image.writeToFile(mc.gameDirectory.toPath().resolve("pr6-size" + size + "-" + name + ".png"));
        }
    }

    private PreviewMaterialAccess pattern(PatternPreviewWidget widget) throws Exception {
        Object[] patterns = (Object[]) field(PatternPreviewWidget.class, "patterns").get(widget);
        return (PreviewMaterialAccess) patterns[field(PatternPreviewWidget.class, "index").getInt(widget)];
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private void require(boolean value, String message) {
        if (!value) throw new AssertionError("PR6 size=" + size + " step=" + step + ": " + message);
        checks.add(message);
    }
}
