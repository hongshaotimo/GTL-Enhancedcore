package com.gtl.enhancedcore.audit;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.IRecipeHandler;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableFluidTank;
import com.gregtechceu.gtceu.api.pattern.FactoryBlockPattern;
import com.gregtechceu.gtceu.api.pattern.Predicates;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.common.block.LampBlock;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import org.gtlcore.gtlcore.common.data.GTLMachines.GTAEMachines;
import com.gregtechceu.gtceu.common.machine.multiblock.part.FluidHatchPartMachine;
import com.gregtechceu.gtceu.integration.ae2.machine.feature.IGridConnectedMachine;
import com.gregtechceu.gtceu.integration.ae2.slot.ExportOnlyAEFluidList;
import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtl.enhancedcore.common.item.LampConfiguration;
import com.gtl.enhancedcore.common.machine.IndustrialSteamPlatformMachine;
import com.gtl.enhancedcore.common.structure.SpaceElevatorMaintenance;
import com.gtl.enhancedcore.integration.jade.SteamPlatformInfoProvider;
import com.gtl.enhancedcore.mixin.gtceu.BlockPatternAccessor;
import com.lowdragmc.lowdraglib.side.fluid.FluidStack;
import com.mojang.authlib.GameProfile;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.registries.ForgeRegistries;
import org.gtlcore.gtlcore.common.machine.multiblock.part.MEDualHatchStockPartMachine;
import snownee.jade.impl.BlockAccessorImpl;

/** Real registry, native ME extraction, full production elevator and steam structures. */
public final class SteamLampElevatorChecks {
    private static final List<Elevator> ELEVATORS = new ArrayList<>();
    private static final List<MetaMachine> HATCHES = new ArrayList<>();
    private static IndustrialSteamPlatformMachine steam;
    private static Field stored;
    private static Method pull;
    private static int checks;
    private record Elevator(MultiblockControllerMachine machine, List<BlockPos> casing, BlockPos energy) {}

