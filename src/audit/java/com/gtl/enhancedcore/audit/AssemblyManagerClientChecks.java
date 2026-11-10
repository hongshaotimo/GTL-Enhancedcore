package com.gtl.enhancedcore.audit;

import com.google.gson.GsonBuilder;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.block.MetaMachineBlock;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gtl.enhancedcore.client.assembly.AssemblyManagerClient;
import com.gtl.enhancedcore.client.assembly.AssemblyManagerScreen;
import com.gtl.enhancedcore.common.assembly.AssemblyManagerIndex;
import com.gtl.enhancedcore.common.assembly.AssemblyManagerNetwork;
import com.mojang.blaze3d.platform.InputConstants;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachineBase;
import org.lwjgl.glfw.GLFW;

/** Only the explicitly marked child client; never opens an existing world or attaches to a player. */
@Mod("enhancedcore_audit")
public final class AssemblyManagerClientChecks {
    private static final Path ROOT = Path.of("C:/IDEA/GTL-Enhancedcore/_audit/assembly-manager-20261109");
    private static final String WORLD = "assembly-manager-77-air";
    private static final BlockPos FIRST = new BlockPos(0, 96, 16), SECOND = new BlockPos(12, 96, 16);
    private static final int TARGET_COUNT = 77;
    private static final BlockPos FAR = new BlockPos(0, 96, 1700);
    private static final double START_Y = 96;
    private static final int WIDTH = 960, HEIGHT = 600, GUI_SCALE = 2;
    private static final InputConstants.Key ALT = InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_LEFT_ALT);
    private final List<String> checks = new ArrayList<>(), failures = new ArrayList<>();
    private final List<Map<String, Object>> captures = new ArrayList<>();
    private final Map<String, Object> travel = new LinkedHashMap<>();
    private Path directory;
    private int stage, ticks, frames;
    private long stageStarted = System.nanoTime();
    private boolean done, opened;
    private volatile boolean prepared, verified, serverStopped;
    private volatile Throwable serverFailure;
    private Map<BlockPos, List<Double>> labelOffsets;
    private float secondAlpha, secondScale, firstAlpha, firstScale, transitionAlpha, transitionScale;
    private Vec3 farOrigin;

    public AssemblyManagerClientChecks() {
        MinecraftForge.EVENT_BUS.addListener(this::tick);
        MinecraftForge.EVENT_BUS.addListener(this::render);
        MinecraftForge.EVENT_BUS.addListener(this::stopped);
    }
    private void stopped(ServerStoppedEvent event) { serverStopped = true; }
    private void render(TickEvent.RenderTickEvent event) {
        if (!done && event.phase == TickEvent.Phase.END && Minecraft.getInstance().getOverlay() == null) frames++;
    }
    private void next(int value) { stage = value; frames = 0; stageStarted = System.nanoTime(); }

    private void tick(TickEvent.ClientTickEvent event) {
        if (done || event.phase != TickEvent.Phase.END || !Boolean.getBoolean("gtl.enhancedcore.assemblyManagerAudit")) return;
        var mc = Minecraft.getInstance();
        try {
            if (++ticks > 18000) throw new AssertionError("Client audit timeout at stage " + stage);
            if (stage > 0 && stage != 1 && System.nanoTime() - stageStarted > 90_000_000_000L)
                throw new AssertionError("Stage timeout: " + stage);
            if (serverFailure != null) throw new AssertionError("Server fixture failed", serverFailure);
            if (mc.getOverlay() != null) return;
            switch (stage) {
                case 0 -> {
                    directory = mc.gameDirectory.toPath().toAbsolutePath().normalize();
                    require(directory.getParent().equals(ROOT) && Files.isRegularFile(directory.resolve("assembly-manager.marker")), "Marked isolated client directory");
                    if (mc.screen instanceof com.gtl.enhancedcore.client.gui.ChangelogScreen) { mc.screen.onClose(); return; }
                    if (!(mc.screen instanceof TitleScreen)) return;
                    require(mc.level == null && mc.getConnection() == null && !Files.exists(directory.resolve("saves/" + WORLD)), "No existing player world or connection");
                    Files.createDirectories(directory.resolve("screenshots"));
                    var rules = new GameRules(); rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
                    rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null); rules.getRule(GameRules.RULE_RANDOMTICKING).set(0, null);
                    next(1);
                    mc.createWorldOpenFlows().createFreshLevel(WORLD, new LevelSettings("Assembly Manager Audit", GameType.CREATIVE,
                            false, Difficulty.PEACEFUL, true, rules, WorldDataConfiguration.DEFAULT), new WorldOptions(20261109L, false, false),
                            r -> r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
                }
                case 1 -> {
                    if (mc.level == null || mc.player == null || mc.gameMode == null || mc.getSingleplayerServer() == null) return;
                    mc.setScreen(null); mc.options.hideGui = false; mc.options.pauseOnLostFocus = false;
                    var uuid = mc.player.getUUID();
                    mc.getSingleplayerServer().execute(() -> {
                        try {
                            var server = mc.getSingleplayerServer(); var level = server.overworld();
                            var definition = GTRegistries.MACHINES.get(AssemblyManagerIndex.TARGET);
                            if (definition == null) throw new AssertionError("Registered ADD super buffer missing");
                            for (int i = 0; i < TARGET_COUNT; i++) {
                                BlockPos pos = i == 0 ? FIRST : i == 1 ? SECOND : i == 2 ? FIRST.above(8)
                                        : new BlockPos(((i - 3) % 11 - 5) * 8, 96, 32 + (i - 3) / 11 * 8);
                                var state = definition.defaultBlockState().setValue(
                                        ((MetaMachineBlock) definition.getBlock()).rotationState.property, Direction.NORTH);
                                level.setBlock(pos, state, 3);
                                var machine = MetaMachine.getMachine(level.getChunkAt(pos), pos);
                                if (!(machine instanceof MEPatternBufferPartMachineBase buffer)) throw new AssertionError("Real super buffer BE missing");
                                buffer.setCustomName(i == 0 ? "审计总成 Alpha" : i == 1 ? "审计总成 Beta"
                                        : i % 9 == 0 || i == 2 ? "重复名称总成" : "审计总成 " + i);
                                if (!level.isEmptyBlock(pos.below())) throw new AssertionError("Air fixture unexpectedly has support");
                            }
                            var player = server.getPlayerList().getPlayer(uuid);
                            var item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("gtl_enhancedcore", "assembly_manager"));
                            if (!(item instanceof com.gtl.enhancedcore.common.item.AssemblyManagerItem)) throw new AssertionError("Registered manager item missing");
                            player.getInventory().selected = 0; player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
                            player.getAbilities().flying = true; player.onUpdateAbilities();
                            player.teleportTo(level, .5, START_Y, .5, 0, 4); level.setDayTime(6000);
                            player.getInventory().setChanged(); player.inventoryMenu.broadcastChanges();
                        } catch (Throwable failure) { serverFailure = failure; }
                        finally { prepared = true; }
                    }); next(2);
                }
                case 2 -> {
                    if (!prepared || mc.player == null || mc.player.distanceToSqr(.5, START_Y, .5) > .2 || !AssemblyManagerNetwork.isHolding(mc.player)) return;
                    resize(mc); require(mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND).consumesAction(), "Actual gameMode.useItem consumes registered manager use"); next(3);
                }
                case 3 -> {
                    if (!(mc.screen instanceof AssemblyManagerScreen) || frames < 18) return;
                    var targets = AssemblyManagerClient.targets();
                    if (targets.size() != TARGET_COUNT) return;
                    require(targets.stream().anyMatch(target -> target.pos().equals(FIRST) && target.name().equals("审计总成 Alpha"))
                            && targets.stream().anyMatch(target -> target.pos().equals(SECOND) && target.name().equals("审计总成 Beta"))
                            && targets.stream().anyMatch(target -> target.pos().equals(FIRST.above(8)))
                            && targets.stream().filter(target -> target.name().equals("重复名称总成")).count() > 1,
                            "Server snapshot contains 77 real named air buffers, duplicate names and same-XZ different-Y targets");
                    opened = true; capture(mc, "manager-open-960x600");
                    mc.screen.onClose(); addFarTarget(mc); next(10);
                }
                case 4 -> {
                    if (mc.screen != null || mc.player == null) return;
                    aimAt(mc, FIRST);
                    if (frames < 60 || !FIRST.equals(field(AssemblyManagerClient.class, "markedTarget").get(null))) return;
                    require(labelValue(FIRST, "alpha") > .9f && labelValue(FIRST, "scale") > 1.1f,
                            "Actual crosshair focus gradually reveals and enlarges the first target name");
                    labelOffsets = labelOffsets(FIRST, SECOND);
                    secondAlpha = labelValue(SECOND, "alpha"); secondScale = labelValue(SECOND, "scale");
                    firstAlpha = labelValue(FIRST, "alpha"); firstScale = labelValue(FIRST, "scale");
                    capture(mc, "hud-focus-Alpha"); aimAt(mc, SECOND); next(18);
                }
                case 7 -> {
                    if (verified && frames >= 12) {
                        require(Boolean.TRUE.equals(travel.get("farAltArrived")), "Production Alt reaches the far real total assembly in unsupported air");
                        capture(mc, "hud-far-alt-arrived"); disconnect(mc);
                    }
                }
                case 8 -> { if (mc.level == null && mc.getSingleplayerServer() == null && serverStopped) finish(mc); }
                case 10 -> {
                    if (mc.player == null || mc.player.distanceToSqr(.5, START_Y, .5) > .2 || frames < 18) return;
                    if (!AssemblyManagerClient.targets().stream().anyMatch(t -> t.pos().equals(FAR))) return;
                    mc.gui.getChat().clearMessages(true); next(4);
                }
                case 18 -> {
                    if (frames < 3) return;
                    if (!SECOND.equals(field(AssemblyManagerClient.class, "markedTarget").get(null))) return;
                    transitionAlpha = labelValue(SECOND, "alpha"); transitionScale = labelValue(SECOND, "scale");
                    require(transitionAlpha > secondAlpha && transitionScale > secondScale,
                            "Aiming at another real projected target starts the focus fade and scale animation");
                    capture(mc, "hud-focus-Beta-transition"); next(19);
                }
                case 19 -> {
                    if (System.nanoTime() - stageStarted < 1_000_000_000L) return;
                    require(labelValue(SECOND, "alpha") > secondAlpha + .1f
                                    && labelValue(SECOND, "scale") > secondScale + .05f
                                    && labelValue(SECOND, "alpha") > transitionAlpha + .01f
                                    && labelValue(SECOND, "scale") > transitionScale + .01f
                                    && labelValue(FIRST, "alpha") < firstAlpha - .05f
                                    && labelValue(FIRST, "scale") < firstScale - .05f,
                            "Focus transfer fades and enlarges the new name while smoothly shrinking the old name");
                    require(sameOffsets(labelOffsets, labelOffsets(FIRST, SECOND)),
                            "Name base bounds keep the same offsets from their own world projections during focus transfer");
                    travel.put("focusAlpha", List.of(secondAlpha, transitionAlpha, labelValue(SECOND, "alpha")));
                    travel.put("focusScale", List.of(secondScale, transitionScale, labelValue(SECOND, "scale")));
                    capture(mc, "hud-focus-Beta-settled");
                    farOrigin = new Vec3(100.5, START_Y, .5);
                    movePlayer(mc, farOrigin, 0, 0); next(20);
                }
                case 20 -> {
                    if (mc.player == null || mc.player.distanceToSqr(farOrigin) > .1) return;
                    aimAt(mc, FAR);
                    if (frames < 60 || !FAR.equals(field(AssemblyManagerClient.class, "markedTarget").get(null))) return;
                    var projected = (Map<?, ?>) field(AssemblyManagerClient.class, "PROJECTED").get(null);
                    var states = (Map<?, ?>) field(AssemblyManagerClient.class, "LABEL_STATES").get(null);
                    require(mc.player.distanceToSqr(Vec3.atCenterOf(FAR)) > 1500.0 * 1500,
                            "Real far target starts more than 1500 blocks from the player");
                    require(projected.containsKey(FAR) && states.containsKey(FAR)
                                    && labelValue(FAR, "alpha") > .9f && labelValue(FAR, "scale") > 1.1f,
                            "Real target at z=1700 projects and displays its focused name without a distance limit");
                    require(AssemblyManagerClient.targets().size() == TARGET_COUNT + 1,
                            "Snapshot includes all 77 near machines plus the registered far real machine");
                    capture(mc, "hud-far-name-visible");
                    KeyMapping.set(ALT, true); KeyMapping.click(ALT); next(22);
                }
                case 22 -> {
                    if (frames < 6) return;
                    KeyMapping.set(ALT, false); next(23);
                }
                case 23 -> {
                    if (mc.player == null || mc.player.distanceToSqr(Vec3.atCenterOf(FAR)) > 25) return;
                    require(mc.player.distanceToSqr(farOrigin) > 1500.0 * 1500 && mc.player.getAbilities().flying,
                            "Actual Alt input travels over 1500 blocks while preserving client flying state");
                    var uuid = mc.player.getUUID();
                    mc.getSingleplayerServer().execute(() -> {
                        try {
                            var player = mc.getSingleplayerServer().getPlayerList().getPlayer(uuid);
                            var level = player.serverLevel(); var feet = player.blockPosition();
                            if (player.distanceToSqr(Vec3.atCenterOf(FAR)) > 25 || player.distanceToSqr(farOrigin) <= 1500.0 * 1500)
                                throw new AssertionError("Alt did not move the actual server player beside the far target");
                            if (!level.noCollision(player, player.getBoundingBox()) || !level.isEmptyBlock(feet.below())
                                    || !level.getFluidState(feet).isEmpty() || !level.getFluidState(feet.above()).isEmpty())
                                throw new AssertionError("Far teleport did not land in collision-free dry unsupported air");
                            if (!player.getAbilities().flying) throw new AssertionError("Far teleport lost server flying state");
                            travel.put("farAltArrived", true); travel.put("farTarget", FAR.toShortString());
                            travel.put("farServerPosition", player.position().toString());
                            travel.put("unsupportedAir", true); travel.put("flyingPreserved", true); travel.put("productionAltInput", true);
                        } catch (Throwable failure) { serverFailure = failure; }
                        finally { verified = true; }
                    }); next(7);
                }
                default -> throw new IllegalStateException("Unknown stage " + stage);
            }
        } catch (Throwable failure) {
            failures.add("stage " + stage + ": " + failure); failure.printStackTrace(); serverFailure = null;
            if (stage == 8 || mc.level == null && mc.getSingleplayerServer() == null) finish(mc); else disconnect(mc);
        }
    }
    private void resize(Minecraft mc) {
        mc.options.guiScale().set(GUI_SCALE);
        GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), WIDTH, HEIGHT); GLFW.glfwPollEvents(); mc.resizeDisplay();
    }
    private static void aim(Minecraft mc, float yaw, float pitch) {
        mc.player.setYRot(yaw); mc.player.setXRot(pitch); mc.player.yRotO = yaw; mc.player.xRotO = pitch;
    }
    private void movePlayer(Minecraft mc, Vec3 pos, float yaw, float pitch) {
        var uuid = mc.player.getUUID();
        mc.getSingleplayerServer().execute(() -> {
            try {
                var player = mc.getSingleplayerServer().getPlayerList().getPlayer(uuid);
                player.teleportTo(player.serverLevel(), pos.x, pos.y, pos.z, yaw, pitch);
            } catch (Throwable failure) { serverFailure = failure; }
        });
    }
    private void addFarTarget(Minecraft mc) {
        mc.getSingleplayerServer().execute(() -> {
            try {
                var level = mc.getSingleplayerServer().overworld();
                var definition = GTRegistries.MACHINES.get(AssemblyManagerIndex.TARGET);
                var state = definition.defaultBlockState().setValue(
                        ((MetaMachineBlock) definition.getBlock()).rotationState.property, Direction.NORTH);
                level.setBlock(FAR, state, 3);
                var machine = MetaMachine.getMachine(level.getChunkAt(FAR), FAR);
                if (!(machine instanceof MEPatternBufferPartMachineBase buffer)) throw new AssertionError("Far real buffer missing");
                buffer.setCustomName("远距总成 Far 1700"); AssemblyManagerIndex.update(buffer);
                if (!AssemblyManagerIndex.get(level).contains(FAR)) throw new AssertionError("Far buffer was not indexed");
            } catch (Throwable failure) { serverFailure = failure; }
        });
    }
    private static void aimAt(Minecraft mc, BlockPos pos) {
        Vec3 delta = Vec3.atCenterOf(pos).subtract(mc.player.getEyePosition());
        aim(mc, (float)Math.toDegrees(Math.atan2(-delta.x, delta.z)),
                (float)-Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z))));
    }
    private static float labelValue(BlockPos pos, String name) throws Exception {
        var states = (Map<?, ?>) field(AssemblyManagerClient.class, "LABEL_STATES").get(null);
        Object state = states.get(pos);
        if (state == null) throw new AssertionError("No projected label state for " + pos);
        return field(state.getClass(), name).getFloat(state);
    }
    private static Map<BlockPos, List<Double>> labelOffsets(BlockPos... positions) throws Exception {
        Map<BlockPos, List<Double>> result = new LinkedHashMap<>();
        var projected = (Map<?, ?>) field(AssemblyManagerClient.class, "PROJECTED").get(null);
        var states = (Map<?, ?>) field(AssemblyManagerClient.class, "LABEL_STATES").get(null);
        for (var pos : positions) {
            Object point = projected.get(pos), state = states.get(pos);
            if (point == null || state == null) throw new AssertionError("Expected real label projection for " + pos);
            Object bounds = field(state.getClass(), "baseBounds").get(state);
            result.put(pos, List.of(((Number) field(bounds.getClass(), "x").get(bounds)).doubleValue() - field(point.getClass(), "x").getDouble(point),
                    ((Number) field(bounds.getClass(), "y").get(bounds)).doubleValue() - field(point.getClass(), "y").getDouble(point),
                    (double) field(bounds.getClass(), "width").getInt(bounds), (double) field(bounds.getClass(), "height").getInt(bounds)));
        }
        return result;
    }
    private static boolean sameOffsets(Map<BlockPos, List<Double>> before, Map<BlockPos, List<Double>> after) {
        for (var pos : before.keySet()) for (int i = 0; i < 4; i++)
            if (Math.abs(before.get(pos).get(i) - after.get(pos).get(i)) > .01) return false;
        return true;
    }
    private void capture(Minecraft mc, String name) throws Exception {
        if (mc.screen instanceof AssemblyManagerScreen) {
            require(mc.getWindow().getWidth() == WIDTH && mc.getWindow().getHeight() == HEIGHT, "Actual requested GUI framebuffer " + name);
        }
        String file = "screenshots/" + name + ".png";
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) { image.writeToFile(directory.resolve(file)); }
        captures.add(Map.of("file", file, "frames", frames, "logicalWidth", mc.getWindow().getGuiScaledWidth(), "logicalHeight", mc.getWindow().getGuiScaledHeight()));
    }
    private void disconnect(Minecraft mc) {
        KeyMapping.set(ALT, false); next(8);
        if (mc.level != null) mc.level.disconnect(); mc.clearLevel(); mc.setScreen(new TitleScreen());
    }
    private static Field field(Class<?> type, String name) throws Exception { var f = type.getDeclaredField(name); f.setAccessible(true); return f; }
    private void require(boolean valid, String message) { if (!valid) throw new AssertionError(message); checks.add(message); }
    private void finish(Minecraft mc) {
        done = true; KeyMapping.set(ALT, false);
        try {
            var result = new LinkedHashMap<String, Object>(); result.put("status", failures.isEmpty() && opened && verified && serverStopped ? "PASS" : "FAIL");
            result.put("checks", checks); result.put("failures", failures); result.put("screenshots", captures); result.put("travel", travel);
            result.put("worldOpened", opened); result.put("serverStopped", serverStopped);
            Files.writeString(directory.resolve("assembly-manager-checks.json"), new GsonBuilder().setPrettyPrinting().create().toJson(result));
        } catch (Exception failure) { failure.printStackTrace(); }
        mc.stop();
    }
}
