package com.gtl.enhancedcore.audit;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageCells;
import appeng.api.storage.cells.StorageCell;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.file.FileWatcher;
import com.glodblock.github.extendedae.config.EPPConfig;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler;
import com.gtl.enhancedcore.common.config.InfinityCellConfigInjector;
import com.gtl.enhancedcore.common.config.SingularityRecipeConfig.Entry;
import com.gtl.enhancedcore.common.data.machine.InfinitySingularityRecipeLoader;
import com.gtl.enhancedcore.common.machine.GTLEnhancedcoreMachines;
import com.gtl.enhancedcore.common.machine.hatch.MEDrivePartMachine;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.ConfigTracker;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;

public final class ModuleStabilityRuntimeChecks {
    private static final Gson JSON = new Gson();
    private static final String BOOT_ID = UUID.randomUUID().toString();
    private static final BlockPos MACHINE_POS = new BlockPos(6404, 224, 6404);
    private static final BlockPos DONOR_POS = MACHINE_POS.east();
    private static final BlockPos POWER_POS = DONOR_POS.east();
    private static final BlockPos CHEST_POS = MACHINE_POS.south(3);
    private static final int SLOT_COUNT = 630;
    private static final int INITIAL_CELLS = 638;
    private static final int SAVED_CELLS = 6;
    private static final int PRIORITY = 12345;
    private static Session current;

    private ModuleStabilityRuntimeChecks() {}

    public static String start(MinecraftServer server) throws Exception {
        if (current != null) throw new IllegalStateException("Probe already started in this JVM; use a real restart");
        current = new Session(server);
        try {
            current.begin();
        } catch (Exception | AssertionError error) {
            current.fail(error);
        }
        return current.result();
    }

    public static String tick(MinecraftServer server) throws Exception {
        if (current == null) throw new IllegalStateException("Probe was not started");
        if (!current.terminal && current.server != server) throw new IllegalStateException("Different server instance");
        if (!current.terminal) {
            try {
                current.advance();
            } catch (Exception | AssertionError error) {
                current.fail(error);
            }
        }
        return current.result();
    }

    public static String abort(MinecraftServer server, String reason) throws Exception {
        if (current == null || current.server != server) throw new IllegalStateException(reason);
        current.fail(new IllegalStateException(reason));
        return current.result();
    }

    private enum Phase {
        WAIT_GRID, SEED, FILL, FULL, REMOVE_FIRST, REFILL_FIRST, REMOVE_LAST, REFILL_LAST,
        PAYLOAD, VERIFY_PAYLOAD, CONFIG_INITIAL, CONFIG_REPLACED, CONFIG_WITHDRAWN,
        CONFIG_RESTORE, SAVE, RESTART_VERIFY, FAILURE_WAIT, FAILURE_RESTORE, RECOVERY_RESTORE, FINISHED
    }

    private static final class Session {
        private MinecraftServer server;
        private ServerLevel world;
        private final Path game;
        private final Path worldPath;
        private final Path config;
        private final Path checkpointPath;
        private final Path resultPath;
        private final Path journalPath;
        private final JsonObject coverage = new JsonObject();
        private final JsonArray completed = new JsonArray();
        private Phase phase = Phase.WAIT_GRID;
        private long phaseStarted = System.nanoTime();
        private int phaseTicks;
        private int assertions;
        private int freshAssertions;
        private int filled;
        private int pullCalls;
        private boolean restart;
        private boolean terminal;
        private boolean forcedBefore;
        private boolean forceHeld;
        private boolean watcherSuspended;
        private String status = "RUNNING";
        private String failure = "";
        private String restorationError = "";
        private JsonObject checkpoint;
        private JsonObject journal;
        private MEDrivePartMachine machine;
        private DriveBlockEntity donor;
        private ChestBlockEntity chest;
        private NotifiableItemStackHandler slots;
        private Method productionPull;
        private IActionSource source;
        private AEItemKey cellKey;
        private final AEItemKey diamond = AEItemKey.of(new ItemStack(Items.DIAMOND));
        private final AEItemKey emerald = AEItemKey.of(new ItemStack(Items.EMERALD));
        private final AEItemKey gold = AEItemKey.of(new ItemStack(Items.GOLD_INGOT));
        private StorageCell oldFirst;
        private StorageCell oldLast;
        private CompletableFuture<Void> reload;
        private ModConfig eppConfig;
        private ForgeConfigSpec.ConfigValue<List<? extends String>> eppTypes;
        private Runnable registeredWatcher;
        private Runnable forgeWatcher;
        private Path eppWatchPath;
        private Path eppPath;
        private Path recipePath;
        private List<Entry> materialsBefore;
        private List<Item> itemsBefore;
        private List<Fluid> fluidsBefore;
        private List<Item> injectedItems;
        private List<Fluid> injectedFluids;
        private List<Item> injectedItemSnapshot;
        private List<Fluid> injectedFluidSnapshot;
        private Item materialItemA;
        private Item materialItemB;
        private Fluid materialFluidA;
        private Fluid materialFluidB;

        private Session(MinecraftServer server) throws Exception {
            this.server = server;
            check(server.isSameThread(), "Probe must run on the real server thread");
            Path project = Path.of("C:/IDEA/GTL-Enhancedcore").toRealPath();
            Path audit = project.resolve("_audit").toRealPath();
            game = FMLPaths.GAMEDIR.get().toRealPath();
            check(audit.startsWith(project) && game.startsWith(audit) && !game.equals(audit),
                    "gameDir is not a real isolated project _audit instance: " + game);
            check(Files.isRegularFile(game.resolve("module-stability.marker"), LinkOption.NOFOLLOW_LINKS),
                    "Missing explicit module-stability.marker");
            config = FMLPaths.CONFIGDIR.get().toRealPath();
            worldPath = server.getWorldPath(LevelResource.ROOT).toRealPath();
            check(config.startsWith(game) && worldPath.startsWith(game), "Config/world escaped the isolated instance");
            Path kubejs = game.resolve("kubejs").toRealPath();
            check(kubejs.startsWith(game), "KubeJS directory escaped the isolated instance");
            checkpointPath = kubejs.resolve("module_stability_checkpoint.json");
            resultPath = kubejs.resolve("module_stability_result.json");
            journalPath = kubejs.resolve("module_stability_config_backup.json");
            world = server.overworld();
            check(world != null, "Missing real ServerLevel");
        }

