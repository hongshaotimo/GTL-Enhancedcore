package com.gtl.enhancedcore.audit;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import com.google.gson.GsonBuilder;
import com.gregtechceu.gtceu.api.block.MetaMachineBlock;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gtl.enhancedcore.client.patterncut.PatternCutClient;
import com.gtl.enhancedcore.client.patterncut.PatternCutScreen;
import com.gtl.enhancedcore.common.patterncut.PatternCutService;
import com.lowdragmc.lowdraglib.side.fluid.FluidStack;
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
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachine;
import org.lwjgl.glfw.GLFW;

/** One new-world interaction session; controller membership is an explicit audit fixture. */
@Mod("enhancedcore_audit")
public final class PatternCutClientChecks {
    private static final Path ROOT = Path.of("C:/IDEA/GTL-Enhancedcore/_audit/pattern-cut-20261112");
    private static final String WORLD = "pattern-cut-rework";
    private static final BlockPos CONTROL = new BlockPos(0, 96, 0), FIRST = new BlockPos(2, 96, 0),
            SECOND = new BlockPos(4, 96, 0), BETA = new BlockPos(6, 96, 0), EMPTY = new BlockPos(6, 96, 3),
            ALPHA = new BlockPos(0, 96, 3), MANUAL = new BlockPos(3, 96, 3);
    private static final InteractionHand HAND = InteractionHand.MAIN_HAND;
    private final List<String> checks = new ArrayList<>(), failures = new ArrayList<>();
    private final List<Map<String, Object>> captures = new ArrayList<>();
    private final Map<String, Object> evidence = new LinkedHashMap<>();
    private Path directory;
    private int stage, frames, ticks;
    private long stageStarted = System.nanoTime();
    private boolean done, opened;
    private volatile boolean serverDone, serverStopped;
    private volatile Throwable serverFailure;
    private volatile ServerPlayer fixturePlayer;
    private ItemStack existingPattern;
    private java.util.UUID directRecord;

