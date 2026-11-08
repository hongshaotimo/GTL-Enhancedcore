package com.gtl.enhancedcore.mixin.gtlcore.client;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gtl.enhancedcore.client.preview.PreviewMaterialAccess;
import com.gtl.enhancedcore.client.preview.PreviewMaterialRows;
import net.minecraft.core.Direction;
import com.lowdragmc.lowdraglib.gui.editor.ColorPattern;
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.SceneWidget;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.gtlcore.gtlcore.api.gui.PatternPreviewWidget;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PatternPreviewWidget.class, remap = false)
public abstract class PreviewVisibilityMixin implements PreviewMaterialAccess.Interaction {
    @Unique private PreviewMaterialRows.Selection enhanced$selection;
    @Unique private PreviewMaterialAccess enhanced$pattern;
    @Unique private Set<BlockPos> enhanced$selectedPositions = Set.of();

    @Override
    public PreviewMaterialRows.Selection enhanced$materialSelection() {
        if (enhanced$selection == null) enhanced$selection = new PreviewMaterialRows.Selection();
        return enhanced$selection;
    }

    @Override
    public void enhanced$refreshVisibility() {
        var selected = enhanced$materialSelection().selected();
        var positions = new HashSet<BlockPos>();
        if (selected != null && enhanced$pattern != null) {
            enhanced$pattern.enhanced$predicates().forEach((pos, predicate) -> {
                if (enhanced$pattern.enhanced$candidates(predicate).contains(selected)) positions.add(pos);
            });
        }
        enhanced$selectedPositions = positions;
        enhanced$applyVisibility();
    }

    /** The local three-mode visibility control already owns replaceable-material inspection. */
    @Inject(method = "renderReplaceableHighlights", at = @At("HEAD"), cancellable = true, require = 1)
    private void enhanced$disableDuplicateHighlights(com.mojang.blaze3d.vertex.PoseStack pose,
            BlockPos controller, CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "onPosSelected", at = @At("TAIL"), require = 1)
    private void enhanced$highlightAllowed(BlockPos pos, Direction facing, CallbackInfo ci) {
        if (enhanced$pattern != null) {
            enhanced$materialSelection().position(enhanced$pattern.enhanced$predicates().get(pos));
        }
    }
    @Shadow @Final private SceneWidget sceneWidget;
    @Unique private int enhanced$visibilityMode;
    @Unique private List<BlockPos> enhanced$unfilteredCore = List.of();
    @Unique private Set<BlockPos> enhanced$replaceable = Set.of();
    @Unique private BlockPos enhanced$controller;

    @Inject(method = "<init>", at = @At("TAIL"), require = 1)
    private void enhanced$visibilityButton(CallbackInfo ci) {
        var preview = (PatternPreviewWidget) (Object) this;
        var controls = preview.widgets.stream().filter(ButtonWidget.class::isInstance)
                .map(ButtonWidget.class::cast).filter(button -> button.getSelfPositionX() == 138)
                .sorted((left, right) -> Integer.compare(left.getSelfPositionY(), right.getSelfPositionY())).toList();
        for (var button : controls) {
            if (button.getSelfPositionY() >= 70) button.setSelfPosition(138, button.getSelfPositionY() + 20);
        }
        var label = new TextTexture("").setSupplier(() -> Component.translatable(enhanced$modeKey()).getString());
        var small = new GuiTextureGroup(ColorPattern.T_GRAY.rectTexture(), label);
        var large = new GuiTextureGroup(GuiTextures.BUTTON, label);
        var button = new ButtonWidget(138, 70, 18, 18,
                (graphics, mouseX, mouseY, x, y, width, height) ->
                        (preview.getPreviewControls().fullscreen() ? large : small)
                                .draw(graphics, mouseX, mouseY, x, y, width, height),
                click -> {
                    enhanced$visibilityMode = (enhanced$visibilityMode + 1) % 3;
                    enhanced$applyVisibility();
                });
        button.setHoverBorderTexture(1, -1);
        button.setHoverTooltips(Component.translatable("gtl_enhancedcore.preview.visibility_hint"));
        preview.addWidget(button);
    }

    @Inject(method = "setupScene", at = @At("TAIL"), require = 1)
    private void enhanced$filterScene(@Coerce Object pattern, CallbackInfo ci) {
        var access = (PreviewMaterialAccess) pattern;
        if (enhanced$pattern != access) enhanced$materialSelection().clear();
        enhanced$pattern = access;
        enhanced$controller = access.enhanced$controllerPosition();
        enhanced$replaceable = access.enhanced$replaceablePositions();
        enhanced$unfilteredCore = List.copyOf(sceneWidget.getCore());
        enhanced$refreshVisibility();
    }

    @Unique
    private String enhanced$modeKey() {
        return switch (enhanced$visibilityMode) {
            case 1 -> "gtl_enhancedcore.preview.visibility_controller";
            case 2 -> "gtl_enhancedcore.preview.visibility_replaceable";
            default -> "gtl_enhancedcore.preview.visibility_all";
        };
    }

    @Unique
    private void enhanced$applyVisibility() {
        var visible = enhanced$unfilteredCore.stream().filter(pos -> enhanced$visibilityMode == 0
                || pos.equals(enhanced$controller)
                || enhanced$visibilityMode == 2 && (enhanced$materialSelection().selected() == null
                        ? enhanced$replaceable.contains(pos) : enhanced$selectedPositions.contains(pos))).toList();
        var core = sceneWidget.getCore();
        if (core.size() == visible.size() && core.containsAll(visible)) return;
        // 山海：native setRenderedCore also reframes the camera and invalidates its mesh.
        var center = sceneWidget.getCenter();
        float zoom = sceneWidget.getZoom();
        sceneWidget.setRenderedCore(visible, null);
        sceneWidget.setCenter(center);
        sceneWidget.setZoom(zoom);
    }
}