        private void begin() throws Exception {
            if (Files.exists(journalPath)) {
                journal = readJson(journalPath);
                if (!journal.get("restored").getAsBoolean()) {
                    initializeConfig();
                    suspendWatcher();
                    restoreConfiguration();
                    next(Phase.RECOVERY_RESTORE);
                    return;
                }
                journal = null;
            }
            restart = Files.exists(checkpointPath);
            if (restart) {
                checkpoint = readJson(checkpointPath);
                check(checkpoint.get("schema").getAsInt() == 1, "Unknown checkpoint schema");
                check("WAITING_REAL_RESTART".equals(checkpoint.get("status").getAsString()),
                        "Checkpoint already completed; use a clean isolated world for another round");
                check(!BOOT_ID.equals(checkpoint.get("bootId").getAsString()), "Script reload is not a real JVM restart");
                check(worldPath.toString().equals(checkpoint.get("worldPath").getAsString()), "Different saved world");
                freshAssertions = checkpoint.get("assertions").getAsInt();
            }
            int chunkX = MACHINE_POS.getX() >> 4;
            int chunkZ = MACHINE_POS.getZ() >> 4;
            forcedBefore = world.getForcedChunks().contains(net.minecraft.world.level.ChunkPos.asLong(chunkX, chunkZ));
            world.setChunkForced(chunkX, chunkZ, true);
            forceHeld = true;
            if (!restart) createFixture();
            bindFixture();
            next(Phase.WAIT_GRID);
        }

        private void createFixture() throws Exception {
            for (BlockPos position : List.of(MACHINE_POS, DONOR_POS, POWER_POS, CHEST_POS)) {
                check(world.getBlockState(position).isAir() && world.getBlockEntity(position) == null,
                        "Fixture position is not empty: " + position);
            }
            var definition = GTLEnhancedcoreMachines.getMEDrive();
            check(definition != null, "ME integrated drive was not registered");
            check(world.setBlock(MACHINE_POS, definition.defaultBlockState(), 3), "Failed to place registered ME drive");
            check(world.setBlock(DONOR_POS, AEBlocks.DRIVE.block().defaultBlockState(), 3), "Failed to place real AE2 donor");
            check(world.setBlock(POWER_POS, AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState(), 3),
                    "Failed to place real AE2 power source");
            check(world.setBlock(CHEST_POS, Blocks.CHEST.defaultBlockState(), 3), "Failed to place disconnected evidence chest");
        }

        private void bindFixture() throws Exception {
            check(MetaMachine.getMachine(world, MACHINE_POS) instanceof MEDrivePartMachine, "Missing production ME machine");
            machine = (MEDrivePartMachine) MetaMachine.getMachine(world, MACHINE_POS);
            check(world.getBlockEntity(DONOR_POS) instanceof DriveBlockEntity, "Donor is not a real AE2 drive");
            donor = (DriveBlockEntity) world.getBlockEntity(DONOR_POS);
            check(world.getBlockEntity(CHEST_POS) instanceof ChestBlockEntity, "Missing evidence chest");
            chest = (ChestBlockEntity) world.getBlockEntity(CHEST_POS);
            var storageField = MEDrivePartMachine.class.getDeclaredField("machineStorage");
            storageField.setAccessible(true);
            slots = (NotifiableItemStackHandler) storageField.get(machine);
            check(slots.getSlots() == SLOT_COUNT, "Production drive does not expose all 630 slots");
            productionPull = MEDrivePartMachine.class.getDeclaredMethod("tryPullCells");
            productionPull.setAccessible(true);
            source = IActionSource.ofMachine(() -> machine.getMainNode().getNode());
            cellKey = AEItemKey.of(AEItems.ITEM_CELL_1K.stack());
            check(!machine.autoFillCells, "Automatic polling must not race this deterministic probe");
            if (!restart) {
                check(donor.getInternalInventory().insertItem(0, AEItems.ITEM_CELL_64K.stack(), false).isEmpty(),
                        "Real donor rejected its storage cell");
                machine.drivePriority = PRIORITY;
                machine.markDirty();
                IStorageProvider.requestUpdate(machine.getMainNode());
            }
        }

