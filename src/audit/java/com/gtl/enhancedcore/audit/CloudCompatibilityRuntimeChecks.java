package com.gtl.enhancedcore.audit;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.GridHelper;
import appeng.api.stacks.*;
import appeng.blockentity.networking.CreativeEnergyCellBlockEntity;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.*;
import com.google.gson.GsonBuilder;
import com.gregtechceu.gtceu.api.machine.*;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.common.machine.multiblock.part.MaintenanceHatchPartMachine;
import com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder;
import com.gtl.enhancedcore.client.plot.*;
import com.gtl.enhancedcore.common.cloud.*;
import com.gtl.enhancedcore.common.cloud.store.CloudPatternStore;
import com.gtl.enhancedcore.common.recipe.iv.*;
import com.gtl.enhancedcore.common.world.plot.PlotSettings;
import com.mojang.authlib.GameProfile;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.fml.common.Mod;
import org.gtlcore.gtlcore.integration.ae2.pattern.PatternQuickUploadMetadata;
import org.lwjgl.glfw.GLFW;

/** Disposable integrated-server audit: real geometry, physical AE, UI and imports. */
@Mod("enhancedcore_audit")
public final class CloudCompatibilityRuntimeChecks {
    private static final Path ROOT = Path.of("C:/IDEA/GTL-Enhancedcore/_audit/cloud-compat-20261105").toAbsolutePath().normalize();
    private static final String WORLD = "cloud-compatibility-new-world";
    private static final int[][] SIZES = {{960, 600, 2}, {1280, 720, 3}, {640, 480, 2}};
    private final List<String> checks = Collections.synchronizedList(new ArrayList<>());
    private final List<String> failures = Collections.synchronizedList(new ArrayList<>());
    private volatile WorldFixture world;
    private final boolean restart = Boolean.getBoolean("gtl.enhancedcore.cloudCompatibilityRestartAudit");
    private final boolean dispatchLoad = Boolean.getBoolean("gtl.enhancedcore.cloudDispatchLoadAudit")
            && !Boolean.getBoolean("gtl.enhancedcore.cloudUploadLoadAudit");
    private final boolean uploadLoad = Boolean.getBoolean("gtl.enhancedcore.cloudUploadLoadAudit");
    private volatile boolean restartWorld;
    private volatile boolean worldDone, serverStopped;
    private boolean done;
    private boolean recoveryUiChecked;
    private int stage, frames, size;
    private Path directory;
    private CampusPlanScreen atlas;

