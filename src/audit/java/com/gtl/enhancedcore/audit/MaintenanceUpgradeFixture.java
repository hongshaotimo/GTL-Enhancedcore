package com.gtl.enhancedcore.audit;

import appeng.api.stacks.AEItemKey;
import com.google.gson.JsonParser;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.logic.OCParams;
import com.gregtechceu.gtceu.api.recipe.logic.OCResult;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ParallelHatchPartMachine;
import com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder;
import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtl.enhancedcore.common.recipe.iv.*;
import com.gtl.enhancedcore.common.structure.GtlMegastructurePatterns;
import com.mojang.authlib.GameProfile;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.registries.ForgeRegistries;
import org.gtlcore.gtlcore.api.machine.trait.IRecipeCapabilityMachine;
import org.gtlcore.gtlcore.api.recipe.IGTRecipe;
import org.gtlcore.gtlcore.common.machine.multiblock.part.maintenance.AutoConfigurationMaintenanceHatchPartMachine;

/** Full, unchanged production geometry. Only source diamond-ore markers receive test parts. */
public final class MaintenanceUpgradeFixture {
    private static final List<Fixture> FIXTURES = new ArrayList<>();
    private static int checks;
    private static int formedTicks;
    private record Fixture(WorkableElectricMultiblockMachine machine, List<BlockPos> ports,
                           Block fallback, BlockPos maintenance, BlockPos body) {}

    private MaintenanceUpgradeFixture() {}

    public static void setup(MinecraftServer server, boolean saved) throws Exception {
        place(server.overworld(), "qft", 0, saved);
        place(server.overworld(), "gravitation_shockburst", 320, saved);
    }

    private static Block block(String id) {
        var key = new ResourceLocation(id);
        check(ForgeRegistries.BLOCKS.containsKey(key), "Missing block " + id);
        return ForgeRegistries.BLOCKS.getValue(key);
    }

    private static void place(ServerLevel world, String name, int offset, boolean saved) throws Exception {
        var definition = (MultiblockMachineDefinition) GTRegistries.MACHINES.get(new ResourceLocation("gtceu", name));
        var shape = definition.getMatchingShapes().getFirst().getBlocks();
        com.google.gson.JsonObject spec;
        try (var in = GtlMegastructurePatterns.class.getResourceAsStream("/data/gtl_enhancedcore/structures/gtl/upgrades.json")) {
            spec = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject(name);
        }
        CompoundTag source;
        try (var in = MaintenanceUpgradeFixture.class.getResourceAsStream("/gtl-upgrade-fixtures/" + name + ".schem")) {
            source = NbtIo.readCompressed(in);
        }
        if (source.contains("Schematic")) source = source.getCompound("Schematic");
        int w = source.getShort("Width"), h = source.getShort("Height"), d = source.getShort("Length");
        check(shape.length == w && shape[0].length == h && shape[0][0].length == d, "Preview/source bounds");
        int marker = source.getCompound("Palette").getInt("minecraft:diamond_ore");
        byte[] data = source.getByteArray("BlockData");
        int cursor = 0;
        Map<Long, Integer> panel = new HashMap<>();
        List<BlockPos> ports = new ArrayList<>();
        for (int i = 0; i < w * h * d; i++) {
            int value = 0, shift = 0, part;
            do {
                part = data[cursor++] & 255;
                value |= (part & 127) << shift;
                shift += 7;
            } while ((part & 128) != 0);
            if (value != marker) continue;
            int x = i % w, y = i / (w * d), z = i / w % d;
            panel.put(BlockPos.asLong(x, y, z), ports.size());
            ports.add(new BlockPos(offset + x, 64 + y, 8192 + z));
        }
        check(ports.size() == 24 && cursor == data.length, "Exact source hatch panel");
        String[] parts = name.equals("qft")
                ? new String[]{"gtceu:iv_input_bus", "gtceu:zpm_energy_input_hatch",
                        "gtceu:max_parallel_hatch", "gtceu:iv_output_bus", "gtceu:auto_configuration_maintenance_hatch"}
                : new String[]{"gtceu:zpm_256a_laser_target_hatch", "gtceu:max_parallel_hatch",
                        "gtceu:iv_input_bus", "gtceu:iv_output_bus", "gtceu:auto_configuration_maintenance_hatch"};
        var fallback = block(spec.get("hatchFallback").getAsString());
        for (int x = 0; x < w; x += 16) for (int z = 0; z < d; z += 16) {
            world.setChunkForced((offset + x) >> 4, (8192 + z) >> 4, true);
            world.getChunk((offset + x) >> 4, (8192 + z) >> 4);
        }
        var core = spec.getAsJsonArray("sourceController");
        var anchor = new BlockPos(offset + core.get(0).getAsInt(), 64 + core.get(1).getAsInt(), 8192 + core.get(2).getAsInt());
        BlockPos body = null;
        int solids = 0;
        for (int x = 0; x < w; x++) for (int y = 0; y < h; y++) for (int z = 0; z < d; z++) {
            if (shape[x][y][z] == null) continue;
            var state = shape[x][y][z].getBlockState();
            if (state.isAir()) continue;
            var port = panel.get(BlockPos.asLong(x, y, z));
            if (port != null) state = (port < parts.length ? block(parts[port]) : fallback).defaultBlockState();
            var pos = new BlockPos(offset + x, 64 + y, 8192 + z);
            if (pos.equals(anchor)) continue;
            if (!saved) world.setBlock(pos, state, 2 | 16);
            if (body == null && port == null && state.is(fallback)) body = pos;
            solids++;
        }
        if (!saved) world.setBlock(anchor, definition.defaultBlockState(), 2 | 16);
        var machine = (WorkableElectricMultiblockMachine) MetaMachine.getMachine(world, anchor);
        check(machine != null && body != null, "Full fixture/controller missing");
        if (!saved) machine.setWorkingEnabled(false);
        FIXTURES.add(new Fixture(machine, ports, fallback, ports.get(parts.length - 1), body));
        log("placed " + name + " solids=" + solids + " size=" + w + "x" + h + "x" + d + " restart=" + saved);
    }