        private void advance() throws Exception {
            check(server.isSameThread(), "Probe left the server thread");
            phaseTicks++;
            long seconds = (System.nanoTime() - phaseStarted) / 1_000_000_000L;
            check(seconds < (reload == null ? 120 : 600), "Timed out in " + phase);
            switch (phase) {
                case WAIT_GRID -> {
                    if (!online() || phaseTicks < 20) return;
                    check(grid() == donor.getMainNode().getGrid(), "Donor and integrated drive are not on the same real grid");
                    check(grid().getEnergyService().isNetworkPowered(), "Real AE2 fixture has no power");
                    next(restart ? Phase.RESTART_VERIFY : Phase.SEED);
                }
                case SEED -> {
                    for (int slot = 0; slot < SLOT_COUNT; slot++) check(slots.getStackInSlot(slot).isEmpty(), "Nonempty fresh slot " + slot);
                    check(network().getAvailableStacks().size() == 0, "Unexpected initial network contents");
                    check(network().insert(cellKey, INITIAL_CELLS, Actionable.MODULATE, source) == INITIAL_CELLS,
                            "Real AE2 donor cannot store the 638 empty cells; no substitute storage is used");
                    check(donorCount(cellKey) == INITIAL_CELLS, "Seeding did not reach the actual donor cell");
                    next(Phase.FILL);
                }
                case FILL -> {
                    for (int attempt = 0; attempt < 8 && filled < SLOT_COUNT; attempt++) {
                        check(slots.getStackInSlot(filled).isEmpty(), "Fill cursor is not empty: " + filled);
                        pull();
                        check(isStockCell(slots.getStackInSlot(filled)), "Production path failed to fill slot " + filled);
                        filled++;
                        check(donorCount(cellKey) + filled == INITIAL_CELLS, "Fill lost or duplicated a real source cell");
                    }
                    if (filled == SLOT_COUNT) next(Phase.FULL);
                }
                case FULL -> {
                    if (phaseTicks < 5) return;
                    verifySlots(false);
                    JsonArray before = slotSnapshot();
                    for (int attempt = 0; attempt < 32; attempt++) {
                        pull();
                        check(donorCount(cellKey) == INITIAL_CELLS - SLOT_COUNT, "Full drive consumed a source cell");
                        check(networkCount(cellKey) == INITIAL_CELLS - SLOT_COUNT, "Full drive changed the real network balance");
                    }
                    check(before.equals(slotSnapshot()), "Full-drive polling changed persisted slot stacks");
                    ItemStack offered = AEItems.ITEM_CELL_1K.stack();
                    check(slots.insertItem(0, offered, false).getCount() == 1 && offered.getCount() == 1,
                            "Occupied GUI-backed handler accepted/consumed an extra cell");
                    passed("all_630_slots_and_full_drive_no_extra_consumption");
                    next(Phase.REMOVE_FIRST);
                }
                case REMOVE_FIRST -> {
                    check(network().insert(diamond, 17, Actionable.MODULATE, source) == 17, "Network refused the cache-refresh payload");
                    oldFirst = machine.getCellInventory(0);
                    oldLast = machine.getCellInventory(SLOT_COUNT - 1);
                    check(oldFirst.getAvailableStacks().get(diamond) == 17, "Priority did not place payload in the first real cell");
                    check(donorCount(diamond) == 0, "Payload reached donor instead of integrated drive");
                    remove(0, 0, diamond, 17);
                    next(Phase.REFILL_FIRST);
                }
                case REFILL_FIRST -> {
                    if (phaseTicks < 5) return;
                    check(networkCount(diamond) == 0 && cachedCount(diamond) == 0, "Removing a cell retained its mounted/cached payload");
                    pull();
                    check(isStockCell(slots.getStackInSlot(0)), "Production refill failed at first slot");
                    check(machine.getCellInventory(0) != oldFirst && machine.getCellInventory(0).getAvailableStacks().size() == 0,
                            "First refill reused the removed storage-cell cache");
                    check(machine.getCellInventory(SLOT_COUNT - 1) != oldLast, "Storage callback retained another stale cached cell");
                    passed("first_slot_extraction_refill_and_actual_network_cache_refresh");
                    next(Phase.REMOVE_LAST);
                }
                case REMOVE_LAST -> {
                    oldLast = machine.getCellInventory(SLOT_COUNT - 1);
                    remove(SLOT_COUNT - 1, 1, diamond, 0);
                    next(Phase.REFILL_LAST);
                }
                case REFILL_LAST -> {
                    if (phaseTicks < 5) return;
                    pull();
                    check(isStockCell(slots.getStackInSlot(SLOT_COUNT - 1)), "Production refill failed at last slot 629");
                    check(machine.getCellInventory(SLOT_COUNT - 1) != oldLast, "Last refill reused its removed cache");
                    check(donorCount(cellKey) == SAVED_CELLS, "Two refills did not consume exactly two real source cells");
                    passed("last_page_slot_629_extraction_and_refill");
                    next(Phase.PAYLOAD);
                }
                case PAYLOAD -> {
                    check(machine.getCellInventory(0).insert(gold, 7, Actionable.MODULATE, source) == 7, "First real cell refused saved payload");
                    check(machine.getCellInventory(SLOT_COUNT - 1).insert(emerald, 19, Actionable.MODULATE, source) == 19,
                            "Last real cell refused saved payload");
                    IStorageProvider.requestUpdate(machine.getMainNode());
                    next(Phase.VERIFY_PAYLOAD);
                }
                case VERIFY_PAYLOAD -> {
                    if (phaseTicks < 5) return;
                    verifyConservation();
                    passed("real_cell_payload_persistence_and_conservation_before_save");
                    if (Boolean.parseBoolean(System.getProperty("gtl.enhancedcore.moduleProbeMaterials", "true"))) {
                        beginConfiguration();
                    } else {
                        coverage.addProperty("material_reload", "SKIPPED_BY_OPERATOR");
                        next(Phase.SAVE);
                    }
                }
                case CONFIG_INITIAL -> {
                    if (!reloadFinished()) return;
                    check(EPPConfig.infCellItem.contains(materialItemA) && EPPConfig.infCellFluid.contains(materialFluidA),
                            "Actual server reload listener did not inject registered fixture materials");
                    injectedItems = EPPConfig.infCellItem;
                    injectedFluids = EPPConfig.infCellFluid;
                    injectedItemSnapshot = List.copyOf(injectedItems);
                    injectedFluidSnapshot = List.copyOf(injectedFluids);
                    replaceExtendedConfig();
                    setMaterials(materialItemB, materialFluidB);
                    startReload();
                    next(Phase.CONFIG_REPLACED);
                }
                case CONFIG_REPLACED -> {
                    if (!reloadFinished()) return;
                    verifyReplacement(true);
                    InfinityCellConfigInjector.inject();
                    InfinityCellConfigInjector.inject();
                    verifyReplacement(true);
                    setMaterials(null, null);
                    startReload();
                    next(Phase.CONFIG_WITHDRAWN);
                }
                case CONFIG_WITHDRAWN -> {
                    if (!reloadFinished()) return;
                    verifyReplacement(false);
                    passed("registered_material_loader_forge_config_watcher_listener_ownership_and_idempotence");
                    restoreConfiguration();
                    next(Phase.CONFIG_RESTORE);
                }
                case CONFIG_RESTORE -> {
                    if (!reloadFinished()) return;
                    verifyRestoration();
                    passed("config_bytes_material_snapshot_and_live_extendedae_lists_restored");
                    next(Phase.SAVE);
                }
                case SAVE -> saveCheckpoint();
                case RESTART_VERIFY -> verifyRestart();
                case FAILURE_WAIT -> {
                    if (!reload.isDone()) return;
                    reload = null;
                    restoreConfiguration();
                    next(Phase.FAILURE_RESTORE);
                }
                case FAILURE_RESTORE, RECOVERY_RESTORE -> {
                    if (!reloadFinished()) return;
                    verifyRestoration();
                    finish(phase == Phase.RECOVERY_RESTORE ? "RECOVERED_INTERRUPTED_RUN" : "FAILED");
                }
                case FINISHED -> { }
            }
        }

        private boolean online() {
            return machine.getMainNode().isReady() && machine.getMainNode().isOnline()
                    && donor.getMainNode().isReady() && donor.getMainNode().isOnline()
                    && machine.getMainNode().getGrid() != null;
        }

        private IGrid grid() { return machine.getMainNode().getGrid(); }
        private MEStorage network() { return grid().getStorageService().getInventory(); }
        private long networkCount(AEItemKey key) { return network().getAvailableStacks().get(key); }
        private long cachedCount(AEItemKey key) { return grid().getStorageService().getCachedInventory().get(key); }

