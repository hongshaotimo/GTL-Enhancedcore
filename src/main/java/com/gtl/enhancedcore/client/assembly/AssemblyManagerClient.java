package com.gtl.enhancedcore.client.assembly;

import com.gtl.enhancedcore.common.assembly.AssemblyManagerNetwork;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.multiplayer.ClientPacketListener;
import com.gtl.enhancedcore.client.search.PhoneticSearchMatcher;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import org.joml.Vector4f;

/** Client-side snapshot, map screen, teleport hint and manager marker HUD. */
@Mod.EventBusSubscriber(modid = "gtl_enhancedcore", value = Dist.CLIENT)
public final class AssemblyManagerClient {
    private static final int REFRESH_TICKS = 20;
    private static final Map<BlockPos, AssemblyManagerNetwork.Target> TARGETS = new HashMap<>();
    private static final Map<ResourceLocation, Map<BlockPos, AssemblyManagerNetwork.Target>> SNAPSHOTS = new HashMap<>();
    private static final Map<BlockPos, ScreenPoint> PROJECTED = new HashMap<>();
    private static ResourceLocation dimension;
    private static int ticks;
    private static boolean wasHolding;
    private static BlockPos markedTarget;
    private static boolean keyDown;
    private static double frameFov = 70;
    private static long lastHudFrameNanos;
    private static ClientPacketListener connection;
    private static String query = "";
    private static PhoneticSearchMatcher matcher = PhoneticSearchMatcher.compile("");
    private static final Map<BlockPos, LabelState> LABEL_STATES = new HashMap<>();
    private static final KeyMapping SEARCH_KEY = new KeyMapping("gtl_enhancedcore.assembly.search_key",
            GLFW.GLFW_KEY_BACKSLASH, "key.categories.gtl_enhancedcore");
    private record ScreenPoint(double x, double y, double depth) {}

    private AssemblyManagerClient() {}

    private static final KeyMapping TELEPORT_KEY = new KeyMapping("key.gtl_enhancedcore.assembly_teleport",
            GLFW.GLFW_KEY_LEFT_ALT, "key.categories.gtl_enhancedcore");

