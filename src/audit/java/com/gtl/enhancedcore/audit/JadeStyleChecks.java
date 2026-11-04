package com.gtl.enhancedcore.audit;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec2;
import org.joml.Vector4f;
import snownee.jade.api.config.IWailaConfig;
import snownee.jade.api.theme.IThemeHelper;
import snownee.jade.api.ui.BoxStyle;
import snownee.jade.api.ui.Element;
import snownee.jade.api.ui.IElement;
import snownee.jade.api.ui.IElementHelper;
import snownee.jade.impl.Tooltip;
import snownee.jade.impl.ui.TextElement;
import snownee.jade.overlay.OverlayRenderer;
import snownee.jade.overlay.TooltipRenderer;

/** Native Jade renderer fixtures; no input, world access, viewport helpers or config saves. */
public final class JadeStyleChecks {
    private JadeStyleChecks() {}
    public static List<GuiStyleClientChecks.Scene> scenes() {
        return List.of(scene("jade-native-short", 0, 1), scene("jade-native-matrix-six-outputs", 6, 1),
                scene("jade-native-matrix-scaled", 6, 1.75f), scene("jade-native-long-list", 24, 1));
    }
    private static GuiStyleClientChecks.Scene scene(String name, int outputs, float scale) {
        return new GuiStyleClientChecks.Scene(name, () -> new JadeScreen(outputs, scale), screen -> ((JadeScreen)screen).verify());
    }
    private static void require(boolean valid, String message) { GuiStyleClientChecks.require(valid, message); }

    private static final class JadeScreen extends Screen {
        final int outputs;
        final float configuredScale;
        final Tooltip tooltip = new Tooltip();
        final List<RenderProbe> probes = new ArrayList<>();
        final List<List<IElement>> originals = new ArrayList<>();
        final TooltipRenderer renderer;
        final RenderProbe icon;
        final String message;
        float oldScale, oldX, oldY, oldAnchorX, oldAnchorY;
        IWailaConfig.IconMode oldIconMode;
        int frames;
        boolean completeFirstDraw;

        JadeScreen(int outputs, float scale) {
            super(Component.literal("Native Jade audit"));
            this.outputs = outputs; configuredScale = scale;
            if (outputs == 0) {
                text("机器 / Machine", ChatFormatting.AQUA);
                text("空闲 / Idle", ChatFormatting.WHITE);
            } else {
                text("铂系精炼矩阵 / Platinum matrix", ChatFormatting.AQUA);
                text("-60 3 -6", ChatFormatting.GRAY);
                var helper = IElementHelper.get();
                add(helper.progress(0.2f, Component.literal("1 / 5 s"),
                        helper.progressStyle().color(0xff146a78), BoxStyle.DEFAULT, false));
                text("3.46E18 EU/t (1.49A MAX+15)", ChatFormatting.WHITE);
                text("并行: 2147483647", ChatFormatting.GREEN);
                text("产物 / Outputs: " + outputs, ChatFormatting.GOLD);
                var products = List.of(Items.IRON_INGOT, Items.GOLD_INGOT, Items.COPPER_INGOT,
                        Items.DIAMOND, Items.EMERALD, Items.QUARTZ);
                var names = List.of("铂粉", "钯粉", "钌粉", "铑粉", "铱粉", "锇粉");
                var amounts = List.of("450.36P", "225.18P", "112.59P", "56.29P", "28.14P", "14.07P");
                for (int i = 0; i < outputs; i++) {
                    // GT/GTL RecipeOutputProvider supplies smallItem followed by appended TextElement.
                    add(helper.smallItem(products.get(i % products.size()).getDefaultInstance()));
                    append(new TextElement(Component.literal(" " + amounts.get(i % amounts.size())
                            + " × " + names.get(i % names.size()))));
                }
                text("维护 / Maintenance: 0", ChatFormatting.GREEN);
                text("ME: 在线 / Online", ChatFormatting.WHITE);
                text("批处理 / Batch: ON", ChatFormatting.WHITE);
                text("GregTech Leisure", ChatFormatting.BLUE);
            }
            for (var line : tooltip.lines) {
                var elements = new ArrayList<IElement>();
                elements.addAll(line.getAlignedElements(IElement.Align.LEFT));
                elements.addAll(line.getAlignedElements(IElement.Align.RIGHT)); originals.add(elements);
            }
            message = tooltip.getMessage();
            icon = outputs > 0 ? new RenderProbe(IElementHelper.get().item(Items.IRON_BLOCK.getDefaultInstance())) : null;
            renderer = new TooltipRenderer(tooltip, outputs > 0);
            if (icon != null) renderer.setIcon(icon);
        }
        private void text(String value, ChatFormatting color) { add(new TextElement(Component.literal(value).withStyle(color))); }
        private RenderProbe probe(IElement element) { var probe = new RenderProbe(element); probes.add(probe); return probe; }
        private void add(IElement element) { tooltip.add(probe(element)); }
        private void append(IElement element) { tooltip.append(tooltip.size() - 1, probe(element)); }