        private long donorCount(AEItemKey key) {
            StorageCell cell = donor.getOriginalCellInventory(0);
            check(cell != null, "Actual donor cell inventory is unavailable");
            return cell.getAvailableStacks().get(key);
        }

        private boolean isStockCell(ItemStack stack) {
            return stack.getItem() == AEItems.ITEM_CELL_1K.stack().getItem() && stack.getCount() == 1;
        }

        private void pull() throws Exception {
            check(online(), "Real AE2 grid went offline before production fill");
            check(!machine.autoFillCells, "Unexpected automatic fill race");
            machine.autoFillCells = true;
            try {
                productionPull.invoke(machine);
                pullCalls++;
            } finally {
                machine.autoFillCells = false;
            }
            var pulling = MEDrivePartMachine.class.getDeclaredField("inAutoPull");
            pulling.setAccessible(true);
            check(!pulling.getBoolean(machine), "Production fill failed to clear its reentrancy guard");
        }

        private void remove(int slot, int evidenceSlot, AEItemKey payload, long amount) {
            check(chest.getItem(evidenceSlot).isEmpty(), "Evidence slot is occupied");
            ItemStack removed = slots.extractItem(slot, 1, false);
            check(isStockCell(removed) && slots.getStackInSlot(slot).isEmpty(), "Actual handler extraction failed at " + slot);
            check(machine.getCellInventory(slot) == null, "Empty slot retained a cell inventory");
            StorageCell removedCell = StorageCells.getCellInventory(removed, null);
            check(removedCell != null && removedCell.getAvailableStacks().get(payload) == amount,
                    "Removed real cell lost/duplicated its persisted payload");
            chest.setItem(evidenceSlot, removed);
            chest.setChanged();
        }

        private void verifySlots(boolean withPayload) {
            for (int slot = 0; slot < SLOT_COUNT; slot++) {
                check(isStockCell(slots.getStackInSlot(slot)), "Missing/stacked/nonfixture cell at " + slot);
                check(slots.getSlotLimit(slot) == 1, "Production slot limit changed at " + slot);
                StorageCell cell = machine.getCellInventory(slot);
                check(cell != null, "Real StorageCells inventory missing at " + slot);
                var contents = cell.getAvailableStacks();
                long expectedGold = withPayload && slot == 0 ? 7 : 0;
                long expectedEmerald = withPayload && slot == SLOT_COUNT - 1 ? 19 : 0;
                check(contents.get(gold) == expectedGold && contents.get(emerald) == expectedEmerald,
                        "Actual cell payload mismatch at " + slot);
                check(contents.size() == (expectedGold + expectedEmerald == 0 ? 0 : 1), "Unexpected real cell contents at " + slot);
            }
        }

        private void verifyConservation() {
            check(online(), "Fixture grid is offline during conservation check");
            verifySlots(true);
            check(donorCount(cellKey) == SAVED_CELLS, "Saved source-cell balance changed");
            check(donor.getOriginalCellInventory(0).getAvailableStacks().size() == 1, "Donor contains unexpected items");
            check(networkCount(cellKey) == SAVED_CELLS && cachedCount(cellKey) == SAVED_CELLS,
                    "Actual and cached source-cell balances differ");
            check(networkCount(gold) == 7 && cachedCount(gold) == 7, "First mounted payload is not visible in the real network/cache");
            check(networkCount(emerald) == 19 && cachedCount(emerald) == 19, "Last mounted payload is not visible in the real network/cache");
            check(networkCount(diamond) == 0 && cachedCount(diamond) == 0, "Removed payload is still exposed by the network");
            check(network().getAvailableStacks().size() == 3, "Network exposes unexpected fixture keys");
            for (int slot = 0; slot < chest.getContainerSize(); slot++) {
                ItemStack stack = chest.getItem(slot);
                if (slot > 1) {
                    check(stack.isEmpty(), "Unexpected evidence chest stack at " + slot);
                    continue;
                }
                check(isStockCell(stack), "Removed cell disappeared or stacked in evidence slot " + slot);
                StorageCell evidence = StorageCells.getCellInventory(stack, null);
                check(evidence != null, "Removed cell NBT cannot be read by actual StorageCells");
                check(evidence.getAvailableStacks().get(diamond) == (slot == 0 ? 17 : 0), "Removed payload is not conserved");
                check(evidence.getAvailableStacks().size() == (slot == 0 ? 1 : 0), "Unexpected removed-cell contents");
            }
            long installed = 0;
            for (int slot = 0; slot < SLOT_COUNT; slot++) installed += slots.getStackInSlot(slot).getCount();
            long removed = (long) chest.getItem(0).getCount() + chest.getItem(1).getCount();
            check(installed + donorCount(cellKey) + removed == INITIAL_CELLS, "Measured physical cell accounting does not sum to 638");
            check(world.getEntitiesOfClass(ItemEntity.class, new AABB(MACHINE_POS).inflate(5)).isEmpty(),
                    "Fixture unexpectedly dropped loose item entities");
        }

        private JsonArray slotSnapshot() {
            return snapshot(slots.getSlots(), slots::getStackInSlot);
        }

        private JsonArray snapshot(int size, java.util.function.IntFunction<ItemStack> getter) {
            JsonArray result = new JsonArray();
            for (int slot = 0; slot < size; slot++) result.add(stackSnapshot(getter.apply(slot)).toString());
            return result;
        }

        private CompoundTag stackSnapshot(ItemStack stack) {
            return (stack.isEmpty() ? new ItemStack(Items.AIR, 0) : stack).save(new CompoundTag());
        }

