package com.gtl.enhancedcore.client.renderer;

import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtl.enhancedcore.common.gui.GuiStyle;
import com.gtl.enhancedcore.common.world.campus.CampusDeviceBlocks;
import com.gtl.enhancedcore.common.world.campus.VirtualMarkerBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Readable site labels owned by the marker BE; replacement removes the label with it. */
@Mod.EventBusSubscriber(modid = GTLEnhancedcore.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class VirtualMarkerRenderer implements BlockEntityRenderer<VirtualMarkerBlockEntity> {
    private static final int MAX_DISTANCE = 64;
    private static final int CACHE_LIMIT = 256;
    private static final float TEXT_SCALE = 0.0225F;
    private static int resourcesRevision;
    private final Font font;
    private int seenResourcesRevision = -1;
    // The cache contains only value records and text, never block entities or worlds.
    private final Map<LabelKey, Label> labels = new LinkedHashMap<>(CACHE_LIMIT, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<LabelKey, Label> eldest) {
            return size() > CACHE_LIMIT;
        }
    };

    public VirtualMarkerRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
    }

    @SubscribeEvent
    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(CampusDeviceBlocks.VIRTUAL_MARKER_BLOCK_ENTITY.get(),
                VirtualMarkerRenderer::new);
    }

    @SubscribeEvent
    public static void registerReload(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> resourcesRevision++);
    }

    @Override
    public void render(VirtualMarkerBlockEntity marker, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        Minecraft minecraft = Minecraft.getInstance();
        if (marker.isRemoved() || minecraft.options.hideGui) return;
        Vec3 anchor = Vec3.atCenterOf(marker.getBlockPos()).add(0, 0.85, 0);
        double distance = anchor.distanceTo(minecraft.gameRenderer.getMainCamera().getPosition());
        if (distance >= MAX_DISTANCE) return;
        // Normal render depth preserves occlusion by buildings; never use SEE_THROUGH.
        float opacity = distance <= 40 ? 1F : (float) ((MAX_DISTANCE - distance) / 24);
        int alpha = Math.round(255 * opacity);
        if (alpha < 12) return;
        if (seenResourcesRevision != resourcesRevision) {
            labels.clear();
            seenResourcesRevision = resourcesRevision;
        }
        LabelKey key = new LabelKey(marker.machineId(), marker.displayNameKey(), marker.width(),
                marker.height(), marker.depth(), minecraft.getLanguageManager().getSelected());
        Label label = labels.computeIfAbsent(key, this::createLabel);
        pose.pushPose();
        pose.translate(0.5, 1.35, 0.5);
        pose.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
        pose.scale(-TEXT_SCALE, -TEXT_SCALE, TEXT_SCALE);
        int row = 0;
        int titleColor = (alpha << 24) | (GuiStyle.DARK_TEXT & 0xffffff);
        int detailColor = (alpha << 24) | (GuiStyle.DARK_ACCENT & 0xffffff);
        int background = (Math.round(0.80F * alpha) << 24) | 0x15262c;
        for (FormattedCharSequence line : label.title()) {
            draw(line, row++, titleColor, background, pose, buffers);
        }
        for (FormattedCharSequence line : label.detail()) {
            draw(line, row++, detailColor, background, pose, buffers);
        }
        pose.popPose();
    }

    private void draw(FormattedCharSequence line, int row, int color, int background,
                      PoseStack pose, MultiBufferSource buffers) {
        font.drawInBatch(line, -font.width(line) / 2F, row * (font.lineHeight + 2), color, false,
                pose.last().pose(), buffers, Font.DisplayMode.NORMAL, background, LightTexture.FULL_BRIGHT);
    }

    private Label createLabel(LabelKey key) {
        ResourceLocation id = ResourceLocation.tryParse(key.machineId());
        Component title;
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
            title = Component.translatable("gtl_enhancedcore.campus.marker.unassigned");
        } else {
            Component name = key.displayNameKey().isEmpty() ? BuiltInRegistries.BLOCK.get(id).getName()
                    : Component.translatable(key.displayNameKey());
            title = Component.translatable("gtl_enhancedcore.campus.marker.title", name);
        }
        // Measure the active resource-pack font. Widen only until the whole name fits
        // within three title lines; no code-point estimate and no ellipsis.
        int width = 180;
        List<FormattedCharSequence> lines = font.split(title, width);
        while (lines.size() > 3) {
            width += 24;
            lines = font.split(title, width);
        }
        List<FormattedCharSequence> detail = new ArrayList<>();
        detail.addAll(font.split(Component.translatable("gtl_enhancedcore.campus.marker.dimensions",
                key.width(), key.height(), key.depth()), width));
        detail.addAll(font.split(Component.translatable("gtl_enhancedcore.campus.marker.place_controller"), width));
        return new Label(List.copyOf(lines), List.copyOf(detail));
    }

    @Override
    public int getViewDistance() {
        return MAX_DISTANCE;
    }

    private record LabelKey(String machineId, String displayNameKey, int width, int height, int depth, String language) {}
    private record Label(List<FormattedCharSequence> title, List<FormattedCharSequence> detail) {}
}