    public static boolean ready() {
        if (FIXTURES.stream().anyMatch(f -> !f.machine().isFormed())) {
            formedTicks = 0;
            return false;
        }
        // GTL defers recipe-part subscriptions until a later server task after formation.
        if (++formedTicks == 1 || formedTicks == 20) {
            for (var f : FIXTURES) log("formation_tick=" + formedTicks + " " + bindingDetails(f));
        }
        return formedTicks >= 20;
    }

    private static String bindingDetails(Fixture f) {
        var m = f.machine();
        var actual = ((IRecipeCapabilityMachine) m).getMaintenanceMachine();
        return m.getDefinition().getId() + " expected=" + f.maintenance() + "/" + maintenance(f)
                + " actual=" + actual + " parts=" + m.getParts().stream()
                .map(p -> p.getClass().getSimpleName() + "@" + p.self().getPos()).toList();
    }

    private static AutoConfigurationMaintenanceHatchPartMachine maintenance(Fixture fixture) {
        return (AutoConfigurationMaintenanceHatchPartMachine) MetaMachine.getMachine(
                fixture.machine().getLevel(), fixture.maintenance());
    }

    public static void structureChecks() {
        for (var f : FIXTURES) {
            var m = f.machine();
            var world = m.getLevel();
            var lock = m.getPatternLock();
            lock.lock();
            try {
                check(m.isFormed(), "Did not form naturally");
                check(maintenance(f) != null
                        && ((IRecipeCapabilityMachine) m).getMaintenanceMachine() == maintenance(f),
                        "Maintenance not bound: " + bindingDetails(f));
                var state = world.getBlockState(f.maintenance());
                var body = world.getBlockState(f.body());
                m.onStructureInvalid();
                world.setBlock(f.maintenance(), f.fallback().defaultBlockState(), 2 | 16);
                check(m.checkPattern(), "Optional maintenance became mandatory");
                world.setBlock(f.maintenance(), state, 2 | 16);
                var spare = f.ports().getLast();
                world.setBlock(spare, state, 2 | 16);
                check(!m.checkPattern(), "Second maintenance hatch accepted");
                world.setBlock(spare, f.fallback().defaultBlockState(), 2 | 16);
                world.setBlock(f.body(), state, 2 | 16);
                check(!m.checkPattern(), "Maintenance accepted outside the panel");
                world.setBlock(f.body(), body, 2 | 16);
                check(m.checkPattern(), "Restored fixture rejected");
                m.onStructureFormed();
                log("optional_maintenance_limits=OK " + m.getDefinition().getId());
            } finally {
                lock.unlock();
            }
        }
    }