        private void verifySnapshotSemantics() {
            ItemStack emptyOne = new ItemStack(Items.AIR, 1);
            ItemStack emptyZero = new ItemStack(Items.AIR, 0);
            check(emptyOne.isEmpty() && emptyZero.isEmpty(), "Real ItemStack air sentinels must both be empty");
            check(!emptyOne.save(new CompoundTag()).equals(emptyZero.save(new CompoundTag())),
                    "Real empty sentinel counts no longer reproduce the failed checkpoint boundary");
            check(stackSnapshot(emptyOne).equals(stackSnapshot(emptyZero)), "Empty inventory snapshot is not canonical");
            ItemStack occupied = new ItemStack(Items.DIAMOND);
            CompoundTag metadata = new CompoundTag();
            metadata.putLong("module_snapshot_probe", Long.MAX_VALUE);
            occupied.setTag(metadata);
            check(stackSnapshot(occupied).equals(occupied.save(new CompoundTag())), "Nonempty snapshot discarded full NBT");
            ItemStack changed = occupied.copy();
            changed.getOrCreateTag().putLong("module_snapshot_probe", Long.MAX_VALUE - 1);
            check(!stackSnapshot(occupied).equals(stackSnapshot(changed)), "Nonempty NBT mismatch was hidden");
            changed = occupied.copy();
            changed.setCount(2);
            check(!stackSnapshot(occupied).equals(stackSnapshot(changed)), "Nonempty stack count mismatch was hidden");
            changed = new ItemStack(Items.EMERALD);
            changed.setTag(metadata.copy());
            check(!stackSnapshot(occupied).equals(stackSnapshot(changed)), "Nonempty item identity mismatch was hidden");
        }

        private void compareSnapshot(String inventory, JsonArray expected, int size,
                                     java.util.function.IntFunction<ItemStack> getter) throws Exception {
            check(expected.size() == size, "Saved inventory size changed");
            for (int slot = 0; slot < size; slot++) {
                CompoundTag saved = TagParser.parseTag(expected.get(slot).getAsString());
                ItemStack savedStack = ItemStack.of(saved.copy());
                if (savedStack.isEmpty()) saved = stackSnapshot(savedStack);
                CompoundTag actual = stackSnapshot(getter.apply(slot));
                if (!saved.equals(actual)) {
                    JsonObject mismatch = new JsonObject();
                    mismatch.addProperty("marker", "MODULE_STABILITY");
                    mismatch.addProperty("bootId", BOOT_ID);
                    mismatch.addProperty("worldPath", worldPath.toString());
                    mismatch.addProperty("inventory", inventory);
                    mismatch.addProperty("slot", slot);
                    mismatch.addProperty("expected", saved.toString());
                    mismatch.addProperty("actual", actual.toString());
                    mismatch.addProperty("expectedAfterRealItemStackLoad",
                            ItemStack.of(saved.copy()).save(new CompoundTag()).toString());
                    writeJson(resultPath.resolveSibling("module_stability_nbt_mismatch.json"), mismatch);
                }
                check(saved.equals(actual), "Real restart changed full stack NBT in " + inventory + " at " + slot
                        + "; expected=" + saved + "; actual=" + actual);
            }
        }

        private void saveCheckpoint() throws Exception {
            verifySnapshotSemantics();
            passed("real_empty_slot_semantics_and_strict_nonempty_snapshot_checks");
            verifyConservation();
            for (int slot = 0; slot < SLOT_COUNT; slot++) machine.getCellInventory(slot).persist();
            donor.getOriginalCellInventory(0).persist();
            machine.markDirty();
            donor.setChanged();
            chest.setChanged();
            JsonObject saved = new JsonObject();
            saved.addProperty("schema", 1);
            saved.addProperty("status", "WAITING_REAL_RESTART");
            saved.addProperty("bootId", BOOT_ID);
            saved.addProperty("gameDir", game.toString());
            saved.addProperty("worldPath", worldPath.toString());
            saved.addProperty("fixture", "6404,224,6404;minecraft:overworld");
            saved.addProperty("sourceRemaining", SAVED_CELLS);
            saved.addProperty("removedCells", 2);
            saved.addProperty("pullCalls", pullCalls);
            saved.add("slots", slotSnapshot());
            saved.add("donor", snapshot(donor.getInternalInventory().size(), donor.getInternalInventory()::getStackInSlot));
            saved.add("chest", snapshot(chest.getContainerSize(), chest::getItem));
            saved.add("coverage", coverage.deepCopy());
            saved.add("completed", completed.deepCopy());
            releaseChunk();
            check(server.saveEverything(true, true, true), "Real server save/flush failed");
            saved.addProperty("assertions", assertions);
            writeJson(checkpointPath, saved);
            checkpoint = saved;
            finish("WAITING_REAL_RESTART");
        }

        private void verifyRestart() throws Exception {
            verifySnapshotSemantics();
            check("6404,224,6404;minecraft:overworld".equals(checkpoint.get("fixture").getAsString()), "Different fixture coordinates");
            check(game.toString().equals(checkpoint.get("gameDir").getAsString()), "Checkpoint came from another instance");
            check(machine.drivePriority == PRIORITY && !machine.autoFillCells, "Persisted drive settings changed");
            verifyConservation();
            compareSnapshot("machine", checkpoint.getAsJsonArray("slots"), slots.getSlots(), slots::getStackInSlot);
            compareSnapshot("donor", checkpoint.getAsJsonArray("donor"), donor.getInternalInventory().size(), donor.getInternalInventory()::getStackInSlot);
            compareSnapshot("chest", checkpoint.getAsJsonArray("chest"), chest.getContainerSize(), chest::getItem);
            for (int attempt = 0; attempt < 32; attempt++) {
                pull();
                check(donorCount(cellKey) == SAVED_CELLS, "Full saved drive consumed a cell after real restart");
            }
            checkpoint.getAsJsonObject("coverage").entrySet().forEach(entry -> coverage.add(entry.getKey(), entry.getValue().deepCopy()));
            checkpoint.getAsJsonArray("completed").forEach(entry -> completed.add(entry.deepCopy()));
            passed("real_jvm_restart_all_630_slot_nbt_donor_chest_payload_and_balance_conservation");
            checkpoint.addProperty("status", "COMPLETE");
            checkpoint.addProperty("restartBootId", BOOT_ID);
            checkpoint.addProperty("restartAssertions", assertions);
            checkpoint.addProperty("totalAssertions", freshAssertions + assertions);
            checkpoint.add("coverage", coverage.deepCopy());
            checkpoint.add("completed", completed.deepCopy());
            writeJson(checkpointPath, checkpoint);
            finish("COMPLETE");
        }