    public PatternCutClientChecks() {
        MinecraftForge.EVENT_BUS.addListener(this::tick); MinecraftForge.EVENT_BUS.addListener(this::render);
        MinecraftForge.EVENT_BUS.addListener(this::stopped); MinecraftForge.EVENT_BUS.addListener(this::serverTick);
    }
    private void stopped(ServerStoppedEvent event) { serverStopped = true; }
    private void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && fixturePlayer != null && !done) {
            try { configureController(fixturePlayer); } catch (Throwable failure) { serverFailure = failure; }
        }
    }
    private void render(TickEvent.RenderTickEvent event) {
        if (!done && event.phase == TickEvent.Phase.END && Minecraft.getInstance().getOverlay() == null) frames++;
    }
    private void next(int value) { stage = value; frames = 0; stageStarted = System.nanoTime(); }
    private void tick(TickEvent.ClientTickEvent event) {
        if (done || event.phase != TickEvent.Phase.END || !Boolean.getBoolean("gtl.enhancedcore.patternCutAudit")) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (++ticks > 18000 || stage > 1 && System.nanoTime() - stageStarted > 90_000_000_000L)
                throw new AssertionError("Client audit timeout at stage " + stage + "; player="
                        + (mc.player == null ? "null" : mc.player.position()) + "; view=" + PatternCutClient.view());
            if (serverFailure != null) throw new AssertionError("Server fixture failed", serverFailure);
            if (mc.getOverlay() != null) return;
            switch (stage) {
                case 0 -> {
                    directory = mc.gameDirectory.toPath().toAbsolutePath().normalize();
                    require(directory.getParent().equals(ROOT) && Files.isRegularFile(directory.resolve("pattern-cut.marker")), "Marked isolated client directory");
                    if (mc.screen instanceof com.gtl.enhancedcore.client.gui.ChangelogScreen) { mc.screen.onClose(); return; }
                    if (!(mc.screen instanceof TitleScreen)) return;
                    require(mc.level == null && mc.getConnection() == null && !Files.exists(directory.resolve("saves/" + WORLD)), "Fresh isolated world only");
                    Files.createDirectories(directory.resolve("screenshots"));
                    var rules = new GameRules(); rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
                    rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null); rules.getRule(GameRules.RULE_RANDOMTICKING).set(0, null);
                    next(1);
                    mc.createWorldOpenFlows().createFreshLevel(WORLD, new LevelSettings("Pattern Cut Rework Audit", GameType.CREATIVE,
                            false, Difficulty.PEACEFUL, true, rules, WorldDataConfiguration.DEFAULT), new WorldOptions(20261112L, false, false),
                            r -> r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
                }
                case 1 -> {
                    if (mc.level == null || mc.player == null || mc.gameMode == null || mc.getSingleplayerServer() == null) return;
                    mc.setScreen(null); mc.options.pauseOnLostFocus = false; mc.options.hideGui = false;
                    server(mc, player -> {
                        place(player, CONTROL, "gtceu", "large_chemical_reactor");
                        for (var pos : List.of(FIRST, SECOND, BETA, EMPTY, ALPHA, MANUAL)) place(player, pos, "gtladditions", "me_super_pattern_buffer");
                        for (var pos : List.of(FIRST, SECOND, ALPHA)) buffer(player, pos).setCustomName("Alpha 审计总成");
                        buffer(player, BETA).setCustomName("Beta 审计总成"); buffer(player, MANUAL).setCustomName("Unmatched 手动目标");
                        seed(player, FIRST, pattern(Items.APPLE, Items.GOLD_INGOT), 2, 1, 11);
                        seed(player, SECOND, pattern(Items.CARROT, Items.IRON_INGOT), 3, 1, 17);
                        seed(player, BETA, pattern(Items.POTATO, Items.COPPER_INGOT), 5, 1, 7);
                        existingPattern = pattern(Items.COBBLESTONE, Items.EMERALD);
                        seed(player, MANUAL, existingPattern.copy(), 13, 2, 100);
                        player.getInventory().selected = 0;
                        player.setItemInHand(HAND, new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("gtlcore", "me_pattern_buffer_cut"))));
                        player.serverLevel().setBlock(new BlockPos(2, 95, 1), Blocks.BEDROCK.defaultBlockState(), 3);
                        player.getAbilities().flying = false; player.onUpdateAbilities();
                        player.teleportTo(player.serverLevel(), 2.5, 96, 1.5, 0, 0); player.serverLevel().setDayTime(6000);
                        configureController(player); fixturePlayer = player; player.setShiftKeyDown(true); sync(player);
                        evidence.put("fixture", "Real LCR and real super buffers; audit-only isFormed plus native partPositions and bidirectional membership, not a physically formed structure");
                    }); next(2);
                }
                case 2 -> {
                    if (!serverDone || frames < 70) return;
                    shift(mc, true); useBlock(mc, EMPTY, "Native Shift-right-click empty source"); next(3);
                }
                case 3 -> {
                    var view = PatternCutClient.view();
                    if (view == null || frames < 20) return;
                    require(view.entries().isEmpty() && !view.message().getString().isBlank(), "Empty source creates no record and returns a visible reason");
                    evidence.put("emptySourceMessage", Component.Serializer.toJson(view.message()));
                    useBlock(mc, FIRST, "Native Shift-right-click first Alpha source"); next(4);
                }
                case 4 -> {
                    if (PatternCutClient.view() == null || PatternCutClient.view().entries().size() != 1 || frames < 15) return;
                    useBlock(mc, SECOND, "Native Shift-right-click second Alpha source"); next(5);
                }
                case 5 -> {
                    if (PatternCutClient.view() == null || PatternCutClient.view().entries().size() != 2 || frames < 15) return;
                    useBlock(mc, BETA, "Native Shift-right-click Beta source"); next(6);
                }
                case 6 -> {
                    if (PatternCutClient.view() == null || PatternCutClient.view().entries().size() != 3 || frames < 15) return;
                    shift(mc, false);
                    server(mc, player -> {
                        player.setShiftKeyDown(false);
                        try {
                            var entries = PatternCutService.store(player).list();
                            require(entries.size() == 3 && entries.stream().map(e -> e.id()).distinct().count() == 3, "Three independent cut files remain behind two displayed names");
                            var path = player.server.getWorldPath(LevelResource.ROOT).resolve("data/gtl_enhancedcore/pattern_cut").resolve(player.getUUID().toString());
                            require(entries.stream().allMatch(e -> Files.isRegularFile(path.resolve(e.id() + ".nbt"))), "All three cut payloads are independent files");
                            var tag = player.getItemInHand(HAND).getTagElement("GT.Behaviours");
                            require(tag == null || !tag.contains("cut"), "Held tool contains no full cut payload");
                            for (var pos : List.of(FIRST, SECOND, BETA)) require(patternCount(buffer(player, pos)) == 0 && shared(buffer(player, pos), 0, 0, 0), "Cut clears patterns and shared materials at " + pos);
                            evidence.put("recordIds", entries.stream().map(e -> e.id().toString()).toList());
                        } catch (Exception failure) { throw new RuntimeException(failure); }
                    }); next(7);
                }
                case 7 -> {
                    if (!serverDone || frames < 15) return;
                    resize(mc, 960, 600, 2);
                    require(mc.gameMode.useItem(mc.player, HAND).consumesAction(), "Native air-right-click opens record selector"); next(8);
                }
                case 8 -> {
                    if (!(mc.screen instanceof PatternCutScreen) || frames < 20) return;
                    require(((List<?>) field("filtered").get(mc.screen)).size() == 3, "Three independent cut records remain three GUI rows including duplicate Alpha names");
                    var search = (EditBox) field("search").get(mc.screen);
                    search.setValue("Alpha"); require(((List<?>) field("filtered").get(mc.screen)).size() == 2, "Name search keeps both independent Alpha records");
                    search.setValue("shenji"); require(((List<?>) field("filtered").get(mc.screen)).size() == 3, "Pinyin search finds all three named records");
                    search.setValue("4, 96, 0"); require(((List<?>) field("filtered").get(mc.screen)).size() == 1, "Source coordinates distinguish duplicate Alpha records");
                    search.setValue(""); opened = true;
                    capture(mc, "records-960x600-gui2", 480, 300);
                    clickButton(mc, "smartMode"); next(9);
                }
                case 9 -> {
                    if (!PatternCutClient.view().smart() || frames < 15) return;
                    require(true, "Smart mode GUI click is confirmed by server snapshot");
                    resize(mc, 1280, 720, 3); next(10);
                }
                case 10 -> {
                    if (frames < 18) return;
                    capture(mc, "records-1280x720-gui3", 427, 240); mc.screen.onClose();
                    useBlock(mc, CONTROL, "Native right-click controller in smart mode"); next(11);
                }
                case 11 -> {
                    var view = PatternCutClient.view();
                    if (view == null || view.entries().size() != 1 || frames < 20) return;
                    require(view.entries().getFirst().name().startsWith("Beta"), "Smart paste consumes both Alpha files and retains unmatched Beta");
                    server(mc, player -> {
                        require(patternCount(buffer(player, ALPHA)) == 2 && shared(buffer(player, ALPHA), 5, 2, 28), "Smart same-name group merges two patterns and all shared materials");
                        require(patternCount(buffer(player, MANUAL)) == 1 && shared(buffer(player, MANUAL), 13, 2, 100), "Unmatched target retains its existing pattern and materials");
                    }); next(12);
                }
                case 12 -> {
                    if (!serverDone || frames < 15) return;
                    if (mc.screen != null) mc.screen.onClose();
                    require(mc.gameMode.useItem(mc.player, HAND).consumesAction(), "Reopen selector for direct mode"); resize(mc, 640, 480, 2); next(13);
                }
                case 13 -> {
                    if (!(mc.screen instanceof PatternCutScreen) || frames < 20) return;
                    directRecord = PatternCutClient.view().entries().getFirst().id();
                    clickRow(mc, 0); next(14);
                }
                case 14 -> {
                    if (frames < 12 || PatternCutClient.view().selected() == null
                            || !PatternCutClient.view().selected().equals(directRecord)) return;
                    clickButton(mc, "directMode"); next(15);
                }
                case 15 -> {
                    if (PatternCutClient.view().smart() || frames < 15) return;
                    require(true, "Direct mode GUI click is confirmed by server snapshot");
                    capture(mc, "direct-320x240", 320, 240); mc.screen.onClose();
                    useBlock(mc, MANUAL, "Native right-click different-name occupied target in direct mode"); next(16);
                }
                case 16 -> {
                    if (PatternCutClient.view() == null || !PatternCutClient.view().entries().isEmpty() || frames < 20) return;
                    server(mc, player -> {
                        var target = buffer(player, MANUAL);
                        require(patternCount(target) == 2 && ItemStack.isSameItemSameTags(existingPattern, target.getPatternInventory().getStackInSlot(0)), "Direct paste appends Beta while preserving the existing pattern");
                        require(shared(target, 18, 3, 107), "Direct paste adds materials without replacing occupied shared storage");
                        try { require(PatternCutService.store(player).list().isEmpty(), "All pasted files are consumed exactly once"); }
                        catch (Exception failure) { throw new RuntimeException(failure); }
                    }); next(17);
                }
                case 17 -> { if (serverDone) disconnect(mc); }
                case 18 -> { if (mc.level == null && mc.getSingleplayerServer() == null && serverStopped) finish(mc); }
                default -> throw new IllegalStateException("Unknown stage " + stage);
            }
        } catch (Throwable failure) {
            failures.add("stage " + stage + ": " + failure); failure.printStackTrace(); serverFailure = null;
            if (stage == 18 || mc.level == null && mc.getSingleplayerServer() == null) finish(mc); else disconnect(mc);
        }
    }
    private void server(Minecraft mc, Consumer<ServerPlayer> action) {
        serverDone = false; var id = mc.player.getUUID();
        mc.getSingleplayerServer().execute(() -> {
            try { action.accept(mc.getSingleplayerServer().getPlayerList().getPlayer(id)); }
            catch (Throwable failure) { serverFailure = failure; } finally { serverDone = true; }
        });
    }
    private static void sync(ServerPlayer player) { player.getInventory().setChanged(); player.inventoryMenu.broadcastChanges(); }
    private static void shift(Minecraft mc, boolean down) { mc.options.keyShift.setDown(down); mc.player.setShiftKeyDown(down); }
    private void useBlock(Minecraft mc, BlockPos pos, String label) {
        if (pos.equals(BETA)) {
            String position = "player=" + mc.player.position() + ", targetDistanceSquared=" + mc.player.distanceToSqr(Vec3.atCenterOf(pos));
            evidence.put("betaClickPosition", position); System.out.println("PATTERN-CUT-POSITION " + position);
        }
        require(mc.gameMode.useItemOn(mc.player, HAND, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)).consumesAction(), label);
    }
    private static ItemStack pattern(net.minecraft.world.item.Item input, net.minecraft.world.item.Item output) {
        return PatternDetailsHelper.encodeProcessingPattern(new GenericStack[]{new GenericStack(AEItemKey.of(new ItemStack(input)), 1)}, new GenericStack[]{new GenericStack(AEItemKey.of(new ItemStack(output)), 1)});
    }
    private static void seed(ServerPlayer player, BlockPos pos, ItemStack pattern, int diamonds, int circuits, int water) {
        var buffer = buffer(player, pos); buffer.getPatternInventory().setStackInSlot(0, pattern);
        buffer.getSharedCatalystInventory().setStackInSlot(0, new ItemStack(Items.DIAMOND, diamonds));
        buffer.getSharedCircuitInventory().setStackInSlot(0, new ItemStack(Items.REDSTONE, circuits));
        buffer.getSharedCatalystTank().setFluidInTank(0, FluidStack.create(Fluids.WATER, water));
    }
    private static int patternCount(MEPatternBufferPartMachine buffer) {
        int count = 0; for (int i = 0; i < buffer.getPatternInventory().getSlots(); i++) if (!buffer.getPatternInventory().getStackInSlot(i).isEmpty()) count++; return count;
    }
    private static boolean shared(MEPatternBufferPartMachine buffer, int diamonds, int circuits, int water) {
        return (diamonds == 0 || buffer.getSharedCatalystInventory().getStackInSlot(0).is(Items.DIAMOND))
                && (circuits == 0 || buffer.getSharedCircuitInventory().getStackInSlot(0).is(Items.REDSTONE))
                && buffer.getSharedCatalystInventory().getStackInSlot(0).getCount() == diamonds
                && buffer.getSharedCircuitInventory().getStackInSlot(0).getCount() == circuits
                && buffer.getSharedCatalystTank().getFluidInTank(0).getAmount() == water;
    }
    private static void place(ServerPlayer player, BlockPos pos, String namespace, String id) {
        var definition = GTRegistries.MACHINES.get(new ResourceLocation(namespace, id));
        if (definition == null) throw new AssertionError("Missing real machine " + namespace + ":" + id);
        player.serverLevel().setBlock(pos, definition.defaultBlockState().setValue(((MetaMachineBlock) definition.getBlock()).rotationState.property, Direction.NORTH), 3);
    }
    private static MEPatternBufferPartMachine buffer(ServerPlayer player, BlockPos pos) { return (MEPatternBufferPartMachine) MetaMachine.getMachine(player.serverLevel().getChunkAt(pos), pos); }
    private static void configureController(ServerPlayer player) {
        try {
            var controller = (MultiblockControllerMachine) MetaMachine.getMachine(player.serverLevel().getChunkAt(CONTROL), CONTROL);
            var formed = MultiblockControllerMachine.class.getDeclaredField("isFormed"); formed.setAccessible(true); formed.setBoolean(controller, true);
            var positions = MultiblockControllerMachine.class.getDeclaredField("partPositions"); positions.setAccessible(true); positions.set(controller, new BlockPos[]{ALPHA, MANUAL});
            controller.getParts().clear(); for (var pos : List.of(ALPHA, MANUAL)) buffer(player, pos).addedToController(controller);
            if (controller.getParts().size() != 2) throw new AssertionError("Controller fixture lost native part positions");
        } catch (ReflectiveOperationException failure) { throw new RuntimeException(failure); }
    }
    private static Field field(String name) throws Exception { var field = PatternCutScreen.class.getDeclaredField(name); field.setAccessible(true); return field; }
    private void clickButton(Minecraft mc, String name) throws Exception { click(mc, (AbstractWidget) field(name).get(mc.screen), name); }
    private void clickRow(Minecraft mc, int slot) throws Exception { click(mc, (AbstractWidget) ((List<?>) field("recordRows").get(mc.screen)).get(slot), "record " + slot); }
    private void click(Minecraft mc, AbstractWidget button, String label) {
        require(button.active && button.visible, "GUI " + label + " enabled");
        double x = button.getX() + button.getWidth() / 2.0, y = button.getY() + button.getHeight() / 2.0;
        require(mc.screen.mouseClicked(x, y, 0), "Screen.mouseClicked " + label + " handled"); mc.screen.mouseReleased(x, y, 0);
    }
    private static void resize(Minecraft mc, int width, int height, int scale) {
        mc.options.guiScale().set(scale); GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), width, height); GLFW.glfwPollEvents(); mc.resizeDisplay();
    }
    private void capture(Minecraft mc, String name, int width, int height) throws Exception {
        require(mc.getWindow().getGuiScaledWidth() == width && mc.getWindow().getGuiScaledHeight() == height, "Actual logical dimensions " + width + "x" + height);
        require(field("panelWidth").getInt(mc.screen) == 300 && field("panelHeight").getInt(mc.screen) == 220, "GUI stays a centered 300x220 window");
        String file = "screenshots/" + name + ".png";
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) { image.writeToFile(directory.resolve(file)); }
        captures.add(Map.of("file", file, "logicalWidth", width, "logicalHeight", height));
    }
    private void disconnect(Minecraft mc) {
        fixturePlayer = null; mc.options.keyShift.setDown(false); if (mc.player != null) mc.player.setShiftKeyDown(false);
        next(18); if (mc.level != null) mc.level.disconnect(); mc.clearLevel(); mc.setScreen(new TitleScreen());
    }
    private void require(boolean valid, String message) { if (!valid) throw new AssertionError(message); checks.add(message); }
    private void finish(Minecraft mc) {
        done = true;
        try {
            var result = new LinkedHashMap<String, Object>();
            result.put("status", failures.isEmpty() && opened && serverStopped && captures.size() == 3 ? "PASS" : "FAIL");
            result.put("checks", checks); result.put("failures", failures); result.put("screenshots", captures);
            result.put("evidence", evidence); result.put("worldOpened", opened); result.put("serverStopped", serverStopped);
            Files.writeString(directory.resolve("pattern-cut-checks.json"), new GsonBuilder().setPrettyPrinting().create().toJson(result));
        } catch (Exception failure) { failure.printStackTrace(); }
        mc.stop();
    }
}