    public static void rebindChecks() {
        for (var f : FIXTURES) {
            check(f.machine().isFormed() && maintenance(f) != null
                    && ((IRecipeCapabilityMachine) f.machine()).getMaintenanceMachine() == maintenance(f),
                    "Restored maintenance not bound: " + bindingDetails(f));
            log("maintenance_rebind=OK " + f.machine().getDefinition().getId());
        }
    }

    private static GTRecipe raw(WorkableElectricMultiblockMachine machine) {
        return GTRecipeBuilder.of(new ResourceLocation("gtl_enhancedcore", "maintenance_direct"), machine.getRecipeType())
                .inputItems(Items.NETHER_STAR).outputItems(Items.DIAMOND).EUt(8).duration(10_000_000).buildRawRecipe();
    }

    private static IvNativeMatchContext context(WorkableElectricMultiblockMachine machine, long stock, long capacity) {
        return new IvNativeMatchContext(machine, Map.of(AEItemKey.of(Items.NETHER_STAR), stock), Map.of(), stock, capacity);
    }

    public static void modifierChecks() {
        for (var fixture : FIXTURES) {
            var m = fixture.machine();
            check(!IvMachineScope.nativeTarget(m), "Machine accidentally gained isolated mode");
            m.getParts().stream().filter(ParallelHatchPartMachine.class::isInstance)
                    .map(ParallelHatchPartMachine.class::cast).forEach(p -> p.setCurrentParallel(1));
            var hatch = maintenance(fixture);
            hatch.setDurationMultiplier(1);
            var probe = raw(m);
            var slow = context(m, 1000, 1).simulate(() -> m.doModifyRecipe(probe.copy(), new OCParams(), new OCResult()));
            hatch.setDurationMultiplier(0.2f);
            var fast = context(m, 1000, 1).simulate(() -> m.doModifyRecipe(probe.copy(), new OCParams(), new OCResult()));
            check(slow != null && fast != null && fast.duration < slow.duration, "Maintenance speed did not apply: " + m.getDefinition().getId());
            check(probe.duration == 10_000_000, "Modified registered recipe");
            check(IGTRecipe.of(slow).getRealParallels() == 1 && IGTRecipe.of(fast).getRealParallels() == 1,
                    "Maintenance changed native parallel");
            log("maintenance_speed=OK " + m.getDefinition().getId() + " base=" + slow.duration + " adjusted=" + fast.duration);
        }
    }

    public static void displayChecks() {
        for (var f : FIXTURES) {
            var m = f.machine();
            var lines = new ArrayList<Component>();
            m.getDefinition().getTooltipBuilder().accept(m.getDefinition().asStack(), lines);
            check(lines.stream().anyMatch(c -> Component.Serializer.toJson(c).contains("optional_maintenance")),
                    "Maintenance tooltip missing");
            check(lines.stream().noneMatch(c -> Component.Serializer.toJson(c).contains("tooltip.iv_native")),
                    "Unwanted isolated processing tooltip");
            var player = FakePlayerFactory.get((ServerLevel) m.getLevel(),
                    new GameProfile(UUID.randomUUID(), "maintenance_audit"));
            m.createUI(player);
            log("tooltip_and_open_screen=OK " + m.getDefinition().getId());
        }
    }

    public static void checkpoint() {
        for (var f : FIXTURES) {
            maintenance(f).setDurationMultiplier(0.4f);
            maintenance(f).markDirty();
            f.machine().setWorkingEnabled(false);
            f.machine().markDirty();
        }
    }

    public static void verifySaved() {
        for (var f : FIXTURES) {
            check(!f.machine().getRecipeLogic().isWorkingEnabled(), "Paused state lost on restart");
            check(Math.abs(maintenance(f).getDurationMultiplier() - 0.4f) < 0.000001,
                    "Maintenance setting lost on restart");
            check(!IvMachineScope.nativeTarget(f.machine()), "Restart activated isolated processing");
            log("restart_pause_and_maintenance_setting=OK " + f.machine().getDefinition().getId());
        }
    }

    public static int checks() { return checks; }
    private static void check(boolean valid, String message) {
        if (!valid) throw new IllegalStateException(message);
        checks++;
    }
    private static void log(String message) { GTLEnhancedcore.LOGGER.info("[MAINTENANCE_UPGRADE] {}", message); }
}