        @SuppressWarnings("unchecked")
        private void initializeConfig() throws Exception {
            eppConfig = ConfigTracker.INSTANCE.fileMap().values().stream()
                    .filter(candidate -> candidate.getSpec() == EPPConfig.SPEC).findFirst()
                    .orElseThrow(() -> new IllegalStateException("No loaded ExtendedAE Forge ModConfig"));
            check(eppConfig.getConfigData() instanceof CommentedFileConfig, "ExtendedAE config is not a real loaded file config");
            eppPath = checkedExistingFile(eppConfig.getFullPath(), config);
            recipePath = checkedExistingFile(config.resolve("GTL-Enhancedcore/infinity_singularity_recipes.json"), config);
            var typesField = EPPConfig.class.getDeclaredField("INFINITY_CELL_TYPES");
            typesField.setAccessible(true);
            eppTypes = (ForgeConfigSpec.ConfigValue<List<? extends String>>) typesField.get(null);
            eppWatchPath = eppConfig.getFullPath().toAbsolutePath();
            var watchedFilesField = FileWatcher.class.getDeclaredField("watchedFiles");
            watchedFilesField.setAccessible(true);
            Map<?, ?> watchedFiles = (Map<?, ?>) watchedFilesField.get(FileWatcher.defaultInstance());
            Object registration = watchedFiles.get(eppWatchPath);
            check(registration != null, "Real ExtendedAE config file watcher is disabled/unavailable; no fake watcher is substituted");
            var handlerField = registration.getClass().getDeclaredField("changeHandler");
            handlerField.setAccessible(true);
            registeredWatcher = (Runnable) handlerField.get(registration);
            forgeWatcher = registeredWatcher;
            if (forgeWatcher.getClass().getName().equals(
                    "org.embeddedt.modernfix.forge.config.NightConfigFixer$MonitoringConfigTracker")) {
                var delegateField = forgeWatcher.getClass().getDeclaredField("configTracker");
                delegateField.setAccessible(true);
                forgeWatcher = (Runnable) delegateField.get(forgeWatcher);
            }
            check(forgeWatcher.getClass().getName().equals("net.minecraftforge.fml.config.ConfigFileTypeHandler$ConfigWatcher"),
                    "Unexpected config watcher implementation: " + forgeWatcher.getClass().getName());
        }

        private void beginConfiguration() throws Exception {
            initializeConfig();
            materialsBefore = InfinitySingularityRecipeLoader.getMaterials();
            itemsBefore = List.copyOf(EPPConfig.infCellItem);
            fluidsBefore = List.copyOf(EPPConfig.infCellFluid);
            check(materialsBefore.equals(InfinitySingularityRecipeLoader.parseAndCache()),
                    "Startup material snapshot differs from config; refusing to overwrite an inconsistent baseline");
            List<Item> itemOptions = new ArrayList<>(List.of(Items.DIAMOND, Items.EMERALD, Items.GOLD_INGOT,
                    Items.IRON_INGOT, Items.COPPER_INGOT, Items.REDSTONE, Items.APPLE, Items.BREAD));
            itemOptions.removeIf(itemsBefore::contains);
            check(itemOptions.size() >= 2, "Need two registered item materials absent from the original ExtendedAE lists");
            List<Fluid> fluidOptions = new ArrayList<>(ForgeRegistries.FLUIDS.getValues());
            fluidOptions.removeIf(fluid -> fluid == Fluids.EMPTY || fluidsBefore.contains(fluid));
            fluidOptions.sort(java.util.Comparator.comparing(fluid -> ForgeRegistries.FLUIDS.getKey(fluid).toString()));
            check(fluidOptions.size() >= 2, "Need two registered fluid materials absent from the original ExtendedAE lists");
            materialItemA = itemOptions.get(0);
            materialItemB = itemOptions.get(1);
            materialFluidA = fluidOptions.get(0);
            materialFluidB = fluidOptions.get(1);
            journal = new JsonObject();
            journal.addProperty("schema", 1);
            journal.addProperty("restored", false);
            journal.addProperty("gameDir", game.toString());
            journal.addProperty("recipePath", recipePath.toString());
            journal.addProperty("eppPath", eppPath.toString());
            journal.addProperty("recipeBytes", Base64.getEncoder().encodeToString(Files.readAllBytes(recipePath)));
            journal.addProperty("eppBytes", Base64.getEncoder().encodeToString(Files.readAllBytes(eppPath)));
            journal.add("materials", JSON.toJsonTree(materialsBefore));
            journal.add("items", itemIds(itemsBefore));
            journal.add("fluids", fluidIds(fluidsBefore));
            journal.add("eppTypes", JSON.toJsonTree(eppTypes.get()));
            writeJson(journalPath, journal);
            suspendWatcher();
            setMaterials(materialItemA, materialFluidA);
            startReload();
            next(Phase.CONFIG_INITIAL);
        }

        private void suspendWatcher() throws IOException {
            FileWatcher.defaultInstance().setWatch(eppWatchPath, () -> {});
            watcherSuspended = true;
        }

        private void runForgeWatcher() {
            check(!eppConfig.getSpec().isCorrecting(), "Config spec is busy correcting another file");
            ClassLoader previous = Thread.currentThread().getContextClassLoader();
            try {
                forgeWatcher.run();
            } finally {
                Thread.currentThread().setContextClassLoader(previous);
            }
        }

        private void replaceExtendedConfig() {
            List<String> replacement = new ArrayList<>(eppTypes.get());
            replacement.add(ForgeRegistries.ITEMS.getKey(materialItemA).toString());
            replacement.add(ForgeRegistries.FLUIDS.getKey(materialFluidA).toString());
            try (CommentedFileConfig file = CommentedFileConfig.builder(eppPath).sync().build()) {
                file.load();
                file.set(eppTypes.getPath(), replacement);
                file.save();
            }
            runForgeWatcher();
            check(EPPConfig.infCellItem != injectedItems && EPPConfig.infCellFluid != injectedFluids,
                    "Actual ExtendedAE ModConfigEvent handler did not replace both list identities");
            check(EPPConfig.infCellItem.contains(materialItemA) && EPPConfig.infCellFluid.contains(materialFluidA),
                    "Actual ExtendedAE handler did not read user-base fixture materials from its config file");
            check(eppTypes.get().equals(replacement), "Real Forge config reload did not accept the file contents");
        }

        private void setMaterials(Item item, Fluid fluid) throws Exception {
            JsonObject fixture = new JsonObject();
            JsonArray recipes = new JsonArray();
            List<Entry> expected = new ArrayList<>();
            if (item != null) {
                String id = ForgeRegistries.ITEMS.getKey(item).toString();
                addMaterial(recipes, id, 1, false);
                expected.add(new Entry(id, 1, false));
            }
            if (fluid != null) {
                String id = ForgeRegistries.FLUIDS.getKey(fluid).toString();
                addMaterial(recipes, id, 1000, true);
                expected.add(new Entry(id, 1000, true));
            }
            fixture.add("recipes", recipes);
            writeBytes(recipePath, JSON.toJson(fixture).getBytes(StandardCharsets.UTF_8));
            registerMaterials(expected);
        }

