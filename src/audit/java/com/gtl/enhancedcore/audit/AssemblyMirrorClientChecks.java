package com.gtl.enhancedcore.audit;

import appeng.api.networking.GridHelper;
import appeng.core.definitions.AEBlocks;
import com.google.gson.GsonBuilder;
import com.gregtechceu.gtceu.api.block.MetaMachineBlock;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gtl.enhancedcore.client.assembly.AssemblyManagerScreen;
import com.gtl.enhancedcore.client.assembly.AssemblyMirrorClient;
import com.gtl.enhancedcore.common.assembly.AssemblyMirrorNetwork;
import com.gtl.enhancedcore.common.assembly.AssemblyMirrorService;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachine;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferProxyPartMachine;
import org.lwjgl.glfw.GLFW;

/** Explicitly marked child client only; the world and all machines are newly created. */
@Mod("enhancedcore_audit")
public final class AssemblyMirrorClientChecks {
    private static final Path ROOT = Path.of("C:/IDEA/GTL-Enhancedcore/_audit/assembly-mirror-20261110");
    private static final String WORLD = "assembly-mirror-network";
    private static final BlockPos ANCHOR = new BlockPos(0, 96, 1), FIRST = new BlockPos(3, 96, 1),
            SECOND = new BlockPos(6, 96, 1), OTHER = new BlockPos(9, 96, 1), PROXY = new BlockPos(1, 96, 4);
    private static final InteractionHand HAND = InteractionHand.MAIN_HAND;
    private final List<String> checks = new ArrayList<>(), failures = new ArrayList<>();
    private final List<Map<String, Object>> captures = new ArrayList<>();
    private final Map<String, Object> evidence = new LinkedHashMap<>();
    private Path directory;
    private int stage, frames, ticks;
    private long stageStarted = System.nanoTime();
    private boolean done, opened, connected;
    private volatile boolean serverDone, serverStopped;
    private volatile Throwable serverFailure;