    public CloudCompatibilityRuntimeChecks() {
        if (!Boolean.getBoolean("gtl.enhancedcore.cloudCompatibilityAudit")) return;
        MinecraftForge.EVENT_BUS.addListener(this::clientTick);
        MinecraftForge.EVENT_BUS.addListener(this::serverTick);
        if (dispatchLoad || uploadLoad) {
            MinecraftForge.EVENT_BUS.addListener(CloudDispatchLoadRuntimeChecks::renderBoundary);
            MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.HIGHEST,CloudDispatchLoadRuntimeChecks::startTickBoundary);
            MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.LOWEST,CloudDispatchLoadRuntimeChecks::endTickBoundary);
        }
        MinecraftForge.EVENT_BUS.addListener(this::stopped);
    }
    private void stopped(ServerStoppedEvent event) { serverStopped = true; }
    private void clientTick(TickEvent.ClientTickEvent event) {
        if (done || event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        try {
            if (stage == 0) {
                if (mc.getOverlay() != null || mc.screen == null) return;
                directory = mc.gameDirectory.toPath().toAbsolutePath().normalize();
                check(directory.startsWith(ROOT) && !directory.equals(ROOT)
                                && Files.isRegularFile(directory.resolve("cloud-compat.marker")), "marked isolated compatibility fixture");
                verifyRestartConfirmationGuards();
                if (restart) {
                    check(mc.level == null && mc.getSingleplayerServer() == null
                                    && Files.isRegularFile(directory.resolve("saves/" + WORLD + "/level.dat"))
                                    && Files.isRegularFile(directory.resolve("saves/" + WORLD + "/cloud-compat-restart.dat")),
                            "restart opens only the previously generated marked compatibility world");
                    stage = 4;
                    mc.createWorldOpenFlows().loadLevel(mc.screen, WORLD);
                    return;
                }
                check(mc.level == null && mc.getSingleplayerServer() == null
                                && !Files.exists(directory.resolve("saves/" + WORLD)), "new world path only; no player world opened");
                Files.createDirectories(directory.resolve("screenshots"));
                if (dispatchLoad || uploadLoad) {
                    mc.options.pauseOnLostFocus = false; stage = 4; createAuditWorld(mc); return;
                }
                if(Boolean.getBoolean("gtl.enhancedcore.cloudOrderRecoveryClientAudit")&&!recoveryUiChecked){
                    mc.setScreen(new TitleScreen());CloudOrderRecoveryClientChecks.begin(directory,checks);stage=7;return;
                }
                try { CloudCapacityUiChecks.run(directory, checks); }
                catch (Throwable failure) { record("native capacity UI", failure); }
                try { CloudNativeIdleRuntimeChecks.run(directory, checks); }
                catch (Throwable failure) { record("native idle routing", failure); }
                try { CloudSteamScenarios.run(directory, checks); }
                catch (Throwable failure) { record("steam native scenarios", failure); }
                resize(mc, SIZES[0]);
                var editor = new CreateWorldPlotScreen(new TitleScreen(), PlotSettings.plannedCampus(),
                        Component.literal("Compatibility Preview Audit"), Component.translatable("gui.done"), ignored -> {});
                mc.setScreen(editor); stage = 1; frames = 0;
            } else if (stage == 1) {
                if (++frames < 8) return;
                String label = Component.translatable("gtl_enhancedcore.create_world_plot.campus_map_button").getString();
                var button = mc.screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
                        .filter(widget -> widget.getMessage().getString().equals(label)).findFirst().orElseThrow();
                check(button.visible && button.active && mc.screen.mouseClicked(button.getX() + button.getWidth() / 2.0,
                                button.getY() + button.getHeight() / 2.0, 0), "production world editor preview button uses real mouse click");
                check(mc.screen instanceof CampusPlanScreen, "world editor opens actual CampusPlanScreen with null initial selection");
                atlas = (CampusPlanScreen)mc.screen; stage = 2; frames = 0;
            } else if (stage == 2) {
                if (++frames < 8) return;
                var search = (EditBox)field(CampusPlanScreen.class, "searchBox").get(atlas);
                search.setValue("");
                check(field(CampusPlanScreen.class, "selected").get(atlas) == null, "initial empty atlas query retains null selection safely size " + size);
                search.setValue("audit-no-campus-match-20261105");
                check(((List<?>)field(CampusPlanScreen.class, "searchMatches").get(atlas)).isEmpty(), "no-match atlas query is safe size " + size);
                search.setValue(""); stage = 3; frames = 0;
            } else if (stage == 3) {
                if (++frames < 8) return;
                try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                    image.writeToFile(directory.resolve("screenshots/campus-preview-" + size + ".png"));
                }
                checks.add("actual rendered campus initial/no-match/reinit at " + mc.getWindow().getGuiScaledWidth() + "x" + mc.getWindow().getGuiScaledHeight());
                if (++size < SIZES.length) {
                    resize(mc, SIZES[size]);
                    atlas = new CampusPlanScreen(null, PlotSettings.plannedCampus()); mc.setScreen(atlas);
                    stage = 2; frames = 0; return;
                }
                resize(mc, SIZES[0]); mc.setScreen(new TitleScreen()); stage = 4;
                mc.options.pauseOnLostFocus = false;
                createAuditWorld(mc);
            } else if (stage == 4) {
                if (restart && mc.level == null && mc.screen != null) {
                    if (++frames % 120 == 0) Files.writeString(directory.resolve("cloud-compat-restart-screen.json"),
                            new GsonBuilder().setPrettyPrinting().create().toJson(Map.of("screen", mc.screen.getClass().getName(),
                                    "title", Component.Serializer.toJson(mc.screen.getTitle()), "frames", frames,
                                    "widgets", mc.screen.children().stream().filter(AbstractWidget.class::isInstance)
                                            .map(AbstractWidget.class::cast).map(widget -> Component.Serializer.toJson(widget.getMessage())).toList())));
                    String continueKey = restartContinueKey(mc.screen);
                    if (frames >= 8 && continueKey != null) {
                        String text = Component.translatable(continueKey).getString();
                        var button = mc.screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
                                .filter(widget -> widget.visible && widget.active && widget.getMessage().getString().equals(text)).findFirst().orElseThrow();
                        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                            image.writeToFile(directory.resolve("screenshots/restart-generated-world-confirmation.png"));
                        }
                        check(mc.screen.mouseClicked(button.getX() + button.getWidth() / 2.0, button.getY() + button.getHeight() / 2.0, 0),
                                "restart confirms experimental status only for the fixed marked audit world through its real continue button");
                        return;
                    }
                    if (frames > 2400) throw new AssertionError("Restart world failed to open; screen=" + mc.screen.getClass().getName()
                            + " title=" + mc.screen.getTitle().getString());
                }
                if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) return;
                stage = 5; mc.setScreen(null);
                var server = mc.getSingleplayerServer();
                server.execute(() -> {
                    try {
                        if (restart) {
                            CloudRestartRuntimeChecks.begin(server.overworld(), checks);
                            restartWorld = true;
                        } else {
                            if (!dispatchLoad && !uploadLoad) {
                                try { CloudCapacityRuntimeChecks.run(directory, checks); }
                                catch (Throwable failure) { record("registered native capacity", failure); }
                            }
                            world = new WorldFixture(server.overworld(), checks);
                        }
                    }
                    catch (Throwable failure) { record("world initialization", failure); worldDone = true; }
                });
            } else if (stage == 5) {
                if (dispatchLoad || uploadLoad) CloudDispatchLoadRuntimeChecks.clientTick();
                if (!worldDone) return;
                stage = 6; mc.level.disconnect(); mc.clearLevel(); mc.setScreen(new TitleScreen());
            } else if (stage == 6) {
                if (mc.level != null || mc.getSingleplayerServer() != null || !serverStopped) return;
                checks.add("integrated server fully stopped and newly generated audit world saved"); finish(mc);
            } else if(stage==7){
                if(CloudOrderRecoveryClientChecks.step()){
                    recoveryUiChecked=true;mc.setScreen(new TitleScreen());stage=0;
                }
            }
        } catch (Throwable failure) { record("client", failure); finish(mc); }
    }
    private void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || world == null && !restartWorld || worldDone) return;
        if (dispatchLoad || uploadLoad) CloudDispatchLoadRuntimeChecks.beginAuxiliaryEndWork();
        try { worldDone = restart ? CloudRestartRuntimeChecks.step() : world.tick(); }
        catch (Throwable failure) { record("real world", failure); worldDone = true; }
        finally { if(dispatchLoad || uploadLoad) CloudDispatchLoadRuntimeChecks.endAuxiliaryEndWork(); }
    }
    private static void createAuditWorld(Minecraft mc) {
        mc.createWorldOpenFlows().createFreshLevel(WORLD,
                new LevelSettings("Cloud Compatibility Audit", GameType.CREATIVE, false, Difficulty.PEACEFUL,
                        true, new GameRules(), WorldDataConfiguration.DEFAULT),
                new WorldOptions(20261105L, false, false), registry -> registry.registryOrThrow(Registries.WORLD_PRESET)
                        .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
    }
    private static Field field(Class<?> type, String name) throws Exception { var field = type.getDeclaredField(name); field.setAccessible(true); return field; }
    private static void resize(Minecraft mc, int[] size) {
        mc.options.guiScale().set(size[2]); GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), size[0], size[1]); mc.resizeDisplay();
    }
    private void check(boolean value, String label) { if (!value) throw new AssertionError(label); checks.add(label); }
    private static String restartContinueKey(Screen screen) {
        if (!(screen.getTitle().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents title)
                || !Set.of("selectWorld.backupQuestion.experimental", "selectWorld.backupQuestion.customized").contains(title.getKey())) return null;
        if (screen.getClass() == BackupConfirmScreen.class) return "selectWorld.backupJoinSkipButton";
        return screen.getClass() == ConfirmScreen.class ? "gui.proceed" : null;
    }
    private void verifyRestartConfirmationGuards() {
        var title = Component.translatable("selectWorld.backupQuestion.experimental");
        check("selectWorld.backupJoinSkipButton".equals(restartContinueKey(new BackupConfirmScreen(null, (backup, erase) -> {},
                        title, Component.empty(), false))), "restart confirmation guard accepts only the known vanilla backup screen and its continue key");
        check("gui.proceed".equals(restartContinueKey(new ConfirmScreen(accepted -> {}, title, Component.empty()))),
                "restart confirmation guard accepts the observed Forge confirmation screen and its proceed key");
        check(restartContinueKey(new ConfirmScreen(accepted -> {}, Component.translatable("selectWorld.recreate.error.title"), Component.empty())) == null
                        && restartContinueKey(new ConfirmScreen(accepted -> {}, Component.literal("Unknown world warning"), Component.empty())) == null,
                "restart confirmation guard rejects unknown and damaged-world titles");
        check(restartContinueKey(new ConfirmScreen(accepted -> {}, title, Component.empty()) {}) == null,
                "restart confirmation guard rejects unrecognized confirmation subclasses");
    }
    private void record(String group, Throwable failure) { failures.add(group + ": " + failure); failure.printStackTrace(); }
    private void finish(Minecraft mc) {
        if(Boolean.getBoolean("gtl.enhancedcore.cloudOrderRecoveryClientAudit"))CloudOrderRecoveryClientChecks.cleanup();
        done = true; var result = new LinkedHashMap<String, Object>();
        result.put("status", failures.isEmpty() ? "PASS" : "FAIL"); result.put("checks", checks); result.put("failures", failures);
        result.put("serverStopped", serverStopped);
        result.put("scope", restart ? "Independent second JVM reloads only the marked first-phase generated world; checks original payload/status, two cloud node identities and lease IDs/slots/generations, accepted order ownership/material buckets, exact wireless automatic grid restoration, fail-closed old origin and authenticated reconnect through production UI action, actual AE provider index recovery."
                : dispatchLoad || uploadLoad ? "New isolated real two-LCR cloud network, bounded physical AE CPU storage, distinct real processing patterns; actual dispatch or central physical import profile as selected; observation-only server/render/call/stack samples and real AE-terminal block-use/menu/render/repository response. Performance boundary is recorded in selected profile JSON; load mode skips compatibility UI/topology/restart scenarios."
                : "Real new integrated-server world, production autoBuild central library and two LCRs/cloud endpoints, actual AE nodes/smart cable/provider split and merge, CraftingService provider index, generator physical staging/WAL, real wireless core chunk unload/reload and exact membership; campus creation preview and synchronized native capacity UI actual framebuffer rendering at three sizes; capacity dummy fixture reports its own boundaries.");
        result.put("jvmPid", ProcessHandle.current().pid()); result.put("restart", restart);
        result.put("notCovered", restart ? List.of("Player's existing world and AE layout", "Actual remote multiplayer claims")
                : List.of("Player's existing world and AE layout", "Independent JVM restart requires separate restart audit", "Actual remote multiplayer claims"));
        try { Files.writeString(directory.resolve(restart ? "cloud-compat-restart-checks.json" : "cloud-compat-checks.json"), new GsonBuilder().setPrettyPrinting().create().toJson(result)); }
        catch (Exception failure) { failure.printStackTrace(); }
        mc.stop();
    }

    private static final class WorldFixture {
        final ServerLevel level;
        final List<String> checks;
        final FakePlayer owner;
        final List<WorkableElectricMultiblockMachine> controllers = new ArrayList<>();
        final List<CloudPatternBufferMachine> children = new ArrayList<>();
        final List<CreativeEnergyCellBlockEntity> cells = new ArrayList<>();
        final CloudPatternLibraryMachine main;
        final CreativeEnergyCellBlockEntity mainCell;
        final long deadline;
        boolean linked;
        int stage;
        UUID entryId;

        WorldFixture(ServerLevel level, List<String> checks) throws Exception {
            this.level = level; this.checks = checks; deadline = level.getGameTime() + (Boolean.getBoolean("gtl.enhancedcore.cloudUploadLoadAudit") ? 60_000 : 7200);
            owner = FakePlayerFactory.get(level, new GameProfile(UUID.fromString("05c0c27b-2350-4d48-a0a5-54c54ea35d1e"), "CloudCompatAudit"));
            owner.setGameMode(GameType.CREATIVE);
            buildController(new BlockPos(256, 100, 256)); buildController(new BlockPos(272, 100, 256));
            var recipes = new ArrayList<net.minecraft.world.item.crafting.Recipe<?>>(level.getRecipeManager().getRecipes());
            var cloudRecipe = GTRecipeBuilder.of(new ResourceLocation("gtl_enhancedcore", "audit_cloud_compat_shared"), controllers.getFirst().getRecipeType())
                    .inputItems(Items.APPLE).outputItems(Items.GOLD_INGOT).duration(2000000000).EUt(8).buildRawRecipe();
            recipes.add(cloudRecipe); controllers.getFirst().getRecipeType().getLookup().addRecipe(cloudRecipe);
            for (int index = 0; index < 2; index++) {
                var recipe = GTRecipeBuilder.of(new ResourceLocation("gtl_enhancedcore", "audit_cloud_compat_bender_" + index), GTRecipeTypes.BENDER_RECIPES)
                        .inputItems(index == 0 ? Items.CARROT : Items.POTATO).outputItems(index == 0 ? Items.IRON_NUGGET : Items.GOLD_NUGGET)
                        .duration(20).EUt(8).buildRawRecipe(); recipes.add(recipe); GTRecipeTypes.BENDER_RECIPES.getLookup().addRecipe(recipe);
            }
            level.getRecipeManager().replaceRecipes(recipes);
            CloudCpuCompletionRuntimeChecks.prepareRecipes(level, controllers.getFirst());
            if(Boolean.getBoolean("gtl.enhancedcore.cloudFrozenCpuRecoveryAudit"))
                CloudFrozenCpuRecoveryRuntimeChecks.prepareRecipes(level,controllers.getFirst());
            if (Boolean.getBoolean("gtl.enhancedcore.cloudDispatchLoadAudit") && !Boolean.getBoolean("gtl.enhancedcore.cloudUploadLoadAudit"))
                CloudDispatchLoadRuntimeChecks.prepareRecipes(level, controllers.getFirst());
            if (Boolean.getBoolean("gtl.enhancedcore.cloudUploadLoadAudit"))
                CloudBatchImportLoadRuntimeChecks.prepareRecipes(level, controllers.getFirst());
            var mainPos = new BlockPos(336, 100, 256);
            for (int x = (mainPos.getX() - 32) >> 4; x <= (mainPos.getX() + 32) >> 4; x++)
                for (int z = (mainPos.getZ() - 48) >> 4; z <= (mainPos.getZ() + 48) >> 4; z++) level.setChunkForced(x, z, true);
            var mainDefinition = GTRegistries.MACHINES.get(new ResourceLocation("gtl_enhancedcore", "cloud_pattern_library"));
            level.setBlockAndUpdate(mainPos, mainDefinition.defaultBlockState()); main = (CloudPatternLibraryMachine)MetaMachine.getMachine(level, mainPos);
            main.setFrontFacing(Direction.NORTH); main.onMachinePlaced(owner, ItemStack.EMPTY); main.getPattern().autoBuild(owner, main.getMultiblockState());
            if (!level.isEmptyBlock(mainPos.north())) throw new AssertionError("Main front power cell is not exterior air");
            level.setBlockAndUpdate(mainPos.north(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
            mainCell = (CreativeEnergyCellBlockEntity)level.getBlockEntity(mainPos.north());
            if (!level.isEmptyBlock(mainPos.north().east()) || !level.isEmptyBlock(mainPos.north().east(2)))
                throw new AssertionError("Physical AE CPU positions are not exterior air");
            level.setBlockAndUpdate(mainPos.north().east(), AEBlocks.CRAFTING_STORAGE_64K.block().defaultBlockState());
            level.setBlockAndUpdate(mainPos.north().east(2), AEBlocks.CRAFTING_ACCELERATOR.block().defaultBlockState());
        }
        private void buildController(BlockPos origin) {
            for (int x = (origin.getX() - 8) >> 4; x <= (origin.getX() + 8) >> 4; x++)
                for (int z = (origin.getZ() - 8) >> 4; z <= (origin.getZ() + 8) >> 4; z++) level.setChunkForced(x, z, true);
            var definition = (MultiblockMachineDefinition)GTRegistries.MACHINES.get(new ResourceLocation("gtceu", "large_chemical_reactor"));
            level.setBlockAndUpdate(origin, definition.defaultBlockState());
            var machine = (WorkableElectricMultiblockMachine)MetaMachine.getMachine(level, origin);
            machine.setFrontFacing(Direction.NORTH); machine.getPattern().autoBuild(owner, machine.getMultiblockState());
            var casing = com.gregtechceu.gtceu.common.data.GTBlocks.CASING_PTFE_INERT.get().defaultBlockState();
            var cloud = GTRegistries.MACHINES.get(new ResourceLocation("gtl_enhancedcore", "cloud_pattern_buffer"));
            var energy = GTRegistries.MACHINES.get(new ResourceLocation("gtceu", "ev_energy_input_hatch"));
            BlockPos childPos = null; boolean powered = false;
            for (var cursor : BlockPos.betweenClosed(origin.offset(-3, -3, -3), origin.offset(3, 3, 3))) {
                BlockPos pos = cursor.immutable(); var block = level.getBlockState(pos).getBlock();
                if (IvNativeHatches.materials(block) || IvBufferRegistry.contains(net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(block))) {
                    if (childPos == null && level.isEmptyBlock(pos.north()) && level.isEmptyBlock(pos.north(2))) {
                        childPos = pos; level.setBlockAndUpdate(pos, cloud.defaultBlockState());
                    } else level.setBlockAndUpdate(pos, casing);
                } else if (PartAbility.INPUT_ENERGY.isApplicable(block)) { level.setBlockAndUpdate(pos, energy.defaultBlockState()); powered = true; }
            }
            for (var cursor : BlockPos.betweenClosed(origin.offset(-2, -2, -2), origin.offset(2, 2, 2))) {
                BlockPos pos = cursor.immutable();
                if (!level.getBlockState(pos).is(casing.getBlock())) continue;
                if (childPos == null && level.isEmptyBlock(pos.north()) && level.isEmptyBlock(pos.north(2))) {
                    childPos = pos; level.setBlockAndUpdate(pos, cloud.defaultBlockState());
                } else if (!powered) { level.setBlockAndUpdate(pos, energy.defaultBlockState()); powered = true; }
                if (childPos != null && powered) break;
            }
            for (var pos : BlockPos.betweenClosed(origin.offset(-3, -3, -3), origin.offset(3, 3, 3)))
                if (MetaMachine.getMachine(level, pos) instanceof MaintenanceHatchPartMachine maintenance) maintenance.setMaintenanceProblems((byte)0);
            if (childPos == null || !powered) throw new AssertionError("Real LCR lacks cloud/energy candidates");
            var child = (CloudPatternBufferMachine)MetaMachine.getMachine(level, childPos);
            child.setFrontFacing(Direction.NORTH); child.onMachinePlaced(owner, ItemStack.EMPTY);
            level.setBlockAndUpdate(childPos.north(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
            var cell = (CreativeEnergyCellBlockEntity)level.getBlockEntity(childPos.north());
            level.setBlockAndUpdate(childPos.north(2), AEBlocks.DRIVE.block().defaultBlockState());
            ((DriveBlockEntity)level.getBlockEntity(childPos.north(2))).getInternalInventory().setItemDirect(0, AEItems.ITEM_CELL_1K.stack());
            controllers.add(machine); children.add(child); cells.add(cell);
        }
        boolean tick() throws Exception {
            if (level.getGameTime() > deadline) throw new AssertionError("Compatibility world timeout stage=" + stage + " main=" + main.isFormed()
                    + " mainError=" + main.getMultiblockState().error + " children=" + children.stream().map(c -> c.cloudReadyFailure(true)).toList());
            for (var machine : controllers) if ((stage != 11 || CloudCpuCompletionRuntimeChecks.wantOuterPower())
                    &&(stage!=14||CloudFrozenCpuRecoveryRuntimeChecks.wantOuterPower())
                    && machine.isFormed() && machine.getEnergyContainer() != null)
                machine.getEnergyContainer().addEnergy(machine.getEnergyContainer().getEnergyCapacity());
            if (stage == 0) {
                if (!main.isFormed() || controllers.stream().anyMatch(c -> !c.isFormed())
                        || mainCell.getMainNode().getNode() == null || cells.stream().anyMatch(c -> c.getMainNode().getNode() == null)) return false;
                if (!linked) {
                    for (var cell : cells) GridHelper.createConnection(mainCell.getMainNode().getNode(), cell.getMainNode().getNode()); linked = true;
                }
                if (children.stream().anyMatch(c -> !c.cloudReady(true))) return false;
                if (!children.get(0).cloudMode().equals(children.get(1).cloudMode())) throw new AssertionError("Same real LCRs must claim same recipe mode");
                checks.add("two production LCRs and central library form without synthetic formed/parts flags; both cloud endpoints claim same mode");
                if (Boolean.getBoolean("gtl.enhancedcore.cloudDispatchLoadAudit") && !Boolean.getBoolean("gtl.enhancedcore.cloudUploadLoadAudit")) {
                    CloudDispatchLoadRuntimeChecks.begin(level, main, children, owner, checks); stage = 20; return false;
                }
                if (Boolean.getBoolean("gtl.enhancedcore.cloudUploadLoadAudit")) {
                    CloudBatchImportLoadRuntimeChecks.begin(level, main, children, owner, checks); stage = 21; return false;
                }
                if (Boolean.getBoolean("gtl.enhancedcore.cloudWirelessTicketProbe")) {
                    var pattern = PatternDetailsHelper.encodeProcessingPattern(new GenericStack[]{new GenericStack(AEItemKey.of(Items.APPLE), 1)},
                            new GenericStack[]{new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 1)});
                    PatternQuickUploadMetadata.writeRecipeTypeId(pattern, children.getFirst().cloudMode());
                    var operation = UUID.randomUUID(); var store = CloudPatternStore.get(level);
                    entryId = store.upload(main.libraryId(), owner.getUUID(), pattern, new CompoundTag(), operation).id();
                    store.acknowledge(main.libraryId(), owner.getUUID(), operation); stage = 3; return false;
                }
                stage = 1;
            } else if (stage == 20) {
                return CloudDispatchLoadRuntimeChecks.step();
            } else if (stage == 21) {
                return CloudBatchImportLoadRuntimeChecks.step();
            } else if (stage == 1) {
                CloudGeneratorImportRuntimeChecks.begin(level, main, owner, checks); stage = 10;
            } else if (stage == 10) {
                if (!CloudGeneratorImportRuntimeChecks.step()) return false;
                if(Boolean.getBoolean("gtl.enhancedcore.cloudInboxPreparedAudit")) {
                    CloudInboxPreparedRuntimeChecks.begin(level,main,owner,checks);stage=13;return false;
                }
                // Complete the short ordinary/scaled CPU scenarios before seeding the long restart order.
                CloudCpuCompletionRuntimeChecks.begin(level, main, children, owner, checks); stage = 11;
            } else if(stage==13) {
                if(!CloudInboxPreparedRuntimeChecks.step())return false;
                CloudCpuCompletionRuntimeChecks.begin(level,main,children,owner,checks);stage=11;
            } else if (stage == 11) {
                if (!CloudCpuCompletionRuntimeChecks.step()) return false;
                if(Boolean.getBoolean("gtl.enhancedcore.cloudFrozenCpuRecoveryAudit")){
                    CloudFrozenCpuRecoveryRuntimeChecks.begin(level,main,children,owner,checks);stage=14;return false;
                }
                stage=15;
            } else if(stage==14){
                if(!CloudFrozenCpuRecoveryRuntimeChecks.step())return false;
                children.set(0,CloudFrozenCpuRecoveryRuntimeChecks.replacementSource());stage=15;
            } else if(stage==15){
                var pattern = PatternDetailsHelper.encodeProcessingPattern(new GenericStack[]{new GenericStack(AEItemKey.of(Items.APPLE), 1)},
                        new GenericStack[]{new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 1)});
                PatternQuickUploadMetadata.writeRecipeTypeId(pattern, children.getFirst().cloudMode());
                var operation = UUID.randomUUID(); var store = CloudPatternStore.get(level);
                entryId = store.upload(main.libraryId(), owner.getUUID(), pattern, new CompoundTag(), operation).id();
                store.acknowledge(main.libraryId(), owner.getUUID(), operation); stage = 12;
            } else if (stage == 12) {
                if (children.stream().anyMatch(child -> CloudTopologyRuntimeChecks.publishedPattern(child, entryId).isEmpty())) return false;
                CloudTopologyRuntimeChecks.begin(level, main, children.getFirst(), children.get(1), entryId, owner, checks); stage = 2;
            } else if (stage == 2) {
                if (!CloudTopologyRuntimeChecks.step()) return false;
                var operation = UUID.randomUUID(); var store = CloudPatternStore.get(level);
                var original = CloudTopologyRuntimeChecks.takeExportedOriginal();
                if (original.isEmpty()) throw new AssertionError("Exported physical original is missing before restart preparation");
                entryId = store.upload(main.libraryId(), owner.getUUID(), original, new CompoundTag(), operation).id();
                store.acknowledge(main.libraryId(), owner.getUUID(), operation); stage = 3;
            } else if (stage == 3) {
                if (children.stream().anyMatch(child -> child.cloudNode().leases().stream().noneMatch(lease -> entryId.equals(lease.entryId())))) return false;
                CloudWirelessRuntimeChecks.begin(level, main, children, owner, checks); stage = 4;
            } else if (stage == 4) {
                if (!CloudWirelessRuntimeChecks.step()) return false;
                if (Boolean.getBoolean("gtl.enhancedcore.cloudWirelessTicketProbe")) return true;
                CloudRestartRuntimeChecks.prepare(level, main, children, owner, entryId, CloudWirelessRuntimeChecks.frequency(), checks);
                checks.add("actual central originals and both endpoint authorities checkpoint to new world");
                return true;
            }
            return false;
        }
    }
}