    public static void setup(MinecraftServer server) throws Exception {
        var world = server.overworld();
        steam = (IndustrialSteamPlatformMachine) place(world,
                "gtl_enhancedcore:industrial_steam_platform", new BlockPos(0, 64, 10000), false);
        stored = IndustrialSteamPlatformMachine.class.getDeclaredField("steamStored");
        stored.setAccessible(true);
        pull = IndustrialSteamPlatformMachine.class.getDeclaredMethod("pullSteamFromHatches", long.class);
        pull.setAccessible(true);
        steam.setWorkingEnabled(false);
        place(world, "gtceu:space_elevator", new BlockPos(256, 0, 10000), true);
        place(world, "gtladditions:space_elevator_mkii", new BlockPos(512, 0, 10000), true);
        for (var definition : List.of(GTAEMachines.STOCKING_IMPORT_HATCH_ME,
                GTRegistries.MACHINES.get(new ResourceLocation("gtceu:me_dual_hatch_stock_part_machine")),
                GTRegistries.MACHINES.get(new ResourceLocation("gtceu:tag_filter_me_stock_hatch_part_machine")))) {
            var pos = new BlockPos(1024 + 16 * HATCHES.size(), 100, 10000);
            world.setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, true);
            world.setBlock(pos, definition.defaultBlockState(), 3);
            var hatch = MetaMachine.getMachine(world, pos);
            hatch.setFrontFacing(Direction.NORTH);
            world.setBlock(pos.south(), appeng.core.definitions.AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState(), 3);
            world.setBlock(pos.south().east(), appeng.core.definitions.AEBlocks.DRIVE.block().defaultBlockState(), 3);
            ((appeng.blockentity.storage.DriveBlockEntity) world.getBlockEntity(pos.south().east()))
                    .getInternalInventory().setItemDirect(0, appeng.core.definitions.AEItems.FLUID_CELL_64K.stack());
            HATCHES.add(hatch);
        }
        lamps(world);
        copyIsolation();
    }

    private static MultiblockControllerMachine place(ServerLevel world, String id, BlockPos base, boolean elevator) {
        var definition = (MultiblockMachineDefinition) GTRegistries.MACHINES.get(new ResourceLocation(id));
        var shape = definition.getMatchingShapes().getFirst().getBlocks();
        BlockPos core = null, energy = null;
        BlockState coreState = null;
        var casing = new ArrayList<BlockPos>();
        var casingBlock = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("gtlcore:space_elevator_mechanical_casing"));
        int solids = 0;
        for (int x = 0; x < shape.length; x++) for (int y = 0; y < shape[x].length; y++)
            for (int z = 0; z < shape[x][y].length; z++) {
                var info = shape[x][y][z];
                if (info == null || info.getBlockState().isAir()) continue;
                var pos = base.offset(x, y, z);
                check(world.isInWorldBounds(pos), "Shape exceeds world height " + id);
                world.setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, true);
                var state = info.getBlockState();
                if (state.is(definition.getBlock())) { core = pos; coreState = state; continue; }
                check(!elevator || !PartAbility.MAINTENANCE.isApplicable(state.getBlock()), "Preview still requires maintenance");
                if (state.is(casingBlock)) casing.add(pos);
                if (PartAbility.INPUT_ENERGY.isApplicable(state.getBlock())) energy = pos;
                world.setBlock(pos, state, 2 | 16);
                solids++;
            }
        check(core != null, "No controller in preview " + id);
        world.setBlock(core, coreState, 2 | 16);
        var machine = (MultiblockControllerMachine) MetaMachine.getMachine(world, core);
        if (elevator) {
            check(energy != null && casing.size() > 2, "Incomplete elevator fixture");
            ELEVATORS.add(new Elevator(machine, casing, energy));
        }
        log("placed=" + id + " solids=" + solids + " size=" + shape.length + "x" + shape[0].length + "x" + shape[0][0].length);
        return machine;
    }

    public static boolean ready() {
        return steam.isFormed() && ELEVATORS.stream().allMatch(f -> f.machine().isFormed())
                && HATCHES.stream().allMatch(h -> ((IGridConnectedMachine) h).getMainNode().isOnline());
    }

    public static void status() {
        log("waiting steam=" + steam.isFormed() + " elevators=" + ELEVATORS.stream()
                .map(f -> f.machine().getDefinition().getId() + ":" + f.machine().isFormed()).toList()
                + " networks=" + HATCHES.stream().map(h -> h.getDefinition().getId() + ":"
                + ((IGridConnectedMachine) h).getMainNode().isOnline()).toList());
    }

    public static void run(MinecraftServer server) throws Exception {
        check(ready(), "Fixtures not ready");
        for (var f : ELEVATORS) elevator(f);
        var world = server.overworld();
        // Attach native handler instances explicitly so each extraction route is independently exercised.
        var originalIn = steam.getCapabilitiesProxy().get(IO.IN, FluidRecipeCapability.CAP);
        var originalBoth = steam.getCapabilitiesProxy().get(IO.BOTH, FluidRecipeCapability.CAP);
        try {
            var tank = new NotifiableFluidTank(steam, 1, 500000, IO.IN);
            bind(tank);
            for (long amount : new long[]{1, 499, 999, 1000, 1001, 50000, 100000, 150000}) {
                stored.setLong(steam, 0);
                tank.setFluidInTank(0, FluidStack.create(GTMaterials.Steam.getFluid(), amount));
                long taken = (long) pull.invoke(steam, 100000L);
                check(taken == Math.min(100000, amount) && steam.getSteamStoredMb() == taken, "Ordinary tank credit " + amount);
                check(tank.getFluidInTank(0).getAmount() == amount - taken, "Ordinary tank debit " + amount);
            }
            tank.setFluidInTank(0, FluidStack.create(net.minecraft.world.level.material.Fluids.WATER, 500));
            stored.setLong(steam, 0);
            check((long) pull.invoke(steam, 100000L) == 0 && tank.getFluidInTank(0).getAmount() == 500, "Non-steam drained");
            for (var hatch : HATCHES) network(hatch);
            bind(tank);
            tank.setFluidInTank(0, FluidStack.empty());
            stored.setLong(steam, 500);
            check(!steam.beforeWorking(null) && steam.getSteamStoredMb() == 500, "Sub-bucket steam lost on failed admission");
            var lines = new ArrayList<Component>();
            steam.addDisplayText(lines);
            check(lines.stream().map(Component.Serializer::toJson).anyMatch(s -> s.contains("steam_stored") && s.contains("500")),
                    "GUI rounded away partial bucket");
            var player = FakePlayerFactory.get(world, new GameProfile(UUID.randomUUID(), "steam_audit"));
            var pos = steam.getPos();
            var data = new CompoundTag();
            var accessor = new BlockAccessorImpl.Builder().level(world).player(player).serverData(data)
                    .serverConnected(true).showDetails(true).blockState(world.getBlockState(pos))
                    .blockEntity(() -> world.getBlockEntity(pos))
                    .hit(new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false)).build();
            new SteamPlatformInfoProvider().appendServerData(data, accessor);
            check(data.getLong("gtlEnhancedcoreSteamStored") == 500, "Jade rounded away partial bucket");
            var be = world.getBlockEntity(pos);
            var saved = be.saveWithFullMetadata();
            stored.setLong(steam, 0);
            be.load(saved);
            check(steam.getSteamStoredMb() == 500, "Steam lost in NBT save/load");
            log("steam_native_accounting_gui_jade_nbt=OK");
        } finally {
            steam.getCapabilitiesProxy().remove(IO.IN, FluidRecipeCapability.CAP);
            steam.getCapabilitiesProxy().remove(IO.BOTH, FluidRecipeCapability.CAP);
            if (originalIn != null) steam.getCapabilitiesProxy().put(IO.IN, FluidRecipeCapability.CAP, originalIn);
            if (originalBoth != null) steam.getCapabilitiesProxy().put(IO.BOTH, FluidRecipeCapability.CAP, originalBoth);
        }
        log("server_checks=PASS checks=" + checks);
    }

    private static void bind(IRecipeHandler<?> handler) {
        steam.getCapabilitiesProxy().put(IO.IN, FluidRecipeCapability.CAP, new ArrayList<>(List.of(handler)));
        steam.getCapabilitiesProxy().put(IO.BOTH, FluidRecipeCapability.CAP, new ArrayList<>(List.of(handler)));
    }

    private static void network(MetaMachine hatch) throws Exception {
        var grid = ((IGridConnectedMachine) hatch).getMainNode().getGrid();
        check(grid != null, "ME grid missing");
        MEStorage storage = grid.getStorageService().getInventory();
        ExportOnlyAEFluidList list = hatch instanceof FluidHatchPartMachine fluid
                ? (ExportOnlyAEFluidList) fluid.tank : ((MEDualHatchStockPartMachine) hatch).aeFluidHandler;
        var slot = list.getInventory()[0];
        var key = AEFluidKey.of(GTMaterials.Steam.getFluid());
        var source = IActionSource.empty();
        bind(list);
        for (long amount : new long[]{1, 499, 999, 1000, 1001, 50000, 100000, 150000}) {
            storage.extract(key, Long.MAX_VALUE, Actionable.MODULATE, source);
            long inserted = storage.insert(key, amount, Actionable.MODULATE, source);
            check(inserted == amount, "Fluid cell insert " + hatch.getDefinition().getId() + ": " + inserted + "/" + amount);
            slot.setConfig(new GenericStack(key, 1));
            // A stale oversized snapshot must not cause fictitious credits or a lost partial extraction.
            slot.setStock(new GenericStack(key, Math.max(amount, 100000)));
            stored.setLong(steam, 0);
            var simulated = slot.drain(FluidStack.create(GTMaterials.Steam.getFluid(), 100000), true);
            check(simulated.getAmount() == Math.min(amount, 100000), "Native simulation failed " + hatch.getDefinition().getId());
            check(storage.getAvailableStacks().get(key) == amount, "Simulation consumed steam");
            long taken = (long) pull.invoke(steam, 100000L);
            check(taken == Math.min(amount, 100000) && steam.getSteamStoredMb() == taken, "ME partial credit " + hatch.getDefinition().getId() + " amount=" + amount + " took=" + taken);
            check(storage.getAvailableStacks().get(key) == amount - taken, "ME debit mismatch");
        }
        stored.setLong(steam, 99999);
        long before = storage.getAvailableStacks().get(key);
        check((long) pull.invoke(steam, 100000L) == 1 && steam.getSteamStoredMb() == 100000, "Capacity overfilled");
        check(storage.getAvailableStacks().get(key) == before - 1, "Near-full debit mismatch");
        check((long) pull.invoke(steam, 100000L) == 0, "Full buffer drained");
        stored.setLong(steam, 0);
        // Both implementations expose the work switch through GTL's controllable interface.
        var setter = hatch.getClass().getMethod("setWorkingEnabled", boolean.class);
        setter.invoke(hatch, false);
        before = storage.getAvailableStacks().get(key);
        check((long) pull.invoke(steam, 100000L) == 0 && storage.getAvailableStacks().get(key) == before, "Disabled hatch drained");
        setter.invoke(hatch, true);
        var water = AEFluidKey.of(net.minecraft.world.level.material.Fluids.WATER);
        check(storage.insert(water, 500, Actionable.MODULATE, source) == 500, "Water fixture insert");
        slot.setConfig(new GenericStack(water, 1));
        slot.setStock(new GenericStack(key, 500));
        check((long) pull.invoke(steam, 100000L) == 0 && steam.getSteamStoredMb() == 0
                && storage.getAvailableStacks().get(water) == 500, "Changed filter drained another fluid as steam");
        slot.setConfig(new GenericStack(key, 1));
        log("native_stocking=" + hatch.getDefinition().getId() + " partial_stale_simulate_capacity_disabled=OK");
    }

    private static void elevator(Elevator f) {
        var m = f.machine();
        var world = m.getLevel();
        var lock = m.getPatternLock();
        lock.lock();
        try {
            check(m.isFormed(), "Elevator failed natural formation");
            check(m.getPattern().checkPatternAt(m.getMultiblockState(), true), "Could not capture elevator predicates");
            // The production elevator pattern intentionally removes maintenance-hatch
            // candidates.  Use real mechanical-casing positions to prove that a hatch
            // cannot be installed after the maintenance requirement was removed.
            var positions = f.casing().stream().limit(2).toList();
            check(positions.size() == 2, "No elevator casing test positions");
            var first = positions.getFirst();
            var second = positions.getLast();
            var casing = world.getBlockState(first);
            var maintenance = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("gtceu:auto_configuration_maintenance_hatch")).defaultBlockState();
            m.onStructureInvalid();
            world.setBlock(first, maintenance, 2 | 16);
            check(!m.checkPattern(), "Maintenance hatch accepted");
            world.setBlock(second, maintenance, 2 | 16);
            check(!m.checkPattern(), "Duplicate maintenance accepted");
            world.setBlock(first, casing, 2 | 16);
            world.setBlock(second, casing, 2 | 16);
            check(m.checkPattern(), "No-maintenance rejected");
            var energy = world.getBlockState(f.energy());
            world.setBlock(f.energy(), casing, 2 | 16);
            check(!m.checkPattern(), "Energy requirement accidentally removed");
            world.setBlock(f.energy(), energy, 2 | 16);
            check(m.checkPattern(), "Restored elevator rejected");
            m.onStructureFormed();
            log("elevator=" + m.getDefinition().getId() + " zero_maintenance_required_one_two_rejected_energy_requirement=OK");
        } finally {
            lock.unlock();
        }
    }

    private static void copyIsolation() {
        var rule = Predicates.blocks(Blocks.STONE).or(Predicates.abilities(PartAbility.MAINTENANCE).setExactLimit(1));
        var original = FactoryBlockPattern.start().aisle("S").where('S', Predicates.controller(rule)).build();
        var changed = SpaceElevatorMaintenance.forbidden(original);
        var before = ((BlockPatternAccessor) original).gtlEnhancedcore$getBlockMatches()[0][0][0].limited.getFirst();
        var after = ((BlockPatternAccessor) changed).gtlEnhancedcore$getBlockMatches()[0][0][0];
        check(before.minCount == 1 && before.maxCount == 1 && after.limited.isEmpty(), "Mutated shared upstream predicate");
    }

    private static void lamps(ServerLevel world) {
        int colors = 0;
        for (var block : ForgeRegistries.BLOCKS) {
            if (!(block instanceof LampBlock lamp)) continue;
            colors++;
            Set<String> identities = new HashSet<>();
            for (int variant = 0; variant < 8; variant++) {
                var expected = lamp.getStackFromIndex(variant);
                var state = lamp.defaultBlockState().setValue(LampBlock.INVERTED, LampBlock.isInverted(expected.getTag()))
                        .setValue(LampBlock.LIGHT, LampBlock.isLightEnabled(expected.getTag()))
                        .setValue(LampBlock.BLOOM, LampBlock.isBloomEnabled(expected.getTag()));
                var clone = ((Block) lamp).getCloneItemStack(world, BlockPos.ZERO, state);
                check(ItemStack.isSameItemSameTags(expected, clone), "Legacy material/hover picker lost NBT " + variant);
                check(identities.add(LampConfiguration.subtype(expected)), "JEI subtype collision");
                var powered = lamp.getCloneItemStack(world, BlockPos.ZERO, state.setValue(LampBlock.POWERED, true));
                check(ItemStack.isSameItemSameTags(clone, powered), "Redstone power became item identity");
                pattern(world, clone);
            }
            var ordinary = new ItemStack(lamp);
            check(LampConfiguration.subtype(ordinary).equals(LampConfiguration.subtype(lamp.getStackFromIndex(4))),
                    "Untagged normal lamp misclassified");
            ordinary.setTag(new CompoundTag());
            check(LampConfiguration.subtype(ordinary).equals("3"), "Empty NBT default mismatch");
            ordinary.getOrCreateTag().putBoolean("inverted", true);
            check(LampConfiguration.subtype(ordinary).equals("4"), "Partial NBT default mismatch");
        }
        check(colors == 32, "Lamp registry coverage changed");
        log("lamp_colors_borders=32 combinations=256 legacy_pick_subtypes_AE_pattern_roundtrip=OK");
    }

    static void pattern(net.minecraft.world.level.Level world, ItemStack stack) {
        var key = AEItemKey.of(stack);
        var encoded = PatternDetailsHelper.encodeProcessingPattern(new GenericStack[]{new GenericStack(key, 204)},
                new GenericStack[]{new GenericStack(AEItemKey.of(Items.DIAMOND), 1)});
        var decoded = PatternDetailsHelper.decodePattern(encoded, world);
        check(decoded != null && decoded.getInputs().length == 1, "Pattern decode failed");
        var input = decoded.getInputs()[0];
        check(input.getPossibleInputs()[0].what().equals(key)
                && input.getMultiplier() * input.getPossibleInputs()[0].amount() == 204, "AE pattern lost lamp NBT/count");
    }

    private static void check(boolean valid, String reason) {
        if (!valid) throw new IllegalStateException(reason);
        checks++;
    }
    private static void log(String message) { GTLEnhancedcore.LOGGER.info("[STEAM_LAMP_AUDIT] {}", message); }
}