        @Override protected void init() {
            var overlay = IWailaConfig.get().getOverlay();
            oldScale = overlay.getOverlayScale(); oldX = overlay.getOverlayPosX(); oldY = overlay.getOverlayPosY();
            oldAnchorX = overlay.getAnchorX(); oldAnchorY = overlay.getAnchorY();
            oldIconMode = overlay.getIconMode();
            if (icon != null) overlay.setIconMode(IWailaConfig.IconMode.TOP);
            overlay.setOverlayScale(configuredScale); overlay.setOverlayPosX(0.5f); overlay.setOverlayPosY(1);
            overlay.setAnchorX(0.5f); overlay.setAnchorY(0);
            renderer.setPaddingFromTheme(IThemeHelper.get().theme()); renderer.recalculateRealRect();
        }
        @Override public void removed() {
            var overlay = IWailaConfig.get().getOverlay();
            overlay.setOverlayScale(oldScale); overlay.setOverlayPosX(oldX); overlay.setOverlayPosY(oldY);
            overlay.setAnchorX(oldAnchorX); overlay.setAnchorY(oldAnchorY);
            overlay.setIconMode(oldIconMode);
        }
        @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
            graphics.fill(0, 0, width, height, 0xff24313b);
            renderer.recalculateRealRect();
            var rect = renderer.getRealRect(); float scale = renderer.getRealScale();
            OverlayRenderer.theme.setValue(IThemeHelper.get().theme());
            float previousAlpha = OverlayRenderer.alpha;
            OverlayRenderer.alpha = 1;
            graphics.pose().pushPose();
            try {
                graphics.pose().translate(rect.getX(), rect.getY(), 0); graphics.pose().scale(scale, scale, 1);
                OverlayRenderer.drawTooltipBox(graphics, 0, 0, (int)Math.ceil(rect.getWidth() / scale),
                        (int)Math.ceil(rect.getHeight() / scale), 1, IWailaConfig.get().getOverlay().getSquare(), renderer);
                renderer.draw(graphics);
            } finally { graphics.pose().popPose(); OverlayRenderer.alpha = previousAlpha; }
            frames++;
            if (frames == 1) completeFirstDraw = probes.stream().allMatch(p -> p.draws == 1 && p.onScreen(width, height))
                    && (icon == null || icon.draws == 1 && icon.onScreen(width, height));
        }
        void verify() {
            var rect = renderer.getRealRect();
            require(frames > 0 && completeFirstDraw, "Native Jade draws every provider row in full on its first frame without input");
            require(rect.getX() >= -1 && rect.getY() >= -1 && rect.getX() + rect.getWidth() <= width + 1
                    && rect.getY() + rect.getHeight() <= height + 1, "Native Jade fixture fully visible: "
                    + rect.getX() + "," + rect.getY() + " " + rect.getWidth() + "x" + rect.getHeight() + " in " + width + "x" + height);
            float naturalWidth = 0, naturalHeight = 0;
            for (var line : tooltip.lines) { naturalWidth = Math.max(naturalWidth, line.getSize().x); naturalHeight += line.getSize().y; }
            naturalWidth += renderer.getPadding(1) + renderer.getPadding(3);
            naturalHeight += renderer.getPadding(0) + renderer.getPadding(2);
            float threshold = height * IWailaConfig.get().getOverlay().getAutoScaleThreshold();
            float expectedScale = naturalHeight * configuredScale > threshold
                    ? Math.max(configuredScale * 0.5f, threshold / naturalHeight) : configuredScale;
            require(Math.abs(renderer.getRealScale() - expectedScale) < 0.001f,
                    "Native Jade threshold autoscale: actual=" + renderer.getRealScale() + ", expected=" + expectedScale + ", threshold=" + threshold);
            int border = !IWailaConfig.get().getOverlay().getSquare() || IThemeHelper.get().theme().backgroundTexture != null ? 2 : 0;
            require(Math.abs(rect.getWidth() - (naturalWidth + border) * renderer.getRealScale()) <= 2
                    && Math.abs(rect.getHeight() - (naturalHeight + border) * renderer.getRealScale()) <= 2,
                    "Native Jade rectangle retains complete natural provider dimensions, theme padding and border");
            if (outputs == 0) require(renderer.getRealScale() == configuredScale, "Short native Jade keeps its natural size and configured scale");
            if (outputs == 24) require(naturalHeight * configuredScale <= threshold || renderer.getRealScale() < configuredScale,
                    "Long native Jade list uses automatic scale when its natural height exceeds the player threshold");
            require(renderer.getTooltip() == tooltip && tooltip.getMessage().equals(message), "Native Jade preserves complete narration and provider tooltip");
            require(tooltip.lines.size() == originals.size(), "Native Jade retains all source lines");
            for (int i = 0; i < originals.size(); i++) {
                var actual = new ArrayList<IElement>();
                actual.addAll(tooltip.lines.get(i).getAlignedElements(IElement.Align.LEFT));
                actual.addAll(tooltip.lines.get(i).getAlignedElements(IElement.Align.RIGHT));
                require(actual.equals(originals.get(i)), "Native Jade source identity retained for line " + i);
            }
            for (var probe : probes) {
                require(probe.draws == frames && probe.onScreen(width, height), "Native Jade element drawn every frame inside screen: "
                        + probe.getMessage() + "; draws=" + probe.draws + "; bounds=" + probe.left + "," + probe.top + ".." + probe.right + "," + probe.bottom);
                require(probe.delegate.getCachedSize().x == probe.nativeSize.x && probe.delegate.getCachedSize().y == probe.nativeSize.y,
                        "Native provider dimensions retained: " + probe.getMessage());
            }
            if (icon != null) {
                require(renderer.getIcon() == icon && icon.draws == frames && icon.onScreen(width, height),
                        "Native TOP machine icon drawn every frame inside screen: draws=" + icon.draws + "; bounds="
                                + icon.left + "," + icon.top + ".." + icon.right + "," + icon.bottom);
                require(renderer.getPadding(3) >= icon.nativeSize.x + 3, "Native TOP icon keeps its normal reserved column");
            }
            if (outputs > 0) require(probes.getLast().getMessage().contains("GregTech Leisure"),
                    "Maintenance, ME, batch and bottom mod-name rows share the complete first draw");
        }
    }

    /** Calls the real native renderer and records its transformed screen footprint. */
    private static final class RenderProbe extends Element {
        final IElement delegate;
        final Vec2 nativeSize;
        int draws;
        float left, top, right, bottom;
        RenderProbe(IElement delegate) {
            this.delegate = delegate; nativeSize = delegate.getCachedSize();
            align(delegate.getAlignment()); translate(delegate.getTranslation()); tag(delegate.getTag());
        }
        @Override public Vec2 getSize() { return nativeSize; }
        @Override public String getMessage() { return delegate.getCachedMessage(); }
        @Override public void render(GuiGraphics graphics, float x, float y, float maxX, float maxY) {
            draws++;
            var matrix = graphics.pose().last().pose();
            var min = new Vector4f(x, y, 0, 1).mul(matrix);
            var max = new Vector4f(x + nativeSize.x, y + nativeSize.y, 0, 1).mul(matrix);
            left = min.x(); top = min.y(); right = max.x(); bottom = max.y();
            delegate.render(graphics, x, y, maxX, maxY);
        }
        boolean onScreen(int width, int height) { return draws > 0 && left >= -1 && top >= -1 && right <= width + 1 && bottom <= height + 1; }
    }
}