    public AssemblyMirrorClientChecks() {
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
        if (done || event.phase != TickEvent.Phase.END || !Boolean.getBoolean("gtl.enhancedcore.assemblyMirrorAudit")) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (++ticks > 18000 || stage > 1 && System.nanoTime() - stageStarted > 90_000_000_000L)
                throw new AssertionError("Client audit timeout at stage " + stage);
            if (serverFailure != null) throw new AssertionError("Server fixture failed", serverFailure);
            if (mc.getOverlay() != null) return;
            switch (stage) {
                case 0 -> {
                    directory = mc.gameDirectory.toPath().toAbsolutePath().normalize();
                    require(directory.getParent().equals(ROOT) && Files.isRegularFile(directory.resolve("assembly-mirror.marker")), "Marked isolated client directory");
                    if (mc.screen instanceof com.gtl.enhancedcore.client.gui.ChangelogScreen) { mc.screen.onClose(); return; }
                    if (!(mc.screen instanceof TitleScreen)) return;
                    require(mc.level == null && mc.getConnection() == null && !Files.exists(directory.resolve("saves/" + WORLD)), "Fresh isolated world only");
                    Files.createDirectories(directory.resolve("screenshots"));
                    var rules = new GameRules(); rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
                    rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null); rules.getRule(GameRules.RULE_RANDOMTICKING).set(0, null);
                    next(1);
                    mc.createWorldOpenFlows().createFreshLevel(WORLD, new LevelSettings("Assembly Mirror Audit", GameType.CREATIVE,
                            false, Difficulty.PEACEFUL, true, rules, WorldDataConfiguration.DEFAULT), new WorldOptions(20261110L, false, false),
                            r -> r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
                }
                case 1 -> {
                    if (mc.level == null || mc.player == null || mc.gameMode == null || mc.getSingleplayerServer() == null) return;
                    mc.setScreen(null); mc.options.pauseOnLostFocus = false; mc.options.hideGui = false;
                    server(mc, player -> {
                        var level = player.serverLevel();
                        level.setBlock(ANCHOR, AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState(), 3);
                        place(player, FIRST, "me_super_pattern_buffer"); place(player, SECOND, "me_super_pattern_buffer");
                        place(player, OTHER, "me_super_pattern_buffer"); place(player, PROXY, "me_super_pattern_buffer_proxy");
                        buffer(player, FIRST).setCustomName("Alpha 审计总成"); buffer(player, SECOND).setCustomName("Beta 审计总成");
                        buffer(player, OTHER).setCustomName("Other 别网总成");
                        var item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("gtl_enhancedcore", "assembly_manager"));
                        player.getInventory().selected = 0; player.setItemInHand(HAND, new ItemStack(item));
                        player.getAbilities().flying = true; player.onUpdateAbilities();
                        player.teleportTo(level, .5, 96, 2.5, 0, 0); level.setDayTime(6000); sync(player);
                    }); next(2);
                }
                case 2 -> {
                    if (!serverDone || frames < 80) return;
                    server(mc, player -> {
                        var anchor = GridHelper.getExposedNode(player.serverLevel(), ANCHOR, Direction.UP);
                        if (anchor == null) throw new AssertionError("Creative energy anchor has no exposed node");
                        GridHelper.createConnection(anchor, buffer(player, FIRST).getMainNode().getNode());
                        GridHelper.createConnection(anchor, buffer(player, SECOND).getMainNode().getNode());
                        require(buffer(player, OTHER).getMainNode().getNode() != null
                                && buffer(player, OTHER).getMainNode().getNode().getGrid() != anchor.getGrid(),
                                "Disconnected comparison buffer has a real separate AE node");
                        AssemblyMirrorNetwork.bindNetwork(player, HAND, ANCHOR, Direction.UP);
                        evidence.put("networkInitialization", "Server bindNetwork with real creative-energy-cell anchor; two real super-buffer nodes joined using GridHelper.createConnection");
                    }); next(3);
                }
                case 3 -> {
                    if (!serverDone || frames < 100) return;
                    resize(mc, 960, 600);
                    require(mc.gameMode.useItem(mc.player, HAND).consumesAction(), "Actual manager use opens GUI"); next(4);
                }
                case 4 -> {
                    var view = AssemblyMirrorClient.view();
                    if (!(mc.screen instanceof AssemblyManagerScreen) || view == null || !view.online() || frames < 18) return;
                    require(view.sources().size() == 2 && view.sources().stream().anyMatch(s -> s.pos().equals(FIRST))
                            && view.sources().stream().anyMatch(s -> s.pos().equals(SECOND))
                            && view.sources().stream().noneMatch(s -> s.pos().equals(OTHER)), "Sources enumerate selected AE network and exclude disconnected buffer: online="
                            + view.online() + ", anchor=" + view.anchor() + ", sources=" + view.sources());
                    connected = opened = true; clickRow(mc, FIRST); next(5);
                }
                case 5 -> {
                    if (!selected(FIRST) || frames < 12) return;
                    capture(mc, "sources-480x300", 480, 300);
                    mc.screen.onClose();
                    server(mc, player -> {
                        ItemStack before = player.getItemInHand(HAND);
                        CompoundTag saved = before.save(new CompoundTag()); ItemStack restored = ItemStack.of(saved);
                        CompoundTag binding = restored.getTag().getCompound(AssemblyMirrorService.SAVE_KEY);
                        require(binding.getString("dimension").equals(player.serverLevel().dimension().location().toString())
                                && binding.getLong("anchor") == ANCHOR.asLong() && binding.getLong("selectedSource") == FIRST.asLong(), "ItemStack.save/of preserves selected network and source");
                        evidence.put("persistedBinding", binding.toString()); player.setItemInHand(HAND, restored); sync(player);
                    }); next(6);
                }
                case 6 -> {
                    if (!serverDone || frames < 18) return;
                    require(mc.gameMode.useItemOn(mc.player, HAND, new BlockHitResult(Vec3.atCenterOf(PROXY), Direction.NORTH, PROXY, false)).consumesAction(),
                            "Actual gameMode.useItemOn right-clicks real super mirror"); next(7);
                }
                case 7 -> {
                    var view = AssemblyMirrorClient.view();
                    if (view == null || view.mirrors().stream().noneMatch(m -> m.pos().equals(PROXY) && m.source().equals(FIRST)) || frames < 18) return;
                    server(mc, player -> {
                        require(proxy(player).getBuffer() == buffer(player, FIRST) && FIRST.equals(proxy(player).getBufferPos()), "Real super mirror binding reads back selected source");
                    });
                    require(mc.gameMode.useItem(mc.player, HAND).consumesAction(), "Reopen real manager after binding"); next(8);
                }
                case 8 -> {
                    if (!serverDone || !(mc.screen instanceof AssemblyManagerScreen) || frames < 18) return;
                    clickRow(mc, SECOND); next(9);
                }
                case 9 -> {
                    if (!selected(SECOND) || frames < 12) return;
                    clickButton(mc, "mirrorsTab"); clickRow(mc, PROXY); clickButton(mc, "rebind"); next(10);
                }
                case 10 -> {
                    var view = AssemblyMirrorClient.view();
                    if (view == null || view.mirrors().stream().noneMatch(m -> m.pos().equals(PROXY) && m.source().equals(SECOND)) || frames < 18) return;
                    server(mc, player -> {
                        require(proxy(player).getBuffer() == buffer(player, SECOND), "GUI mouse-click rebind updates actual mirror");
                        player.teleportTo(player.serverLevel(), 32.5, 96, 2.5, 0, 0);
                    }); resize(mc, 640, 480); next(11);
                }
                case 11 -> {
                    if (!serverDone || mc.player.distanceToSqr(Vec3.atCenterOf(PROXY)) <= 64 || frames < 30) return;
                    clickRow(mc, PROXY); capture(mc, "mirror-management-320x240", 320, 240);
                    clickButton(mc, "unbind"); next(12);
                }
                case 12 -> {
                    var view = AssemblyMirrorClient.view();
                    if (view == null || !view.mirrors().isEmpty() || frames < 18) return;
                    server(mc, player -> {
                        require(player.distanceToSqr(Vec3.atCenterOf(PROXY)) > 64 && proxy(player).getBuffer() == null
                                && proxy(player).getBufferPos() == null, "Remote GUI mouse-click unbind clears actual mirror beyond right-click reach");
                        evidence.put("remoteUnbindDistanceSquared", player.distanceToSqr(Vec3.atCenterOf(PROXY)));
                    }); next(13);
                }
                case 13 -> { if (serverDone) disconnect(mc); }
                case 14 -> { if (mc.level == null && mc.getSingleplayerServer() == null && serverStopped) finish(mc); }
                default -> throw new IllegalStateException("Unknown stage " + stage);
            }
        } catch (Throwable failure) {
            failures.add("stage " + stage + ": " + failure); failure.printStackTrace(); serverFailure = null;
            if (stage == 14 || mc.level == null && mc.getSingleplayerServer() == null) finish(mc); else disconnect(mc);
        }
    }

    private void server(Minecraft mc, Consumer<ServerPlayer> action) {
        serverDone = false; var id = mc.player.getUUID();
        mc.getSingleplayerServer().execute(() -> {
            try { action.accept(mc.getSingleplayerServer().getPlayerList().getPlayer(id)); }
            catch (Throwable failure) { serverFailure = failure; }
            finally { serverDone = true; }
        });
    }
    private static void sync(ServerPlayer player) { player.getInventory().setChanged(); player.inventoryMenu.broadcastChanges(); }
    private static void place(ServerPlayer player, BlockPos pos, String id) {
        var definition = GTRegistries.MACHINES.get(new ResourceLocation("gtladditions", id));
        if (definition == null) throw new AssertionError("Missing real machine " + id);
        player.serverLevel().setBlock(pos, definition.defaultBlockState().setValue(
                ((MetaMachineBlock) definition.getBlock()).rotationState.property, Direction.NORTH), 3);
    }
    private static MEPatternBufferPartMachine buffer(ServerPlayer player, BlockPos pos) {
        return (MEPatternBufferPartMachine) MetaMachine.getMachine(player.serverLevel().getChunkAt(pos), pos);
    }
    private static MEPatternBufferProxyPartMachine proxy(ServerPlayer player) {
        return (MEPatternBufferProxyPartMachine) MetaMachine.getMachine(player.serverLevel().getChunkAt(PROXY), PROXY);
    }
    private static boolean selected(BlockPos pos) { var view = AssemblyMirrorClient.view(); return view != null && pos.equals(view.selectedSource()); }
    private static Field field(String name) throws Exception {
        var f = AssemblyManagerScreen.class.getDeclaredField(name); f.setAccessible(true); return f;
    }
    private void clickButton(Minecraft mc, String name) throws Exception {
        click(mc, (AbstractWidget) field(name).get(mc.screen), "Screen.mouseClicked " + name);
    }
    private void clickRow(Minecraft mc, BlockPos pos) throws Exception {
        var entries = (List<?>) field("filtered").get(mc.screen);
        int scroll = field("scroll").getInt(mc.screen), index = -1;
        for (int i = 0; i < entries.size(); i++) {
            var f = entries.get(i).getClass().getDeclaredField("pos"); f.setAccessible(true);
            if (pos.equals(f.get(entries.get(i)))) index = i;
        }
        var buttons = (List<?>) field("rowButtons").get(mc.screen);
        require(index >= scroll && index - scroll < buttons.size(), "Requested real GUI row is visible: " + pos);
        click(mc, (AbstractWidget) buttons.get(index - scroll), "Screen.mouseClicked row " + pos);
    }
    private void click(Minecraft mc, AbstractWidget button, String label) {
        require(button.active && button.visible, label + " enabled");
        double x = button.getX() + button.getWidth() / 2.0, y = button.getY() + button.getHeight() / 2.0;
        require(mc.screen.mouseClicked(x, y, 0), label + " handled"); mc.screen.mouseReleased(x, y, 0);
    }
    private static void resize(Minecraft mc, int width, int height) {
        mc.options.guiScale().set(2); GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), width, height);
        GLFW.glfwPollEvents(); mc.resizeDisplay();
    }
    private void capture(Minecraft mc, String name, int width, int height) throws Exception {
        require(mc.getWindow().getGuiScaledWidth() == width && mc.getWindow().getGuiScaledHeight() == height,
                "Actual GUI logical dimensions " + width + "x" + height);
        String file = "screenshots/" + name + ".png";
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) { image.writeToFile(directory.resolve(file)); }
        captures.add(Map.of("file", file, "logicalWidth", width, "logicalHeight", height));
    }
    private void disconnect(Minecraft mc) {
        next(14); if (mc.level != null) mc.level.disconnect(); mc.clearLevel(); mc.setScreen(new TitleScreen());
    }
    private void require(boolean valid, String message) { if (!valid) throw new AssertionError(message); checks.add(message); }
    private void finish(Minecraft mc) {
        done = true;
        try {
            var result = new LinkedHashMap<String, Object>();
            result.put("status", failures.isEmpty() && opened && connected && serverStopped && captures.size() == 2 ? "PASS" : "FAIL");
            result.put("checks", checks); result.put("failures", failures); result.put("screenshots", captures);
            result.put("evidence", evidence); result.put("worldOpened", opened); result.put("serverStopped", serverStopped);
            Files.writeString(directory.resolve("assembly-mirror-checks.json"), new GsonBuilder().setPrettyPrinting().create().toJson(result));
        } catch (Exception failure) { failure.printStackTrace(); }
        mc.stop();
    }
}
