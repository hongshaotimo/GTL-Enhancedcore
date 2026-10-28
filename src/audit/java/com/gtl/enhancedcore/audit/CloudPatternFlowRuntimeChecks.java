package com.gtl.enhancedcore.audit;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import com.google.gson.GsonBuilder;
import com.gregtechceu.gtceu.api.machine.*;
import com.gregtechceu.gtceu.api.machine.multiblock.*;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder;
import com.gtl.enhancedcore.common.cloud.*;
import com.gtl.enhancedcore.common.cloud.store.*;
import com.gtl.enhancedcore.common.recipe.iv.*;
import com.lowdragmc.lowdraglib.gui.modular.ModularUIContainer;
import com.mojang.authlib.GameProfile;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.util.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.gtlcore.gtlcore.integration.ae2.pattern.PatternQuickUploadMetadata;

/** Production geometry, real AE grid and real IV executor. Never changes the pattern factory or formed flag. */
@Mod.EventBusSubscriber(modid = "enhancedcore_audit")
public final class CloudPatternFlowRuntimeChecks {
    private static final List<String> CHECKS = new ArrayList<>();
    private static final BlockPos CORE = new BlockPos(256, 100, 256);
    private static ServerLevel level;
    private static FakePlayer player;
    private static WorkableElectricMultiblockMachine controller;
    private static CloudPatternBufferMachine child;
    private static CloudPatternLibraryMachine main;
    private static appeng.blockentity.networking.CreativeEnergyCellBlockEntity mainGridBridge;
    private static appeng.blockentity.networking.WirelessAccessPointBlockEntity passiveAccess;
    private static ItemStack passiveTerminal;
    private static appeng.api.networking.IGrid acceptedFlowGrid;
    private static appeng.api.networking.IGridNode acceptedSourceNode, acceptedDriveNode, acceptedBridgeNode;
    private static final List<String> GRID_TRACE = new ArrayList<>();
    private static int removedAt;
    private static int dispatchedAt;
    private static com.gregtechceu.gtceu.common.machine.multiblock.part.MaintenanceHatchPartMachine genericMaintenance;
    private static com.gregtechceu.gtceu.api.recipe.GTRecipe auditRecipe;
    private static final BlockPos MAIN = CORE.east(64);
    private static BlockPos brokenShell;
    private static net.minecraft.world.level.block.state.BlockState brokenShellState;
    private static ItemStack mainUpload;
    private static int mainEntriesBefore;
    private static BlockPos childPos, powerPos;
    private static UUID entryId, frozenId;
    private static UUID validatedLeaseId;
    private static final List<CloudPatternBufferMachine> peers = new ArrayList<>();
    private static ResourceLocation firstMode, thirdMode, gapMode;
    private static BlockPos gapPos;
    private static CloudPatternBufferMachine replacement;
    private static String stage = "inactive";
    private static long started;
    private static int ticks;
    private static int pausedAt;
    private static boolean nativeFlow;
    private static boolean genericFlow, mainGridLinked, networkImportFlow;
    private static String phase;
    private static final AEItemKey INPUT = AEItemKey.of(Items.APPLE);
    private static final AEItemKey OUTPUT = AEItemKey.of(Items.GOLD_INGOT);
    public static void begin(ServerLevel world) throws Exception {
        level = world; started = System.nanoTime(); stage = "build";
        phase = System.getProperty("gtl.cloudStoreAuditPhase", "flow"); nativeFlow = phase.equals("native");
        networkImportFlow = phase.equals("network_import") || Boolean.getBoolean("gtl.cloudNetworkImportAudit");
        genericFlow = phase.equals("generic") || phase.equals("long_dispatch") || phase.equals("authority_flow") || Boolean.getBoolean("gtl.cloudPassiveRefreshAudit") || networkImportFlow;
        player = FakePlayerFactory.get(level, new GameProfile(UUID.fromString("4c982882-ceda-462d-ad4c-ef7c8612c580"), "CloudFlowAudit"));
        player.setGameMode(GameType.CREATIVE); player.setPos(256, 100, 250);
        for (int x = 13; x <= 19; x++) for (int z = 13; z <= 19; z++) level.setChunkForced(x, z, true);
        for (int x = 19; x <= (networkImportFlow ? 22 : 21); x++) for (int z = 15; z <= 17; z++) level.setChunkForced(x, z, true);
        var definition = (MultiblockMachineDefinition)GTRegistries.MACHINES.get(new ResourceLocation(genericFlow ? "gtceu:large_chemical_reactor" : nativeFlow ? "gtceu:gravitation_shockburst" : "gtl_enhancedcore:plasma_machine_tool"));
        level.setBlock(CORE, definition.defaultBlockState(), 3);
        controller = (WorkableElectricMultiblockMachine)MetaMachine.getMachine(level, CORE);
        controller.setFrontFacing(Direction.NORTH);
        controller.getPattern().autoBuild(player, controller.getMultiblockState());
        var casing = genericFlow ? com.gregtechceu.gtceu.common.data.GTBlocks.CASING_PTFE_INERT.get().defaultBlockState()
                : ForgeRegistries.BLOCKS.getValue(new ResourceLocation(nativeFlow ? "gtlcore:create_casing" : "gtceu:robust_machine_casing")).defaultBlockState();
        var cloud = GTRegistries.MACHINES.get(new ResourceLocation("gtl_enhancedcore", "cloud_pattern_buffer"));
        var energy = GTRegistries.MACHINES.get(new ResourceLocation(nativeFlow ? "gtceu:zpm_256a_laser_target_hatch" : genericFlow ? "gtceu:ev_energy_input_hatch" : "gtceu:iv_energy_input_hatch"));
        // Replace only the auto-builder's actual material/energy candidates. Geometry is untouched.
        int materials = 0, energies = 0;
        for (BlockPos pos : BlockPos.betweenClosed(CORE.offset(-24, -16, -24), CORE.offset(24, 16, 24))) {
            var block = level.getBlockState(pos).getBlock();
            if (IvNativeHatches.materials(block) || IvBufferRegistry.contains(ForgeRegistries.BLOCKS.getKey(block))) {
                if (childPos == null) { childPos = pos.immutable(); level.setBlock(pos, cloud.defaultBlockState(), 3); }
                else level.setBlock(pos, casing, 3);
                materials++;
            } else if (PartAbility.INPUT_ENERGY.isApplicable(block) || nativeFlow && PartAbility.INPUT_LASER.isApplicable(block)) { level.setBlock(pos, energy.defaultBlockState(), 3); energies++; }
        }
        if (nativeFlow) {
            for (BlockPos pos : BlockPos.betweenClosed(CORE.offset(-12, -12, -12), CORE.offset(12, 12, 12))) {
                if (!level.getBlockState(pos).is(casing.getBlock())) continue;
                if (childPos == null && level.isEmptyBlock(pos.north()) && level.isEmptyBlock(pos.north(2))) {
                    childPos = pos.immutable(); level.setBlock(pos, cloud.defaultBlockState(), 3); materials++;
                } else if (energies == 0) { level.setBlock(pos, energy.defaultBlockState(), 3); energies++; }
            }
        }
        if (genericFlow) {
            // Upstream LCR auto-build previews can choose plain X casings for optional abilities.
            // Only replace its actual PTFE X/C candidates; retain the pipe and exact CuNi coil.
            for (BlockPos pos : BlockPos.betweenClosed(CORE.offset(-2, -2, -2), CORE.offset(2, 2, 2))) {
                if (!level.getBlockState(pos).is(casing.getBlock())) continue;
                if (childPos == null && level.isEmptyBlock(pos.north()) && level.isEmptyBlock(pos.north(2))) {
                    childPos = pos.immutable(); level.setBlock(pos, cloud.defaultBlockState(), 3); materials++;
                } else if (energies == 0) { level.setBlock(pos, energy.defaultBlockState(), 3); energies++; }
                if (childPos != null && energies > 0) break;
            }
            var maintenanceDefinition = GTRegistries.MACHINES.get(new ResourceLocation("gtceu", "maintenance_hatch"));
            for (BlockPos pos : BlockPos.betweenClosed(CORE.offset(-2, -2, -2), CORE.offset(2, 2, 2))) {
                if (!PartAbility.MAINTENANCE.isApplicable(level.getBlockState(pos).getBlock())) continue;
                level.setBlock(pos, maintenanceDefinition.defaultBlockState(), 3);
                genericMaintenance = (com.gregtechceu.gtceu.common.machine.multiblock.part.MaintenanceHatchPartMachine)MetaMachine.getMachine(level, pos);
                break;
            }
            if (genericMaintenance == null) throw new AssertionError("LCR auto-builder did not expose its actual maintenance ability candidate");
            genericMaintenance.setMaintenanceProblems((byte)0);
        }
        require(materials > 0 && energies > 0, "Production auto-builder placed real material and energy candidates");
        child = (CloudPatternBufferMachine)MetaMachine.getMachine(level, childPos);
        child.setFrontFacing(Direction.NORTH); child.onMachinePlaced(player, ItemStack.EMPTY);
        powerPos = childPos.north();
        require(level.isEmptyBlock(powerPos) && level.isEmptyBlock(powerPos.north()), "AE cell and drive occupy air outside production geometry");
        level.setBlock(powerPos, AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState(), 3);
        level.setBlock(powerPos.north(), AEBlocks.DRIVE.block().defaultBlockState(), 3);
        ((DriveBlockEntity)level.getBlockEntity(powerPos.north())).getInternalInventory().setItemDirect(0, AEItems.ITEM_CELL_1K.stack());
        var builder = GTRecipeBuilder.of(new ResourceLocation("gtl_enhancedcore", "audit_cloud_" + phase), controller.getDefinition().getRecipeTypes()[0])
                .inputItems(new ItemStack(Items.APPLE)).outputItems(new ItemStack(Items.GOLD_INGOT)).duration(nativeFlow ? 200000000 : genericFlow ? 20000 : 2000).EUt(8);
        if (nativeFlow) {
            builder.notConsumable(Items.DIAMOND);
        }
        var recipe = builder.buildRawRecipe(); auditRecipe = recipe;
        var recipes = new ArrayList<net.minecraft.world.item.crafting.Recipe<?>>(level.getRecipeManager().getRecipes()); recipes.add(recipe);
        level.getRecipeManager().replaceRecipes(recipes);
        level.setChunkForced(MAIN.getX() >> 4, MAIN.getZ() >> 4, true);
        var mainDefinition = (MultiblockMachineDefinition)GTRegistries.MACHINES.get(new ResourceLocation("gtl_enhancedcore", "cloud_pattern_library"));
        level.setBlock(MAIN, mainDefinition.defaultBlockState(), 3); main = (CloudPatternLibraryMachine)MetaMachine.getMachine(level, MAIN);
        main.setFrontFacing(Direction.NORTH); main.onMachinePlaced(player, ItemStack.EMPTY);
        if (nativeFlow) {
            var library = CloudPatternStore.get(level).library(main.libraryId()); var catalystState = library.catalysts();
            var inventory = CloudLibraryService.itemContents(catalystState); inventory.setStackInSlot(0, new ItemStack(Items.DIAMOND));
            catalystState.put("items", inventory.serializeNBT()); library.setCatalysts(player.getUUID(), catalystState);
        }
        main.getPattern().autoBuild(player, main.getMultiblockState());
        require(level.isEmptyBlock(MAIN.north()), "Main controller front has an exterior position for a real physical AE node");
        level.setBlock(MAIN.north(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState(), 3);
        mainGridBridge = (appeng.blockentity.networking.CreativeEnergyCellBlockEntity)level.getBlockEntity(MAIN.north());
        if (Boolean.getBoolean("gtl.cloudPassiveRefreshAudit")) {
            BlockPos accessPos = MAIN.north(2);
            require(level.isEmptyBlock(accessPos), "Passive native access point occupies fixture-owned exterior air");
            level.setBlock(accessPos, AEBlocks.WIRELESS_ACCESS_POINT.block().defaultBlockState(), 3);
            passiveAccess = (appeng.blockentity.networking.WirelessAccessPointBlockEntity)level.getBlockEntity(accessPos);
            require(accessPos.relative(passiveAccess.getOrientation().getSide(appeng.api.orientation.RelativeSide.BACK)).equals(MAIN.north()),
                    "Passive real wireless access point physically connects to its actual powered AE cell");
            passiveAccess.getMainNode().setOwningPlayer(player);
            passiveAccess.getInternalInventory().setItemDirect(0, AEItems.WIRELESS_BOOSTER.stack(64));
            passiveTerminal = AEItems.WIRELESS_TERMINAL.stack();
            var terminalItem = (appeng.items.tools.powered.WirelessTerminalItem)passiveTerminal.getItem();
            appeng.items.tools.powered.WirelessTerminalItem.LINKABLE_HANDLER.link(passiveTerminal, GlobalPos.of(level.dimension(), accessPos));
            terminalItem.injectAEPower(passiveTerminal, terminalItem.getAEMaxPower(passiveTerminal), Actionable.MODULATE);
        }
        stage = "main_formed";
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || level == null || stage.equals("done")) return;
        try {
            ticks++;
            if (ticks % 200 == 0) com.gtl.enhancedcore.GTLEnhancedcore.LOGGER.info(
                    "[CLOUD_FLOW] stage={} tick={} formed={} mode={} AEonline={} AEactive={} jobs={} reason={} mainFormed={} childStatus={} childNode={} actualController={} actualPart={} attached={} ownerMatches={} runtimeAcl={}",
                    stage, ticks, controller.isFormed(), child.cloudMode(), child.getMainNode().isOnline(),
                    child.getMainNode().isActive(), child.iv$getState().jobs.size(), child.iv$getState().message,
                    main.isFormed(), child.cloudStatusLabel(), child.cloudNodeId(), IvBuffers.controller(child) != null, controller.getParts().contains(child),
                    child.cloudNode() != null, child.cloudNode() != null && child.cloudNode().owner().equals(player.getUUID()),
                    child.cloudNode() != null && CloudLibraryService.runtimeAuthorized(child.cloudNode().owner(), child.cloudLibraryId(), level, childPos));
            if (ticks > 2400) throw new AssertionError("Flow timeout stage=" + stage + " formed=" + controller.isFormed() + " patternError=" + controller.getMultiblockState().error + " child=" + child.iv$getState().message);
            if (genericFlow && ticks % 200 == 0 && controller.getRecipeLogic() instanceof IvNativeAccess access)
                com.gtl.enhancedcore.GTLEnhancedcore.LOGGER.info("[CLOUD_GENERIC] tier={} voltage={} summary={} jobs={}",
                        controller.getTier(), controller.getOverclockVoltage(), access.iv$summary(), child.iv$getState().save());
            if (controller.isFormed() && controller.getEnergyContainer() != null) controller.getEnergyContainer().addEnergy(controller.getEnergyContainer().getEnergyCapacity());
            switch (stage) {
                case "main_formed" -> {
                    if (!main.isFormed() || !controller.isFormed()) return;
                    if (Boolean.getBoolean("gtl.cloudPassiveRefreshAudit") && main.libraryId() == null) {
                        if (!passiveAccess.isActive()) return;
                        player.setPos(MAIN.getX() + .5, MAIN.getY() + .5, MAIN.getZ() - 1);
                        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, passiveTerminal);
                        var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(MAIN), Direction.NORTH, MAIN, false);
                        require(main.onUse(main.getBlockState(), level, MAIN, player, net.minecraft.world.InteractionHand.MAIN_HAND, hit)
                                        == net.minecraft.world.InteractionResult.SUCCESS && main.libraryId() != null
                                        && main.serviceGrid() == passiveAccess.getGrid(),
                                "Passive main binds its real powered AE network through actual linked charged terminal onUse");
                        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    }
                    if (!mainGridLinked) {
                        var sourceCell = (appeng.blockentity.networking.CreativeEnergyCellBlockEntity)level.getBlockEntity(powerPos);
                        if (mainGridBridge.getMainNode().getNode() == null || sourceCell.getMainNode().getNode() == null) return;
                        require(child.cloudLibraryId() == null, "A new child does not invent a personal library before discovering a real AE main library");
                        // Persistent AE infrastructure must remain connected when the replaceable child is removed.
                        appeng.api.networking.GridHelper.createConnection(mainGridBridge.getMainNode().getNode(), sourceCell.getMainNode().getNode());
                        mainGridLinked = true;
                    }
                    if (!child.cloudReady(true)) return;
                    if (phase.equals("long_dispatch")) { uploadPattern(); return; }
                    require(main.getMainNode().isActive() && main.getMainNode().getGrid() == child.getGrid(),
                            "Main controller physically connects to its adjacent AE energy-cell node; only the persistent remote energy-cell bridge uses GridHelper");
                    require(main.libraryId().equals(child.cloudLibraryId()), "Actual production 3x3x3 library forms and resolves the child's world library");
                    if (networkImportFlow) {
                        CloudNetworkImportRuntimeChecks.begin(level, main, player, CHECKS);
                        stage = "network_import"; break;
                    }
                    if (Boolean.getBoolean("gtl.cloudPassiveRefreshAudit")) {
                        CloudPatternPassiveRefreshRuntimeChecks.begin(level, main, child, player, CHECKS, () -> connectToMain(child));
                        stage = "passive_refresh"; break;
                    }
                    if (!nativeFlow && !genericFlow) CloudOriginContinuityRuntimeChecks.run(level, CHECKS);
                    mainUpload = PatternDetailsHelper.encodeProcessingPattern(new GenericStack[]{new GenericStack(INPUT, 1)}, new GenericStack[]{new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1)});
                    main.importInventory().setStackInSlot(0, mainUpload.copy());
                    mainEntriesBefore = child.cloudStore().library(child.cloudLibraryId()).entryCount();
                    brokenShell = main.getMultiblockState().getCache().stream().filter(pos -> !pos.equals(MAIN) && !level.isEmptyBlock(pos)).findFirst().orElseThrow().immutable();
                    brokenShellState = level.getBlockState(brokenShell); level.removeBlock(brokenShell, false); stage = "main_broken";
                }
                case "passive_refresh" -> {
                    if (CloudPatternPassiveRefreshRuntimeChecks.step()) finish(null);
                }
                case "network_import" -> {
                    if (CloudNetworkImportRuntimeChecks.step()) finish(null);
                }
                case "main_broken" -> {
                    if (main.isFormed()) return;
                    player.setPos(MAIN.getX() + .5, MAIN.getY() + .5, MAIN.getZ() - 1);
                    player.containerMenu = new ModularUIContainer(main.createUI(player), 70);
                    var view = main.cloudUiSnapshot(player, "", "", 0, "");
                    require(view.getString("status").equals("gtl_enhancedcore.cloud.status.unformed") && view.getString("library").equals(main.libraryId().toString()),
                            "Breaking one actual library shell preserves authorized read-only browsing");
                    main.cloudUiAction(player, "import", new CompoundTag());
                    require(ItemStack.matches(mainUpload, main.importInventory().getStackInSlot(0)) && child.cloudStore().library(child.cloudLibraryId()).entryCount() == mainEntriesBefore,
                            "Unformed main library rejects real import action without losing its physical inbox original");
                    require(child.cloudReady(true), "Breaking main structure does not stop an independently formed cloud child or load a master chunk");
                    CloudLibraryMenuRuntimeChecks.unformed(level, main, player, CHECKS);
                    level.setBlock(brokenShell, brokenShellState, 3); stage = "main_reformed";
                }
                case "main_reformed" -> {
                    if (!main.isFormed()) return;
                    player.containerMenu = new ModularUIContainer(main.createUI(player), 70); main.cloudUiAction(player, "import", new CompoundTag());
                    require(main.importInventory().getStackInSlot(0).isEmpty() && child.cloudStore().library(child.cloudLibraryId()).entryCount() == mainEntriesBefore + 1,
                            "Restoring actual main casing re-enables physical import and transfers the original exactly once");
                    CloudLibraryMenuRuntimeChecks.run(level, main, player, CHECKS); stage = "formed";
                    var terminalPattern = PatternDetailsHelper.encodeProcessingPattern(new GenericStack[]{new GenericStack(INPUT, 1)}, new GenericStack[]{new GenericStack(OUTPUT, 1)});
                    CloudTerminalRuntimeChecks.run(level, main, player, terminalPattern, CHECKS);
                }
                case "formed" -> {
                    if (!controller.isFormed() || !child.cloudReady(true)) return;
                    require(controller.getParts().contains(child) && IvBuffers.controller(child) == controller, "Real production pattern forms with cloud child in actual controller parts");
                    require(controller.getParts().stream().noneMatch(part -> PartAbility.EXPORT_ITEMS.isApplicable(part.self().getBlockState().getBlock())
                                    || PartAbility.EXPORT_FLUIDS.isApplicable(part.self().getBlockState().getBlock())),
                            "Actual isolated structure forms with input-role cloud buffer and no external item or fluid output hatch");
                    require(child.getMainNode().isActive() && child.getGrid() != null, "Physical AE creative cell and drive activate the real cloud grid");
                    if (genericFlow) require(!IvMachineScope.nativeTarget(controller) && IvBuffers.genericCloudTarget(controller)
                                    && IvMachineScope.threads(controller) == 1,
                            "A real non-whitelisted ordinary reactor uses only its native single thread rather than the IV +128 grant");
                    if (genericFlow) {
                        require(controller.getParts().contains(genericMaintenance) && genericMaintenance.getNumMaintenanceProblems() == 6,
                                "Actual ordinary LCR maintenance hatch begins with all six native maintenance problems");
                        var context = new IvNativeMatchContext(controller, Map.of(INPUT, 1L), Map.of(), 1, 1);
                        require(context.prepare(auditRecipe) == null,
                                "Unrepaired actual native maintenance rejects the cloud order's real recipe modifier");
                        genericMaintenance.fixAllMaintenanceProblems();
                        require(genericMaintenance.getNumMaintenanceProblems() == 0 && context.prepare(auditRecipe) != null,
                                "Calling the original hatch repair API clears all six problems and admits the same unmodified cloud recipe");
                    }
                    if (nativeFlow || genericFlow) { uploadPattern(); return; }
                    firstMode = child.cloudMode();
                    for (BlockPos candidate : BlockPos.betweenClosed(childPos.offset(-7, -4, 0), childPos.offset(7, 4, 0))) {
                        if (peers.size() == 2) break;
                        if (!level.getBlockState(candidate).is(ForgeRegistries.BLOCKS.getValue(new ResourceLocation("gtceu", "robust_machine_casing")))) continue;
                        peers.add(placeChild(candidate.immutable()));
                    }
                    require(peers.size() == 2, "Production front material face provides two additional real cloud positions"); stage = "modes";
                }
                case "modes" -> {
                    peers.forEach(CloudPatternFlowRuntimeChecks::connectToMain);
                    if (!controller.isFormed() || peers.stream().anyMatch(p -> p.cloudMode() == null)) return;
                    var ordered = peers.stream().sorted(Comparator.comparingLong(p -> p.getPos().asLong())).toList();
                    var types = controller.getDefinition().getRecipeTypes();
                    require(child.cloudMode().equals(types[0].registryName) && ordered.get(0).cloudMode().equals(types[1].registryName)
                            && ordered.get(1).cloudMode().equals(types[2].registryName), "Three real child endpoints claim definition modes in stable placement order");
                    var middle = ordered.get(0); thirdMode = ordered.get(1).cloudMode(); gapMode = middle.cloudMode(); gapPos = middle.getPos();
                    middle.onMachineRemoved(); level.removeBlock(gapPos, false); replacement = placeChild(gapPos); stage = "mode_gap";
                }
                case "mode_gap" -> {
                    connectToMain(replacement);
                    if (!controller.isFormed() || replacement.cloudMode() == null || !child.cloudReady(true)) return;
                    var survivor = peers.stream().filter(p -> !p.getPos().equals(gapPos)).findFirst().orElseThrow();
                    require(child.cloudMode().equals(firstMode) && survivor.cloudMode().equals(thirdMode) && replacement.cloudMode().equals(gapMode),
                            "Removing the middle real child preserves surviving modes and a new child fills only the released gap");
                    uploadPattern();
                }
                case "paused" -> {
                    if (ticks - pausedAt < 100) return;
                    require(child.cloudStore().library(child.cloudLibraryId()).entry(entryId).lease() == null,
                            "Uploading while paused does not acquire a new original lease during actual allocator ticks");
                    action("resume", new CompoundTag()); require(child.iv$getState().accepting, "Actual authorized resume action reopens admission"); stage = "lease";
                }
                case "lease" -> {
                    if (child.cloudStore().library(child.cloudLibraryId()).entry(entryId).lease() == null || child.getAvailablePatterns().isEmpty()) return;
                    require(child.cloudStore().library(child.cloudLibraryId()).entry(entryId).lease().nodeId().equals(child.cloudNodeId()), "Bounded automatic classifier exclusively leases uploaded original to the real matching controller");
                    validatedLeaseId = child.cloudStore().library(child.cloudLibraryId()).entry(entryId).lease().id();
                    child.cloudRecipesChanged(); child.refreshPublished();
                    require(child.getAvailablePatterns().isEmpty(), "Recipe reload and upstream refresh cannot republish a lease before real classifier validation"); stage = "revalidated";
                }
                case "revalidated" -> {
                    if (child.getAvailablePatterns().isEmpty()) return;
                    require(child.cloudStore().library(child.cloudLibraryId()).entry(entryId).lease().id().equals(validatedLeaseId),
                            "Actual allocator revalidation republishes the same exclusive lease without claiming a second original");
                    verifyLocalizedSearch();
                    if (phase.equals("long_dispatch")) {
                        var pattern = child.getAvailablePatterns().getFirst();
                        boolean visible = false;
                        for (var provider : ((appeng.me.service.CraftingService)child.getGrid().getCraftingService()).getProviders(pattern)) if (provider == child) visible = true;
                        if (!visible) return;
                        CloudLongDispatchRuntimeChecks.run(level, child, INPUT, OUTPUT, CHECKS); finish(null); return;
                    }
                    dispatch(); stage = "output";
                }
                case "output" -> {
                    if (genericFlow && ticks - dispatchedAt >= 40 && !child.iv$getState().jobs.isEmpty()
                            && child.iv$getState().jobs.getFirst().parallel == 0)
                        throw new AssertionError("Generic order never starts: " + genericProbe());
                    if (!child.iv$getState().jobs.isEmpty()) return;
                    var storage = child.getGrid().getStorageService().getInventory();
                    require(storage.extract(OUTPUT, 1, Actionable.MODULATE, IActionSource.ofPlayer(player)) == 1, "Real isolated executor delivers exact completed output to the physical AE item cell");
                    dispatch(); stage = "busy";
                }
                case "busy" -> {
                    if (child.iv$getState().jobs.isEmpty() || child.iv$getState().jobs.getFirst().parallel == 0) return;
                    if (genericFlow) {
                        var job = child.iv$getState().jobs.getFirst();
                        var modified = new IvNativeMatchContext(controller, Map.of(INPUT, 1L), Map.of(), 1, Math.max(1, IvMachineScope.parallel(controller))).prepare(job.recipe);
                        require(modified != null && modified.duration == job.duration && modified.duration != job.recipe.duration
                                        && com.gregtechceu.gtceu.api.recipe.RecipeHelper.getInputEUt(modified) == job.eut,
                                "Generic cloud execution preserves the real ordinary reactor recipe modifier, overclock duration and EU rate");
                    }
                    if (phase.equals("authority_flow")) {
                        var entry = child.cloudStore().library(child.cloudLibraryId()).entry(entryId);
                        CloudCoreAuthorityRuntimeChecks.busyExport(child, entry, CHECKS);
                        stage = "core_export_output"; break;
                    }
                    frozenId = child.cloudNodeId(); var before = child.iv$getState().save();
                    before.putBoolean("accepting", false); // Removal closes admission before freezing its paid work.
                    var oldRoute = child.getAvailablePatterns().getFirst(); traceGrid("before removal"); child.onMachineRemoved(); traceGrid("after onMachineRemoved");
                    require(!child.pushPattern(oldRoute, new KeyCounter[]{new KeyCounter()}), "Detached busy endpoint rejects its previously published lease route");
                    level.removeBlock(childPos, false);
                    removedAt = ticks; traceGrid("after removeBlock");
                    var frozen = child.cloudStore().node(frozenId);
                    require(frozen.detached() && frozen.ivState().equals(before) && child.cloudStore().library(child.cloudLibraryId()).entry(entryId).lease() == null,
                            "Removing real busy child freezes its exact started/paid IV ledger and immediately releases original lease");
                    var cloud = GTRegistries.MACHINES.get(new ResourceLocation("gtl_enhancedcore", "cloud_pattern_buffer"));
                    level.setBlock(childPos, cloud.defaultBlockState(), 3); child = (CloudPatternBufferMachine)MetaMachine.getMachine(level, childPos);
                    child.setFrontFacing(Direction.NORTH); child.onMachinePlaced(player, ItemStack.EMPTY); stage = "restore";
                }
                case "core_export_output" -> {
                    if (!child.iv$getState().jobs.isEmpty()) return;
                    require(child.getGrid().getStorageService().getInventory().extract(OUTPUT, 2, Actionable.MODULATE, IActionSource.ofPlayer(player)) == 1,
                            "Core withdrawal preserves actual paid execution and completes exactly one output");
                    require(child.getAvailablePatterns().isEmpty(), "Completed exported route is not advertised again");
                    require(CloudLibraryService.itemContents(child.cloudStore().library(child.cloudLibraryId()).entry(entryId).catalysts()).getStackInSlot(0).getCount() == 3,
                            "Core withdrawal and actual busy completion preserve all physical catalysts");
                    finish(null);
                }
                case "restore" -> {
                    connectToMain(child);
                    if (ticks - removedAt < 5 || !controller.isFormed() || !child.cloudReady(true)) return;
                    traceGrid("settled replacement");
                    require(((appeng.blockentity.networking.CreativeEnergyCellBlockEntity)level.getBlockEntity(powerPos)).getMainNode().getNode() == acceptedSourceNode
                                    && ((DriveBlockEntity)level.getBlockEntity(powerPos.north())).getMainNode().getNode() == acceptedDriveNode
                                    && mainGridBridge.getMainNode().getNode() == acceptedBridgeNode
                                    && child.getGrid() == sourceGrid() && acceptedDriveNode.getGrid() == child.getGrid()
                                    && acceptedBridgeNode.getGrid() == child.getGrid(),
                            "Replacement child rejoins the surviving original physical storage nodes and library bridge before explicit recovery" + recoveryDiagnostic());
                    var action = new CompoundTag(); action.putString("recoveryId", frozenId.toString()); action("recover", action);
                    require(!child.iv$getState().jobs.isEmpty(), "Actual server-authorized recovery action restores busy task to compatible real child on original live grid"
                            + recoveryDiagnostic());
                    stage = "recovered_output";
                }
                case "recovered_output" -> {
                    if (!child.iv$getState().jobs.isEmpty()) return;
                    var storage = child.getGrid().getStorageService().getInventory();
                    require(storage.extract(OUTPUT, 2, Actionable.MODULATE, IActionSource.ofPlayer(player)) == 1, "Recovered paid job delivers exactly one output without duplicate completion");
                    action("resume", new CompoundTag()); require(child.iv$getState().accepting, "Recovered child resumes through the real authorized UI action");
                    stage = "after_recovery_lease";
                }
                case "after_recovery_lease" -> {
                    if (child.getAvailablePatterns().isEmpty()) return;
                    dispatch(); stage = "after_recovery_output";
                }
                case "after_recovery_output" -> {
                    if (!child.iv$getState().jobs.isEmpty()) return;
                    require(child.getGrid().getStorageService().getInventory().extract(OUTPUT, 2, Actionable.MODULATE, IActionSource.ofPlayer(player)) == 1,
                            "A new published route after recovery accepts a fresh job and delivers exactly one additional output");
                    if (nativeFlow) require(CloudLibraryService.itemContents(child.cloudStore().library(child.cloudLibraryId()).catalysts()).getStackInSlot(0).getCount() == 1,
                            "Actual native execution and recovered execution share one physical central catalyst without consuming or duplicating it");
                    finish(null);
                }
                default -> {}
            }
        } catch (Throwable failure) { failure.printStackTrace(); finish(failure); }
    }
    private static void dispatch() {
        dispatchedAt = ticks;
        if (acceptedFlowGrid == null) {
            acceptedFlowGrid = child.getGrid();
            acceptedSourceNode = ((appeng.blockentity.networking.CreativeEnergyCellBlockEntity)level.getBlockEntity(powerPos)).getMainNode().getNode();
            acceptedDriveNode = ((DriveBlockEntity)level.getBlockEntity(powerPos.north())).getMainNode().getNode();
            acceptedBridgeNode = mainGridBridge.getMainNode().getNode();
        }
        traceGrid("dispatch");
        var storage = child.getGrid().getStorageService().getInventory(); var source = IActionSource.ofPlayer(player);
        require(storage.insert(INPUT, 1, Actionable.MODULATE, source) == 1 && storage.extract(INPUT, 1, Actionable.MODULATE, source) == 1, "AE dispatch removes exactly one physical input from real cell");
        var inputs = new KeyCounter(); inputs.add(INPUT, 1);
        require(child.pushPattern(child.getAvailablePatterns().getFirst(), new KeyCounter[]{inputs}) && child.iv$getState().jobs.size() == 1,
                "Real cloud pushPattern accepts isolated job from its published lease route");
    }
    private static String genericProbe() {
        var job = child.iv$getState().jobs.getFirst();
        var context = new IvNativeMatchContext(controller, job.inventory, Map.of(), Math.max(1, job.remaining), Math.max(1, IvMachineScope.parallel(controller)));
        var modifier = controller.getDefinition().getRecipeModifier();
        var chain = modifier instanceof com.gtl.enhancedcore.mixin.gtceu.RecipeModifierListAccessor list
                ? list.gtlEnhancedcore$getModifiers() : new com.gregtechceu.gtceu.api.recipe.modifier.RecipeModifier[]{modifier};
        return context.simulate(() -> {
            var params = new com.gregtechceu.gtceu.api.recipe.logic.OCParams();
            var result = new com.gregtechceu.gtceu.api.recipe.logic.OCResult();
            var current = job.recipe.copy(); var description = new StringBuilder();
            for (var entry : chain) {
                if (current == null) break;
                current = entry.apply(controller, current, params, result);
                description.append(entry.getClass().getName()).append("=>").append(current == null ? "null" : current.duration)
                        .append(" OC=").append(result.getDuration()).append('/').append(result.getEut()).append(';');
            }
            return "tier=" + controller.getTier() + " max=" + controller.getMaxVoltage() + " oc=" + controller.getOverclockVoltage()
                    + " prepared=" + context.prepare(job.recipe) + " chain=" + description
                    + " details=" + ((IvNativeAccess)controller.getRecipeLogic()).iv$summary();
        });
    }
    private static appeng.api.networking.IGrid sourceGrid() {
        return ((appeng.blockentity.networking.CreativeEnergyCellBlockEntity)level.getBlockEntity(powerPos)).getMainNode().getNode().getGrid();
    }
    private static void traceGrid(String stage) {
        var source = ((appeng.blockentity.networking.CreativeEnergyCellBlockEntity)level.getBlockEntity(powerPos)).getMainNode().getNode();
        var drive = ((DriveBlockEntity)level.getBlockEntity(powerPos.north())).getMainNode().getNode();
        var bridge = mainGridBridge.getMainNode().getNode();
        GRID_TRACE.add(stage + " tick=" + ticks + " acceptedGrid=" + System.identityHashCode(acceptedFlowGrid)
                + " sourceNode=" + System.identityHashCode(source) + "/grid=" + System.identityHashCode(source.getGrid())
                + " driveNode=" + System.identityHashCode(drive) + "/grid=" + System.identityHashCode(drive.getGrid())
                + " bridgeNode=" + System.identityHashCode(bridge) + "/grid=" + System.identityHashCode(bridge.getGrid()));
    }
    private static void verifyLocalizedSearch() {
        var bits = new BitSet(); bits.set(net.minecraft.core.registries.BuiltInRegistries.ITEM.getId(Items.GOLD_INGOT));
        var localized = new com.gtl.enhancedcore.common.cloud.ui.CloudPatternSearch(bits, new BitSet(), Set.of());
        var empty = com.gtl.enhancedcore.common.cloud.ui.CloudPatternSearch.EMPTY;
        var page = CloudLibraryService.page(level, child.cloudLibraryId(), "金锭", "", 0, "", localized);
        require(page.getInt("total") >= 1 && page.getList("entries", 10).getCompound(0).getString("nameJson").contains("item.minecraft.gold_ingot"),
                "Real dedicated-server directory accepts client registry aliases for Chinese search and preserves translated output component JSON");
        require(CloudLibraryService.page(level, child.cloudLibraryId(), "金锭", "", 0, "", empty).getInt("total") == 0,
                "Real English dedicated-server names cannot falsely satisfy Chinese search without client aliases");
        player.setPos(MAIN.getX() + .5, MAIN.getY() + .5, MAIN.getZ() - 1);
        player.containerMenu = new ModularUIContainer(main.createUI(player), 70);
        require(main.cloudUiSnapshot(player, "金锭", "", 0, "", 0, localized).getInt("total") >= 1,
                "Actual authorized main-library menu snapshot forwards localized Chinese search aliases");
        player.setPos(childPos.getX() + .5, childPos.getY() + .5, childPos.getZ() - 1);
        player.containerMenu = new ModularUIContainer(child.createUI(player), 71);
        var childPage = child.cloudUiSnapshot(player, "金锭", "", 0, "", 0, localized);
        require(!childPage.getList("entries", 10).isEmpty() && childPage.getList("entries", 10).getCompound(0).getString("nameJson").contains("item.minecraft.gold_ingot")
                        && child.cloudUiSnapshot(player, "金锭", "", 0, "", 0, empty).getList("entries", 10).isEmpty(),
                "Actual authorized child menu filters published leases with client aliases and preserves translation JSON");
    }
    private static void uploadPattern() {
        action("pause", new CompoundTag()); require(!child.iv$getState().accepting, "Actual authorized pause action closes new admission");
        var pattern = PatternDetailsHelper.encodeProcessingPattern(new GenericStack[]{new GenericStack(INPUT, phase.equals("long_dispatch") ? 2 : 1)}, new GenericStack[]{new GenericStack(OUTPUT, 1)});
        PatternQuickUploadMetadata.writeRecipeTypeId(pattern, child.cloudMode());
        var operation = UUID.randomUUID(); var store = child.cloudStore();
        var entry = store.upload(child.cloudLibraryId(), player.getUUID(), pattern, new CompoundTag(), operation); entryId = entry.id();
        if (phase.equals("authority_flow")) {
            var catalysts = new CompoundTag(); var items = CloudLibraryService.itemContents(new CompoundTag());
            items.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 3)); catalysts.put("items", items.serializeNBT());
            entry.setCatalysts(player.getUUID(), catalysts);
        }
        store.checkpoint(); store.acknowledge(child.cloudLibraryId(), player.getUUID(), operation); store.checkpoint(); pausedAt = ticks; stage = "paused";
    }
    private static CloudPatternBufferMachine placeChild(BlockPos position) {
        var cloud = GTRegistries.MACHINES.get(new ResourceLocation("gtl_enhancedcore", "cloud_pattern_buffer"));
        level.setBlock(position, cloud.defaultBlockState(), 3); var result = (CloudPatternBufferMachine)MetaMachine.getMachine(level, position);
        result.setFrontFacing(Direction.NORTH); result.onMachinePlaced(player, ItemStack.EMPTY); return result;
    }
    private static void connectToMain(CloudPatternBufferMachine endpoint) {
        var mainNode = mainGridBridge.getMainNode().getNode(); var endpointNode = endpoint.getMainNode().getNode();
        if (mainNode != null && endpointNode != null && mainNode.getGrid() != endpointNode.getGrid())
            appeng.api.networking.GridHelper.createConnection(mainNode, endpointNode);
    }
    private static void action(String name, CompoundTag payload) {
        player.setPos(childPos.getX() + .5, childPos.getY() + .5, childPos.getZ() - 1);
        player.containerMenu = new ModularUIContainer(child.createUI(player), 71);
        payload.putLong("revision", child.cloudUiSnapshot(player, "", "", 0, "").getLong("revision"));
        child.cloudUiAction(player, name, payload);
    }
    @SuppressWarnings("unchecked")
    private static String recoveryDiagnostic() throws Exception {
        var source = child.cloudStore().node(frozenId);
        var sourceOrigin = new com.gtl.enhancedcore.common.cloud.transfer.CloudAeOriginGuard(); sourceOrigin.load(source.sourceRoute());
        var registry = CloudOriginAuthorization.class.getDeclaredField("LIVE"); registry.setAccessible(true);
        var all = (Map<Object,Map<Object,java.lang.ref.WeakReference<Object>>>)registry.get(null);
        var known = all.getOrDefault(level.getServer(), Map.of()).get(sourceOrigin.origin());
        return " [status=" + child.cloudStatusLabel() + ", mode=" + child.cloudMode() + ", sourceMode=" + source.mode()
                + ", sourceRoute=" + source.sourceRoute() + ", targetGrid=" + System.identityHashCode(child.getGrid())
                + ", knownGrid=" + System.identityHashCode(known == null ? null : known.get())
                + ", bridgeGrid=" + System.identityHashCode(mainGridBridge.getMainNode().getNode().getGrid())
                + ", sourceGrid=" + System.identityHashCode(sourceGrid())
                + ", driveGrid=" + System.identityHashCode(((DriveBlockEntity)level.getBlockEntity(powerPos.north())).getMainNode().getNode().getGrid())
                + ", authorize=" + CloudOriginAuthorization.authorize(player, sourceOrigin.origin(), child.getGrid()) + "]";
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); CHECKS.add(message); }
    private static void finish(Throwable failure) {
        if (networkImportFlow) CloudNetworkImportRuntimeChecks.close();
        stage = "done"; var report = new LinkedHashMap<String,Object>(); report.put("status", failure == null ? "PASS" : "FAIL");
        report.put("phase", phase); report.put("machine", controller.getDefinition().getId().toString()); report.put("checks", CHECKS); report.put("gridTrace", GRID_TRACE); report.put("ticks", ticks); report.put("seconds", (System.nanoTime() - started) / 1_000_000_000.0);
        if (genericFlow && controller.getRecipeLogic() instanceof IvNativeAccess access) {
            report.put("nativeSummary", access.iv$summary().toString()); report.put("nativeMessage", access.iv$message());
            report.put("actualTier", controller.getTier()); report.put("actualVoltage", controller.getOverclockVoltage());
            report.put("jobs", child.iv$getState().save().toString());
            if (!child.iv$getState().jobs.isEmpty()) {
                var job = child.iv$getState().jobs.getFirst();
                var probe = new IvNativeMatchContext(controller, job.inventory, Map.of(), Math.max(1, job.remaining), Math.max(1, IvMachineScope.parallel(controller)));
                report.put("modifierProbe", String.valueOf(probe.prepare(job.recipe)));
                report.put("conditionProbe", job.recipe.checkConditions(controller.getRecipeLogic()).toString());
            }
        }
        if (failure != null) report.put("error", failure.toString());
        report.put("scope", "Actual registered production geometry (plasma 17x12x23, native shockburst 9x7x9, or unlisted large chemical reactor 3x3x3), physical AE creative cell/drive, audit-only deterministic recipe, real upload/automatic lease/push/isolated execution/removal/UI recovery; no client automation or arbitrary kill guarantee");
        if (Boolean.getBoolean("gtl.cloudPassiveRefreshAudit")) report.put("scope",
                "Minimal real 3x3x3 LCR and library, native cloud child, two 100-tick stable windows, menu first packets, upload-event allocation and physical AE cell disconnect/reconnect; no recipe execution matrix");
        if (networkImportFlow) report.put("scope",
                "Real formed 3x3x3 LCR/library and native AE grid; ordinary and super pattern buffers, AE provider, drive storage and fixed encoding terminal; whole-network original movement, complete NBT deduplication, material-bearing source rejection and repeat-run conservation; fixture-only online UUID registration, no player-world or arbitrary rollback guarantee");
        if (phase.equals("long_dispatch")) report.put("scope",
                "Real formed production LCR/library and actual AE grid/provider; actual transformed ordinary AE CPU executeCrafting, actual GTLCore AEUtils extraction, actual cloud push and persisted job codec with controlled CPU plan/inventory; not billions of recipe ticks or physical storage capacity");
        try { Files.writeString(level.getServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getParent().resolve("cloud-store-" + phase + ".json"), new GsonBuilder().setPrettyPrinting().create().toJson(report)); }
        catch (Exception error) { throw new RuntimeException(error); }
        level.getServer().halt(false);
    }
}
