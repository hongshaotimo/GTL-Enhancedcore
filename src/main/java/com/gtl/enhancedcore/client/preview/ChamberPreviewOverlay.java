package com.gtl.enhancedcore.client.preview;

import com.lowdragmc.lowdraglib.gui.widget.SceneWidget;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;

/** A scene owns only its currently visible chamber positions, never the preview world. */
public final class ChamberPreviewOverlay {
    private static final Map<SceneWidget,SceneState> SCENES = new WeakHashMap<>();
    private static final class SceneState {
        private List<BlockPos> positions = List.of();
        private boolean enabled;
    }
    private ChamberPreviewOverlay() {}
    public static void update(SceneWidget scene, PreviewPatternMemory memory, boolean enabled) {
        var state = SCENES.computeIfAbsent(Objects.requireNonNull(scene), ignored -> new SceneState());
        state.positions = memory.enhanced$chamberPositions().stream().filter(scene.getCore()::contains).toList();
        state.enabled = enabled;
    }
    public static void clear(SceneWidget scene) { SCENES.remove(scene); }
    public static void setEnabled(SceneWidget scene, boolean enabled) {
        SCENES.computeIfAbsent(Objects.requireNonNull(scene), ignored -> new SceneState()).enabled = enabled;
    }
    public static boolean enabled(SceneWidget scene) {
        var state = SCENES.get(scene);
        return state != null && state.enabled;
    }
    public static boolean contains(SceneWidget scene) { return enabled(scene); }
    public static void render(SceneWidget scene) {
        var state = SCENES.get(scene);
        if (state != null && state.enabled)
            com.gtl.enhancedcore.client.structure.ChamberHighlightRenderer.draw(state.positions, 0, 0, 0);
    }
}