    @Mod.EventBusSubscriber(modid = "gtl_enhancedcore", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Keys {
        @SubscribeEvent public static void registerKeys(RegisterKeyMappingsEvent event) { event.register(TELEPORT_KEY); event.register(SEARCH_KEY); }
    }

    public static void accept(ResourceLocation receivedDimension, List<AssemblyManagerNetwork.Target> targets, boolean open) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null
                || !mc.level.dimension().location().equals(receivedDimension)) return;
        synchronizeSession(mc);
        if (!receivedDimension.equals(dimension)) return;
        TARGETS.clear();
        for (var target : targets) TARGETS.put(target.pos().immutable(), target);
        SNAPSHOTS.put(receivedDimension, new HashMap<>(TARGETS));
        PROJECTED.keySet().retainAll(TARGETS.keySet());
        LABEL_STATES.keySet().retainAll(TARGETS.keySet());
        if (!TARGETS.containsKey(markedTarget)) markedTarget = null;
    }

    public static void result(boolean success, Component message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.displayClientMessage(message, true);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            PROJECTED.clear(); LABEL_STATES.clear(); dimension = null; markedTarget = null; ticks = 0; wasHolding = false; keyDown = false; return;
        }
        synchronizeSession(mc);
        boolean holding = AssemblyManagerNetwork.isHolding(mc.player);
        if (!holding) {
            PROJECTED.clear(); LABEL_STATES.clear(); markedTarget = null; ticks = 0; wasHolding = false;
        } else if (!wasHolding || ++ticks >= REFRESH_TICKS) {
            ticks = 0; AssemblyManagerNetwork.requestRefresh();
        }
        wasHolding = holding;
        while (SEARCH_KEY.consumeClick()) {
            if (holding && mc.screen == null && !mc.options.hideGui) mc.setScreen(new HudSearchScreen());
        }
        boolean down = TELEPORT_KEY.isDown();
        if (down && !keyDown && !mc.options.hideGui && mc.screen == null && holding && markedTarget != null)
            AssemblyManagerNetwork.requestTeleport(dimension, markedTarget);
        keyDown = down;
        while (TELEPORT_KEY.consumeClick()) { }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void captureFov(ViewportEvent.ComputeFov event) {
        if (event.usedConfiguredFov()) frameFov = event.getFOV();
    }

    @SubscribeEvent
    public static void project(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        BlockPos previousTarget = markedTarget;
        PROJECTED.clear(); markedTarget = null;
        if (mc.player == null || mc.level == null || mc.options.hideGui
                || (mc.screen != null && !(mc.screen instanceof HudSearchScreen)) || !AssemblyManagerNetwork.isHolding(mc.player)
                || !mc.level.dimension().location().equals(dimension)) return;
        var camera = event.getCamera();
        double cx = camera.getPosition().x(), cy = camera.getPosition().y(), cz = camera.getPosition().z();
        // Reuse this frame's interpolated camera pose, but omit scene-only hurt/view-bob transforms.
        var projection = mc.gameRenderer.getProjectionMatrix(frameFov);
        var combined = new org.joml.Matrix4f(projection).mul(event.getPoseStack().last().pose());
        BlockPos lookingClosest = null;
        double lookingScore = Double.MAX_VALUE;
        BlockPos anchorClosest = null;
        double anchorScore = Double.MAX_VALUE;
        double middleX = mc.getWindow().getGuiScaledWidth() / 2.0;
        double middleY = mc.getWindow().getGuiScaledHeight() / 2.0;
        List<AssemblyManagerNetwork.Target> stableTargets = new ArrayList<>(TARGETS.values());
        stableTargets.sort(Comparator.comparingLong(t -> t.pos().asLong()));
        for (var target : stableTargets) {
            if (!matcher.matches(displayName(target.name()).getString())) continue;
            BlockPos pos = target.pos();
            double distanceSq = mc.player.position().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos));
            Vector4f clip = combined.transform(new Vector4f((float)(pos.getX() + .5 - cx),
                    (float)(pos.getY() + .5 - cy), (float)(pos.getZ() + .5 - cz), 1));
            if (clip.w <= 0) continue;
            double nx = clip.x / clip.w, ny = clip.y / clip.w;
            if (Math.abs(nx) > 1.2 || Math.abs(ny) > 1.2) continue;
            double sx = (nx * .5 + .5) * mc.getWindow().getGuiScaledWidth();
            double sy = (1 - (ny * .5 + .5)) * mc.getWindow().getGuiScaledHeight();
            ScreenPoint point = new ScreenPoint(sx, sy, Math.sqrt(distanceSq));
            PROJECTED.put(pos, point);
            double pointDistance = Math.hypot(sx - middleX, sy - middleY);
            LabelState state = LABEL_STATES.computeIfAbsent(pos, ignored -> new LabelState());
            state.baseBounds = labelBounds(mc, target, sx, sy);
            if (pointDistance < 9 && pointDistance < anchorScore) {
                anchorClosest = pos;
                anchorScore = pointDistance;
            } else if (pointDistance < 75 && state.alpha > .1f && state.baseBounds.contains(middleX, middleY, 0)
                    && point.depth < lookingScore) {
                lookingScore = point.depth;
                lookingClosest = pos;
            }
        }
        LabelState previousState = LABEL_STATES.get(previousTarget);
        markedTarget = anchorClosest != null ? anchorClosest
                : PROJECTED.containsKey(previousTarget) && previousState != null && previousState.alpha > .1f && previousState.baseBounds != null
                && previousState.baseBounds.contains(middleX, middleY, 8) ? previousTarget : lookingClosest;
        LABEL_STATES.keySet().retainAll(PROJECTED.keySet());
    }

    @SubscribeEvent
    public static void drawHud(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui
                || (mc.screen != null && !(mc.screen instanceof HudSearchScreen)) || !AssemblyManagerNetwork.isHolding(mc.player)
                || !mc.level.dimension().location().equals(dimension)) return;
        GuiGraphics g = event.getGuiGraphics();
        List<Map.Entry<BlockPos, ScreenPoint>> markers = new ArrayList<>(PROJECTED.entrySet());
        markers.sort(Comparator.comparingLong(e -> e.getKey().asLong()));
        updateLabelAnimation(mc, g.guiWidth(), g.guiHeight());
        markers.sort(Comparator.comparingInt(e -> e.getKey().equals(markedTarget) ? 1 : 0));
        for (var entry : markers) drawAnchor(g, entry.getKey(), entry.getValue());
        for (var entry : markers) drawWorldName(g, mc, entry.getKey());
        if (!query.isBlank()) {
            g.drawString(mc.font, Component.translatable("gtl_enhancedcore.assembly.hud_filter", query, markers.size()),
                    7, 7, 0xffffe99a, true);
        }
        g.drawString(mc.font, Component.translatable("gtl_enhancedcore.assembly.hud_search_hint", SEARCH_KEY.getTranslatedKeyMessage()),
                7, g.guiHeight() - 18, 0xffc1d5d3, true);
        if (markedTarget != null && TARGETS.containsKey(markedTarget)) {
            Component focusHint = Component.translatable("gtl_enhancedcore.assembly.teleport_hint", TELEPORT_KEY.getTranslatedKeyMessage());
            Component focused = Component.translatable("gtl_enhancedcore.assembly.marker_distance",
                    displayName(TARGETS.get(markedTarget).name()),
                    String.format(java.util.Locale.ROOT, "%.1f", PROJECTED.get(markedTarget).depth));
            int hintY = g.guiHeight() - 52;
            for (var line : mc.font.split(focused.copy().append(" · ").append(focusHint), Math.max(1, g.guiWidth() - 16))) {
                g.drawString(mc.font, line, Math.max(8, (g.guiWidth() - mc.font.width(line)) / 2), hintY,
                        0xffffe99a, true);
                hintY += mc.font.lineHeight + 1;
            }
        }
    }

    private record Bounds(float x, float y, int width, int height) {
        boolean contains(double px, double py, float margin) {
            return px >= x - margin && px < x + width + margin && py >= y - margin && py < y + height + margin;
        }
        boolean overlaps(Bounds other) {
            return x < other.x + other.width && x + width > other.x
                    && y < other.y + other.height && y + height > other.y;
        }
        Bounds scaled(float scale) {
            int scaledWidth = (int)Math.ceil(width * scale), scaledHeight = (int)Math.ceil(height * scale);
            return new Bounds(x + (width - scaledWidth) / 2f, y + (height - scaledHeight) / 2f,
                    scaledWidth, scaledHeight);
        }
    }

    private static final class LabelState {
        float alpha, scale = .8f;
        Bounds baseBounds;
    }

    private static Bounds labelBounds(Minecraft mc, AssemblyManagerNetwork.Target target, double x, double y) {
        int width = Math.min(220, Math.max(90, mc.getWindow().getGuiScaledWidth() - 16));
        var lines = mc.font.split(displayName(target.name()), width - 8);
        int textWidth = lines.stream().mapToInt(mc.font::width).max().orElse(0);
        return new Bounds((float)x + 7, (float)y - (lines.size() * (mc.font.lineHeight + 1) + 5) / 2f,
                Math.min(width, textWidth + 8), lines.size() * (mc.font.lineHeight + 1) + 5);
    }

    private static void updateLabelAnimation(Minecraft mc, int width, int height) {
        long now = System.nanoTime();
        double dt = lastHudFrameNanos == 0 ? 1.0 / 60 : Math.min(.1, Math.max(0, (now - lastHudFrameNanos) / 1_000_000_000.0));
        lastHudFrameNanos = now;
        double cx = width / 2.0, cy = height / 2.0;
        List<Map.Entry<BlockPos, ScreenPoint>> ordered = new ArrayList<>(PROJECTED.entrySet());
        ordered.sort(Comparator.<Map.Entry<BlockPos, ScreenPoint>>comparingInt(e -> e.getKey().equals(markedTarget) ? 0 : 1)
                .thenComparingDouble(e -> Math.hypot(e.getValue().x - cx, e.getValue().y - cy))
                .thenComparingDouble(e -> e.getValue().depth)
                .thenComparingLong(e -> e.getKey().asLong()));
        List<Bounds> occupied = new ArrayList<>();
        for (var entry : ordered) {
            LabelState state = LABEL_STATES.computeIfAbsent(entry.getKey(), ignored -> new LabelState());
            var point = entry.getValue();
            state.baseBounds = labelBounds(mc, TARGETS.get(entry.getKey()), point.x, point.y);
            double distance = Math.hypot(point.x - cx, point.y - cy);
            float focus = (float)Math.max(0, Math.min(1, (75 - distance) / 25));
            boolean selected = entry.getKey().equals(markedTarget);
            float alphaTarget = selected ? 1 : focus * .9f;
            float scaleTarget = selected ? 1.22f : .76f + .08f * focus;
            if (alphaTarget > .02f) {
                Bounds visibleBounds = state.baseBounds.scaled(scaleTarget);
                if (occupied.stream().anyMatch(visibleBounds::overlaps)) alphaTarget = 0;
                else occupied.add(visibleBounds);
            }
            double blend = 1.0 - Math.exp(-dt / .20);
            state.alpha += (alphaTarget - state.alpha) * (float)blend;
            state.scale += (scaleTarget - state.scale) * (float)blend;
        }
    }

    private static void drawAnchor(GuiGraphics g, BlockPos pos, ScreenPoint point) {
        boolean selected = pos.equals(markedTarget);
        g.pose().pushPose();
        g.pose().translate(point.x, point.y, 0);
        g.fill(-3, -3, 4, 4, 0xffffad32);
        g.renderOutline(-4, -4, 9, 9, selected ? 0xffffffff : 0xff472b10);
        if (selected) g.renderOutline(-6, -6, 13, 13, 0xffffe8a5);
        g.pose().popPose();
    }

    private static void drawWorldName(GuiGraphics g, Minecraft mc, BlockPos pos) {
        LabelState state = LABEL_STATES.get(pos);
        var target = TARGETS.get(pos);
        if (state == null || target == null || state.baseBounds == null || state.alpha < .02f) return;
        Bounds b = state.baseBounds;
        float alpha = state.alpha;
        boolean selected = pos.equals(markedTarget);
        int alphaByte = Math.min(255, (int)(alpha * 255));
        float scale = state.scale;
        g.pose().pushPose();
        g.pose().translate(b.x + b.width / 2f, b.y + b.height / 2f, 0);
        g.pose().scale(scale, scale, 1);
        g.pose().translate(-b.width / 2f, -b.height / 2f, 0);
        g.fill(0, 0, b.width, b.height, (alphaByte << 24) | 0x15252c);
        if (selected) g.renderOutline(0, 0, b.width, b.height, (alphaByte << 24) | 0x00ffd060);
        int textColor = (alphaByte << 24) | (selected ? 0x00fff0be : 0x00edf5f3);
        int lineY = 2;
        for (var line : mc.font.split(displayName(target.name()), b.width - 6)) {
            g.drawString(mc.font, line, 3, lineY, textColor, false);
            lineY += mc.font.lineHeight + 1;
        }
        g.pose().popPose();
    }

    @SubscribeEvent
    public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        clearSession();
    }

    private static void clearSession() {
        SNAPSHOTS.clear(); TARGETS.clear(); PROJECTED.clear(); LABEL_STATES.clear();
        connection = null; dimension = null; markedTarget = null; keyDown = false; lastHudFrameNanos = 0;
        ticks = 0; wasHolding = false;
        query = ""; matcher = PhoneticSearchMatcher.compile("");
    }

    private static void synchronizeSession(Minecraft mc) {
        if (connection != mc.getConnection()) {
            clearSession(); connection = mc.getConnection();
        }
        ResourceLocation current = mc.level.dimension().location();
        if (!current.equals(dimension)) {
            dimension = current; TARGETS.clear(); PROJECTED.clear(); LABEL_STATES.clear(); markedTarget = null;
            Map<BlockPos, AssemblyManagerNetwork.Target> cached = SNAPSHOTS.get(current);
            if (cached != null) TARGETS.putAll(cached);
            ticks = 0; wasHolding = false;
        }
    }

    private static final class HudSearchScreen extends Screen {
        private EditBox search;
        HudSearchScreen() { super(Component.translatable("gtl_enhancedcore.assembly.hud_search")); }
        @Override protected void init() {
            search = new EditBox(font, 8, 7, Math.max(50, width - 16), 18, title);
            search.setMaxLength(128); search.setValue(query);
            search.setHint(Component.translatable("gtl_enhancedcore.assembly.hud_search"));
            search.setResponder(value -> { query = value; matcher = PhoneticSearchMatcher.compile(value); });
            addRenderableWidget(search); setInitialFocus(search);
        }
        @Override public void tick() { search.tick(); }
        @Override public boolean keyPressed(int key, int scan, int modifiers) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) { onClose(); return true; }
            return super.keyPressed(key, scan, modifiers);
        }
        @Override public boolean isPauseScreen() { return false; }
    }

    public static List<AssemblyManagerNetwork.Target> targets() { return List.copyOf(TARGETS.values()); }
    public static ResourceLocation dimension() { return dimension; }
    public static Component displayName(String name) {
        return name == null || name.isBlank() ? Component.translatable("gtl_enhancedcore.assembly.unnamed") : Component.literal(name);
    }
}