        private void addMaterial(JsonArray recipes, String id, int count, boolean fluid) {
            JsonObject entry = new JsonObject();
            entry.addProperty("id", id);
            entry.addProperty("count", count);
            entry.addProperty("type", fluid ? "fluid" : "item");
            recipes.add(entry);
        }

        private void registerMaterials(List<Entry> expected) {
            List<ResourceLocation> recipes = new ArrayList<>();
            InfinitySingularityRecipeLoader.registerRecipes(recipe -> {
                check(recipe.getId().getNamespace().equals("gtl_enhancedcore"), "Production builder emitted an unexpected recipe namespace");
                check(recipe.serializeRecipe().has("type"), "Production recipe serialization omitted its serializer");
                recipes.add(recipe.getId());
            });
            check(InfinitySingularityRecipeLoader.getMaterials().equals(expected), "Real config loader material snapshot differs from expected registered materials");
            check(recipes.size() == expected.size(), "Production builder did not emit every fixture recipe");
        }

        private void verifyReplacement(boolean wantedB) {
            check(injectedItems.equals(injectedItemSnapshot) && injectedFluids.equals(injectedFluidSnapshot),
                    "Injector mutated a detached old ExtendedAE list");
            check(EPPConfig.infCellItem != injectedItems && EPPConfig.infCellFluid != injectedFluids, "Injector rebound the old list");
            check(java.util.Collections.frequency(EPPConfig.infCellItem, materialItemA) == 1,
                    "Injector deleted/duplicated the new user-owned item material");
            check(java.util.Collections.frequency(EPPConfig.infCellFluid, materialFluidA) == 1,
                    "Injector deleted/duplicated the new user-owned fluid material");
            check(java.util.Collections.frequency(EPPConfig.infCellItem, materialItemB) == (wantedB ? 1 : 0),
                    "Injector did not add/withdraw exactly its own item contribution");
            check(java.util.Collections.frequency(EPPConfig.infCellFluid, materialFluidB) == (wantedB ? 1 : 0),
                    "Injector did not add/withdraw exactly its own fluid contribution");
            check(InfinitySingularityRecipeLoader.getMaterials().size() == (wantedB ? 2 : 0), "Reload changed the tested material snapshot");
        }

        private void restoreConfiguration() throws Exception {
            check(journal.get("schema").getAsInt() == 1 && game.toString().equals(journal.get("gameDir").getAsString()),
                    "Config backup belongs to another instance/schema");
            check(recipePath.toString().equals(journal.get("recipePath").getAsString())
                            && eppPath.toString().equals(journal.get("eppPath").getAsString()),
                    "Config backup paths do not match the real registered config files");
            writeBytes(recipePath, Base64.getDecoder().decode(journal.get("recipeBytes").getAsString()));
            writeBytes(eppPath, Base64.getDecoder().decode(journal.get("eppBytes").getAsString()));
            runForgeWatcher();
            registerMaterials(savedMaterials());
            startReload();
        }

        private List<Entry> savedMaterials() {
            List<Entry> result = new ArrayList<>();
            for (var element : journal.getAsJsonArray("materials")) {
                JsonObject entry = element.getAsJsonObject();
                result.add(new Entry(entry.get("id").getAsString(), entry.get("count").getAsInt(), entry.get("fluid").getAsBoolean()));
            }
            return result;
        }

        private void verifyRestoration() throws IOException {
            check(Arrays.equals(Files.readAllBytes(recipePath), Base64.getDecoder().decode(journal.get("recipeBytes").getAsString())),
                    "Recipe config bytes were not restored exactly");
            check(Arrays.equals(Files.readAllBytes(eppPath), Base64.getDecoder().decode(journal.get("eppBytes").getAsString())),
                    "ExtendedAE config bytes were not restored exactly");
            check(InfinitySingularityRecipeLoader.getMaterials().equals(savedMaterials()), "Original live material snapshot was not restored");
            check(counts(itemIds(EPPConfig.infCellItem)).equals(counts(journal.getAsJsonArray("items"))), "Original ExtendedAE live item entries were not restored");
            check(counts(fluidIds(EPPConfig.infCellFluid)).equals(counts(journal.getAsJsonArray("fluids"))), "Original ExtendedAE live fluid entries were not restored");
            check(JSON.toJsonTree(eppTypes.get()).equals(journal.get("eppTypes")), "Original ExtendedAE config value was not restored");
            restoreWatcher();
            journal.addProperty("restored", true);
            writeJson(journalPath, journal);
        }

        private JsonArray itemIds(List<Item> items) {
            JsonArray result = new JsonArray();
            items.forEach(item -> result.add(ForgeRegistries.ITEMS.getKey(item).toString()));
            return result;
        }

        private JsonArray fluidIds(List<Fluid> fluids) {
            JsonArray result = new JsonArray();
            fluids.forEach(fluid -> result.add(ForgeRegistries.FLUIDS.getKey(fluid).toString()));
            return result;
        }

        private Map<String, Integer> counts(JsonArray values) {
            Map<String, Integer> result = new HashMap<>();
            values.forEach(value -> result.merge(value.getAsString(), 1, Integer::sum));
            return result;
        }

        private void startReload() {
            check(reload == null, "Another resource reload is still outstanding");
            reload = server.reloadResources(List.copyOf(server.getPackRepository().getSelectedIds()));
        }

        private boolean reloadFinished() {
            if (!reload.isDone()) return false;
            CompletableFuture<Void> finished = reload;
            reload = null;
            finished.getNow(null);
            return true;
        }

        private void next(Phase next) throws IOException {
            phase = next;
            phaseTicks = 0;
            phaseStarted = System.nanoTime();
            writeJson(resultPath, JsonParser.parseString(result()).getAsJsonObject());
        }

        private void passed(String name) {
            coverage.addProperty(name, true);
            completed.add(name);
        }

        private void releaseChunk() {
            if (!forceHeld) return;
            world.setChunkForced(MACHINE_POS.getX() >> 4, MACHINE_POS.getZ() >> 4, forcedBefore);
            forceHeld = false;
        }

        private void restoreWatcher() throws IOException {
            if (!watcherSuspended) return;
            FileWatcher.defaultInstance().setWatch(eppWatchPath, registeredWatcher);
            watcherSuspended = false;
        }

        private void fail(Throwable error) throws Exception {
            if (terminal) return;
            String detail = error.toString();
            for (Throwable cause = error.getCause(); cause != null && cause != error; cause = cause.getCause()) {
                detail += " <- " + cause;
                error = cause;
            }
            boolean failedDuringRestore = phase == Phase.FAILURE_WAIT || phase == Phase.FAILURE_RESTORE
                    || phase == Phase.RECOVERY_RESTORE || phase == Phase.CONFIG_RESTORE;
            if (failure.isEmpty()) failure = detail;
            else restorationError = detail;
            if (journal != null && !journal.get("restored").getAsBoolean()) {
                if (!failedDuringRestore) {
                    if (reload != null && !reload.isDone()) {
                        next(Phase.FAILURE_WAIT);
                        return;
                    }
                    reload = null;
                    try {
                        restoreConfiguration();
                        next(Phase.FAILURE_RESTORE);
                        return;
                    } catch (Exception | AssertionError restoreFailure) {
                        restorationError = restoreFailure.toString();
                    }
                }
                try {
                    check(game.toString().equals(journal.get("gameDir").getAsString())
                                    && recipePath.toString().equals(journal.get("recipePath").getAsString())
                                    && eppPath.toString().equals(journal.get("eppPath").getAsString()),
                            "Emergency restore refused mismatched backup paths");
                    writeBytes(recipePath, Base64.getDecoder().decode(journal.get("recipeBytes").getAsString()));
                    writeBytes(eppPath, Base64.getDecoder().decode(journal.get("eppBytes").getAsString()));
                    if (reload == null || reload.isDone()) {
                        reload = null;
                        runForgeWatcher();
                        registerMaterials(savedMaterials());
                        InfinityCellConfigInjector.inject();
                        verifyRestoration();
                    }
                } catch (Exception | AssertionError restoreFailure) {
                    restorationError += " emergency_restore=" + restoreFailure;
                }
                finish(journal.get("restored").getAsBoolean() ? "FAILED" : "FAILED_RESTORE_REQUIRED");
                return;
            }
            finish("FAILED");
        }

        private void finish(String finalStatus) throws IOException {
            status = finalStatus;
            try {
                restoreWatcher();
            } catch (IOException error) {
                restorationError += " watcher_restore=" + error;
                status = "FAILED_RESTORE_REQUIRED";
            }
            releaseChunk();
            phase = Phase.FINISHED;
            terminal = true;
            try {
                writeJson(resultPath, JsonParser.parseString(result()).getAsJsonObject());
            } catch (IOException error) {
                status = "FAILED_OUTPUT";
                failure += " result_write=" + error;
            } finally {
                server = null;
                world = null;
                machine = null;
                donor = null;
                chest = null;
                slots = null;
                source = null;
                oldFirst = null;
                oldLast = null;
                reload = null;
                eppConfig = null;
                eppTypes = null;
                registeredWatcher = null;
                forgeWatcher = null;
            }
        }

        private String result() {
            JsonObject result = new JsonObject();
            result.addProperty("schema", 1);
            result.addProperty("marker", "MODULE_STABILITY");
            result.addProperty("status", status);
            result.addProperty("phase", phase.name());
            result.addProperty("terminal", terminal);
            result.addProperty("passed", "COMPLETE".equals(status));
            result.addProperty("realForgeRuntime", true);
            result.addProperty("realRestart", restart);
            result.addProperty("bootId", BOOT_ID);
            result.addProperty("gameDir", game.toString());
            result.addProperty("worldPath", worldPath.toString());
            result.addProperty("fixture", "6404,224,6404;minecraft:overworld");
            result.addProperty("assertions", assertions);
            result.addProperty("freshAssertions", restart ? freshAssertions : assertions);
            result.addProperty("totalAssertions", assertions + (restart ? freshAssertions : 0));
            result.addProperty("productionFillCallsThisBoot", pullCalls);
            result.addProperty("slotsFilledByProductionThisBoot", filled);
            result.addProperty("configRestore", journal == null ? "NOT_TOUCHED"
                    : journal.get("restored").getAsBoolean() ? "RESTORED" : "BACKUP_PENDING");
            result.addProperty("configBackup", journalPath.toString());
            result.addProperty("failure", failure);
            result.addProperty("restorationError", restorationError);
            result.addProperty("materialBoundary", "Explicit production registerRecipes captures real FinishedRecipe output; real server.reloadResources exercises the registered listener. Automatic recipe regeneration/WatchService scheduling is not asserted.");
            result.addProperty("fillBoundary", "Actual registered machine/grid/storage; private tryPullCells accelerated across ticks, automatic 20-tick trigger and client GUI rendering are not asserted.");
            result.addProperty("excluded", "Cell packs; multi-player UI; unplug/reconnect; chunk unload; rollback/crash atomicity; TPS benchmark; other machines");
            result.add("coverage", coverage.deepCopy());
            result.add("completed", completed.deepCopy());
            return JSON.toJson(result);
        }

        private Path checkedExistingFile(Path file, Path root) throws IOException {
            require(Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS), "Refusing missing/nonregular/symlink file: " + file);
            Path real = file.toRealPath();
            require(real.startsWith(root), "File escaped the isolated config root: " + real);
            return real;
        }

        private JsonObject readJson(Path file) throws IOException {
            checkedExistingFile(file, game);
            require(Files.size(file) <= 16 * 1024 * 1024, "Oversized audit checkpoint/backup");
            return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        }

        private void writeJson(Path file, JsonObject value) throws IOException {
            writeBytes(file, JSON.toJson(value).getBytes(StandardCharsets.UTF_8));
        }

        private void writeBytes(Path file, byte[] bytes) throws IOException {
            Path parent = file.getParent().toRealPath();
            require(parent.startsWith(game), "Write escaped the isolated instance: " + file);
            if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) checkedExistingFile(file, game);
            Path temporary = Files.createTempFile(parent, "module-stability-", ".tmp");
            try {
                Files.write(temporary, bytes);
                try {
                    Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException unsupported) {
                    Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
        }

        private void require(boolean condition, String message) throws IOException {
            if (!condition) throw new IOException(message);
        }

        private void check(boolean condition, String message) {
            assertions++;
            if (!condition) throw new AssertionError(message);
        }
    }
}
