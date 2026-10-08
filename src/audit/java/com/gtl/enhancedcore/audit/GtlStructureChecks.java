package com.gtl.enhancedcore.audit;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gtl.enhancedcore.GTLEnhancedcore;
import com.lowdragmc.lowdraglib.utils.BlockInfo;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.registries.ForgeRegistries;

/** Places the unmodified source NBT in an isolated real world, one case per tick. */
public final class GtlStructureChecks {
    private static BlockPos ANCHOR = new BlockPos(0, 144, 4096);
    private static final Direction[] FACINGS = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
    private static final Rotation[] ROTATIONS = Rotation.values();
    private static final List<BlockPos> PLACED = new ArrayList<>();
    private static final Set<Long> FORCED = new HashSet<>();
    private static JsonObject manifest;
    private static List<String> names;
    private static int step;
    private static int checks;
    private static Pending pending;
    private static int settle;
    private static long formationStarted;
    private static long lastWaitLog;
    private static boolean upgrades;
    private static String upgradeManifest = "upgrades.json";
    private static String upgradeFixtures = "/gtl-upgrade-fixtures/";
    private static JsonObject originalBindings;
    private static boolean palettes;
    private static final Map<String, String> PALETTE_FIXTURES = new HashMap<>();
    private record Pending(String name, Direction front, int solids, List<BlockPos> ports,
                           BlockPos ordinary, BlockState ordinaryState) {}

    private GtlStructureChecks() {}

    public static void selectUpgrades() {
        if (manifest != null) throw new IllegalStateException("Structure audit already started");
        upgrades = true;
    }
    public static void selectSeptember27() {
        selectUpgrades();
        upgradeManifest = "upgrades_20260927.json";
        upgradeFixtures = "/gtl-upgrade-20260927-fixtures/";
    }

    public static void selectPalettes() {
        selectUpgrades();
        palettes = true;
    }

    public static boolean step(MinecraftServer server) throws Exception {
        try {
            return runStep(server);
        } catch (Exception error) {
            GTLEnhancedcore.LOGGER.error(upgrades ? "[STRUCTURE_UPGRADE] diagnostic" : "[STRUCTURE_AUDIT] diagnostic", error);
            throw error;
        }
    }

    private static boolean runStep(MinecraftServer server) throws Exception {
        if (manifest == null) {
            if (palettes) {
                manifest = new JsonObject();
                originalBindings = new JsonObject();
                String[] manifests = {"manifest.json", "upgrades.json", "upgrades_20260927.json"};
                String[] fixtures = {"/gtl-structure-fixtures/", "/gtl-upgrade-fixtures/", "/gtl-upgrade-20260927-fixtures/"};
                for (int i = 0; i < manifests.length; i++) {
                    try (var input = com.gtl.enhancedcore.common.structure.GtlMegastructurePatterns.class.getResourceAsStream(
                            "/data/gtl_enhancedcore/structures/gtl/" + manifests[i])) {
                        var group = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
                        for (var entry : group.entrySet()) {
                            if (!entry.getValue().getAsJsonObject().has("casingReplacements")) continue;
                            manifest.add(entry.getKey(), entry.getValue());
                            PALETTE_FIXTURES.put(entry.getKey(), fixtures[i]);
                        }
                    }
                    if (i > 0) {
                        try (var input = GtlStructureChecks.class.getResourceAsStream(fixtures[i] + "original-bindings.json")) {
                            var group = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
                            for (var entry : group.entrySet()) originalBindings.add(entry.getKey(), entry.getValue());
                        }
                    }
                }
                check(manifest.size() == 15, "Unexpected changed-casing scope");
                names = manifest.keySet().stream().sorted().toList();
            } else {
            try (var input = com.gtl.enhancedcore.common.structure.GtlMegastructurePatterns.class.getResourceAsStream(
                    "/data/gtl_enhancedcore/structures/gtl/" + (upgrades ? upgradeManifest : "manifest.json"))) {
                check(input != null, "Missing production structure manifest");
                manifest = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
            }
            names = manifest.keySet().stream().sorted().toList();
            if (upgrades) {
                try (var input = GtlStructureChecks.class.getResourceAsStream(upgradeFixtures + "original-bindings.json")) {
                    originalBindings = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
                }
            }
            }
        }
        var world = server.overworld();
        if (pending != null) {
            if (++settle < 16) return false;
            var controller = (MultiblockControllerMachine) MetaMachine.getMachine(world, ANCHOR);
            long elapsed = System.nanoTime() - formationStarted;
            // Native scans use a wall-clock scheduler; catch-up ticks after fixture placement
            // must not exhaust the timeout before that scheduler has had a chance to run.
            if (controller != null && !controller.isFormed() && elapsed < 60_000_000_000L) {
                var error = controller.getMultiblockState().error;
                if (settle == 16 || elapsed - lastWaitLog >= 10_000_000_000L) {
                    log("WAIT " + pending.name() + " facing=" + pending.front()
                            + " elapsed_ms=" + elapsed / 1_000_000
                            + " reason=" + (error == null ? "none" : error.getErrorInfo().getString()));
                    lastWaitLog = elapsed;
                }
                return false;
            }
            verify(world);
            pending = null;
            step++;
            return false;
        }
        clear(world);
        if (step == names.size() * FACINGS.length) {
            log("COMPLETE machines=" + names.size() + " real_world_orientations=" + step + " checks=" + checks);
            return true;
        }
        String name = names.get(step / FACINGS.length);
        Direction front = FACINGS[step % FACINGS.length];
        log("START " + name + " facing=" + front);
        var spec = manifest.getAsJsonObject(name);
        var sourceCore = spec.getAsJsonArray("sourceController");
        ANCHOR = new BlockPos(0, Math.min(144, world.getMaxBuildHeight() - 2
                - spec.getAsJsonArray("sourceSize").get(1).getAsInt() + sourceCore.get(1).getAsInt()), 4096);
        var definition = (com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition)
                GTRegistries.MACHINES.get(new net.minecraft.resources.ResourceLocation(
                        spec.has("namespace") ? spec.get("namespace").getAsString() : "gtceu", name));
        check(definition != null, name + ": missing exact machine ID");
        var pattern = definition.getPatternFactory().get();
        check(com.gtl.enhancedcore.common.structure.GtlMegastructurePatterns.replace(definition.getId(), pattern) == pattern,
                name + ": repeated factory wrapping changed geometry");
        var preview = definition.getMatchingShapes();
        check(preview == definition.getMatchingShapes(), name + ": preview not memoized");
        BlockInfo[][][] blocks = preview.getFirst().getBlocks();
        BlockState optionalPort = null;
        if (palettes && java.util.Arrays.stream(blocks).flatMap(java.util.Arrays::stream)
                .flatMap(java.util.Arrays::stream).filter(java.util.Objects::nonNull).noneMatch(info ->
                        info.getBlockState().getBlock() instanceof com.gregtechceu.gtceu.api.block.MetaMachineBlock block
                                && block != definition.getBlock())) {
            // Native patterns with no minimum hatch count legitimately preview only casings.
            // Install one allowed part so the fixture still exercises controller/part linking.
            var data = firstHatch(pattern, name);
            optionalPort = java.util.stream.Stream.concat(data.common.stream(), data.limited.stream())
                    .filter(simple -> simple.candidates != null)
                    .flatMap(simple -> java.util.Arrays.stream(simple.candidates.get()))
                    .map(BlockInfo::getBlockState)
                    .filter(state -> state.getBlock() instanceof com.gregtechceu.gtceu.api.block.MetaMachineBlock)
                    .findFirst().orElseThrow();
        }
        var size = spec.getAsJsonArray("size");
        check(blocks.length == size.get(0).getAsInt() && blocks[0].length == size.get(1).getAsInt()
                && blocks[0][0].length == size.get(2).getAsInt(), name + ": wrong preview dimensions");
        var core = spec.getAsJsonArray("sourceController");
        var normal = spec.getAsJsonArray("sourceNormal");
        Direction sourceFacing = Direction.fromDelta(normal.get(0).getAsInt(), normal.get(1).getAsInt(),
                normal.get(2).getAsInt());
        int turns = Math.floorMod(front.get2DDataValue() - sourceFacing.get2DDataValue(), 4);
        Rotation partRotation = ROTATIONS[Math.floorMod(front.get2DDataValue() - Direction.NORTH.get2DDataValue(), 4)];
        net.minecraft.nbt.CompoundTag source;
        try (var input = GtlStructureChecks.class.getResourceAsStream(
                (palettes ? PALETTE_FIXTURES.get(name) : upgrades ? upgradeFixtures : "/gtl-structure-fixtures/")
                        + name + ".schem")) {
            source = NbtIo.readCompressed(input);
        }
        if (source.contains("Schematic")) source = source.getCompound("Schematic");
        int width = source.getShort("Width"), height = source.getShort("Height"), depth = source.getShort("Length");
        var paletteTag = source.getCompound("Palette");
        Map<Integer, BlockState> palette = new HashMap<>();
        for (String state : paletteTag.getAllKeys()) {
            String id = state.split("\\[", 2)[0];
            var key = new net.minecraft.resources.ResourceLocation(id);
            check(ForgeRegistries.BLOCKS.containsKey(key), "Unknown source block " + id);
            var blockState = ForgeRegistries.BLOCKS.getValue(key).defaultBlockState();
            if (state.contains("[")) {
                for (String pair : state.substring(state.indexOf('[') + 1, state.length() - 1).split(",")) {
                    String[] property = pair.split("=", 2);
                    blockState = setProperty(blockState, blockState.getBlock().getStateDefinition().getProperty(property[0]), property[1]);
                }
            }
            palette.put(paletteTag.getInt(state), blockState);
        }
        if (upgrades && step % FACINGS.length == 0 && (!palettes || originalBindings.has(name)))
            verifyBindings(name, spec, pattern);
        byte[] data = source.getByteArray("BlockData");
        int cursor = 0, markers = 0, solids = 0;
        List<BlockPos> ports = new ArrayList<>();
        BlockPos ordinary = null;
        BlockState ordinaryState = null;
        BlockState controllerState = null;
        var bodyCasing = ForgeRegistries.BLOCKS.getValue(new net.minecraft.resources.ResourceLocation(
                spec.getAsJsonObject("bindings").getAsJsonObject("#").get("block").getAsString()));
        for (int index = 0; index < width * height * depth; index++) {
            int value = 0, shift = 0, current;
            do {
                current = data[cursor++] & 255;
                value |= (current & 127) << shift;
                shift += 7;
            } while ((current & 128) != 0);
            var state = palette.get(value);
            if (state.isAir()) continue;
            int x = index % width, y = index / (width * depth), z = (index / width) % depth;
            boolean port = state.is(Blocks.DIAMOND_ORE), controller = state.is(Blocks.DIAMOND_BLOCK);
            if (!port && !controller && spec.has("casingReplacements")) {
                var replacement = spec.getAsJsonObject("casingReplacements").get(
                        ForgeRegistries.BLOCKS.getKey(state.getBlock()).toString());
                if (replacement != null) {
                    var target = ForgeRegistries.BLOCKS.getValue(new net.minecraft.resources.ResourceLocation(replacement.getAsString()));
                    check(target != null && target != Blocks.AIR, name + ": missing replacement casing");
                    state = target.defaultBlockState();
                }
            }
            if (port || controller) {
                // Independently rotate source coordinates to the NORTH-facing preview.
                int px = switch (sourceFacing) {
                    case WEST -> depth - 1 - z;
                    case EAST -> z;
                    case SOUTH -> width - 1 - x;
                    default -> x;
                };
                int pz = switch (sourceFacing) {
                    case WEST -> x;
                    case EAST -> width - 1 - x;
                    case SOUTH -> depth - 1 - z;
                    default -> z;
                };
                state = blocks[px][y][pz].getBlockState().rotate(partRotation);
                if (port && markers == 0 && optionalPort != null) state = optionalPort.rotate(partRotation);
                check(!state.is(Blocks.DIAMOND_ORE) && !state.is(Blocks.DIAMOND_BLOCK),
                        name + ": raw marker leaked into preview");
                if (port) markers++;
            }
            int dx = x - core.get(0).getAsInt(), dz = z - core.get(2).getAsInt();
            for (int turn = 0; turn < turns; turn++) {
                int oldX = dx;
                dx = -dz;
                dz = oldX;
            }
            var pos = ANCHOR.offset(dx, y - core.get(1).getAsInt(), dz);
            if (pos.getY() < world.getMinBuildHeight() || pos.getY() >= world.getMaxBuildHeight())
                throw new IllegalStateException(name + ": fixture exceeds world height");
            int chunkX = pos.getX() >> 4, chunkZ = pos.getZ() >> 4;
            if (FORCED.add(ChunkPos.asLong(chunkX, chunkZ))) world.setChunkForced(chunkX, chunkZ, true);
            if (controller) {
                controllerState = state;
                continue;
            }
            world.setBlock(pos, state, 2 | 16);
            PLACED.add(pos);
            solids++;
            if (port) ports.add(pos);
            else if (ordinary == null || !ordinaryState.is(bodyCasing) && state.is(bodyCasing)) {
                ordinary = pos;
                ordinaryState = state;
            }
        }
        check(cursor == data.length && markers == 24 && controllerState != null, name + ": source marker/count mismatch");
        world.setBlock(ANCHOR, controllerState, 2 | 16);
        PLACED.add(ANCHOR);
        var machine = (MultiblockControllerMachine) MetaMachine.getMachine(world, ANCHOR);
        check(machine != null && machine.getFrontFacing() == front, name + ": controller facing mismatch");
        pending = new Pending(name, front, solids, ports, ordinary, ordinaryState);
        settle = 0;
        formationStarted = System.nanoTime();
        lastWaitLog = 0;
        return false;
    }

    private static void verify(ServerLevel world) {
        String name = pending.name();
        var machine = (MultiblockControllerMachine) MetaMachine.getMachine(world, ANCHOR);
        var lock = machine.getPatternLock();
        lock.lock();
        try {
            if (!machine.isFormed()) {
                var before = machine.getMultiblockState().error;
                log("unformed reason=" + (before == null ? "none" : before.getErrorInfo().getString()));
                boolean matches = machine.checkPattern();
                var error = machine.getMultiblockState().error;
                check(false, name + ": did not form naturally, directMatch=" + matches + ", error="
                        + (error == null ? "none" : error.getErrorInfo().getString()));
            }
            check(machine.getParts().size() > 0 && machine.getParts().size() <= 24
                    && machine.getParts().stream().allMatch(java.util.Objects::nonNull), name + ": invalid hatch count "
                    + machine.getParts().size() + " placedPorts="
                    + pending.ports().stream().map(pos -> pos + "=" + ForgeRegistries.BLOCKS.getKey(world.getBlockState(pos).getBlock()))
                            .toList()
                    + " linked=" + machine.getParts().stream().map(part -> part.self().getPos()
                            + "=" + part.self().getDefinition().getId()).limit(30).toList());
            int partCount = machine.getParts().size();
            if (palettes) {
                var spec = manifest.getAsJsonObject(name);
                var expectedAppearance = ForgeRegistries.BLOCKS.getValue(new net.minecraft.resources.ResourceLocation(
                        spec.getAsJsonObject("bindings").getAsJsonObject("#").get("block").getAsString())).defaultBlockState();
                check(machine.getDefinition().getAppearance().get() == expectedAppearance,
                        name + ": native controller appearance changed");
                for (var part : machine.getParts()) for (Direction side : Direction.values())
                    check(machine.getPartAppearance(part, side, expectedAppearance, part.self().getPos()) == expectedAppearance,
                            name + ": native hatch appearance changed");
            }
            if (upgrades && Set.of("mega_fluid_heater", "dimensionally_transcendent_chemical_plant",
                    "super_blast_smelter", "atomic_energy_excitation_plant").contains(name)) {
                var coil = machine.getMultiblockState().getMatchContext().get("CoilType");
                check(coil instanceof com.gregtechceu.gtceu.api.block.ICoilType, name + ": native coil context missing");
                check(((com.gregtechceu.gtceu.api.block.ICoilType) coil).getCoilTemperature() > 1800,
                        name + ": source coil replaced by default temperature");
            }
            if (name.equals("component_assembly_line")) {
                check(machine.getMultiblockState().getMatchContext().get("CATier") != null,
                        name + ": component casing tier context missing");
            }
            if (step % FACINGS.length == 0) {
                var actualPort = pending.ports().stream().filter(pos -> MetaMachine.getMachine(world, pos) != null).findFirst().orElseThrow();
                var portState = world.getBlockState(actualPort);
                machine.onStructureInvalid();
                world.setBlock(pending.ordinary(), portState, 2 | 16);
                check(!machine.checkPattern(), name + ": hatch accepted outside diamond-ore panel");
                world.setBlock(pending.ordinary(), pending.ordinaryState(), 2 | 16);
                check(machine.checkPattern(), name + ": did not recover after restoring body");
                world.setBlock(actualPort, Blocks.DIAMOND_ORE.defaultBlockState(), 2 | 16);
                check(!machine.checkPattern(), name + ": raw diamond ore accepted as a real hatch");
                world.setBlock(actualPort, portState, 2 | 16);
                check(machine.checkPattern(), name + ": did not recover after restoring hatch");
            }
            log("PASS " + name + " facing=" + pending.front() + " blocks=" + pending.solids()
                    + " ports=" + pending.ports().size() + " parts=" + partCount
                    + " elapsed_ms=" + (System.nanoTime() - formationStarted) / 1_000_000);
        } finally {
            lock.unlock();
        }
    }

    private static void clear(ServerLevel world) {
        var machine = MetaMachine.getMachine(world, ANCHOR);
        if (machine instanceof MultiblockControllerMachine controller && controller.isFormed()) {
            controller.onStructureInvalid();
        }
        world.setBlock(ANCHOR, Blocks.AIR.defaultBlockState(), 2 | 16);
        for (var pos : PLACED) world.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16);
        PLACED.clear();
        for (long chunk : FORCED) world.setChunkForced(ChunkPos.getX(chunk), ChunkPos.getZ(chunk), false);
        FORCED.clear();
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new IllegalStateException(message);
        checks++;
    }

    private static void log(String message) {
        GTLEnhancedcore.LOGGER.info(upgrades ? "[STRUCTURE_UPGRADE] {}" : "[STRUCTURE_AUDIT] {}", message);
    }

    private static <T extends Comparable<T>> BlockState setProperty(BlockState state,
            net.minecraft.world.level.block.state.properties.Property<T> property, String value) {
        if (property == null) throw new IllegalStateException("Unknown schematic property");
        return state.setValue(property, property.getValue(value).orElseThrow());
    }

    private static void verifyBindings(String name, JsonObject spec, com.gregtechceu.gtceu.api.pattern.BlockPattern pattern)
            throws java.io.IOException {
        var grid = ((com.gtl.enhancedcore.mixin.gtceu.BlockPatternAccessor) pattern).gtlEnhancedcore$getBlockMatches();
        var old = originalBindings.getAsJsonObject(name).getAsJsonArray("predicates");
        var first = new HashMap<Character, com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate>();
        var z = new int[]{0};
        com.gtl.enhancedcore.common.structure.StructureData.read(com.gtl.enhancedcore.common.structure.GtlMegastructurePatterns.class.getResourceAsStream(
                "/data/gtl_enhancedcore/structures/gtl/" + name + ".pattern.gz")).forEachAisle(rows -> {
            for (int y = 0; y < rows.length; y++) for (int x = 0; x < rows[y].length(); x++)
                first.putIfAbsent(rows[y].charAt(x), grid[z[0]][y][x]);
            z[0]++;
        });
        for (var entry : spec.getAsJsonObject("bindings").entrySet()) {
            var binding = entry.getValue().getAsJsonObject();
            String mode = binding.get("mode").getAsString();
            var predicate = first.get(entry.getKey().charAt(0));
            check(predicate != null, name + ": unused binding " + entry.getKey());
            if (mode.equals("any")) continue;
            var actual = java.util.stream.Stream.concat(predicate.common.stream(), predicate.limited.stream())
                    .distinct().map(StructureUpgradeBindings::describe).toList();
            if (mode.equals("literal")) {
                if (binding.get("block").getAsString().endsWith("_lamp")) {
                    var info = predicate.common.getFirst().candidates.get()[0];
                    var state = info.getBlockState();
                    var items = new org.gtlcore.gtlcore.common.item.UltimateTerminalBehavior.AutoBuildSetting()
                            .apply(new BlockInfo[]{info});
                    var props = binding.getAsJsonObject("properties");
                    check(com.gregtechceu.gtceu.common.block.LampBlock.isInverted(state) == props.get("inverted").getAsBoolean(),
                            name + ": lamp preview lost inversion");
                    check(items.size() == 1
                            && com.gregtechceu.gtceu.common.block.LampBlock.isInverted(items.getFirst().getTag()) == props.get("inverted").getAsBoolean()
                            && com.gregtechceu.gtceu.common.block.LampBlock.isLightEnabled(items.getFirst().getTag()) == props.get("lit").getAsBoolean()
                            && com.gregtechceu.gtceu.common.block.LampBlock.isBloomEnabled(items.getFirst().getTag()) == props.get("bloom").getAsBoolean(),
                            name + ": native terminal changed lamp NBT");
                }
                continue;
            }
            JsonObject source = null;
            for (var candidate : old) if (candidate.getAsJsonObject().get("sample").equals(binding.get("sample"))) {
                source = candidate.getAsJsonObject();
                break;
            }
            check(source != null, name + ": unknown original predicate");
            var expected = new HashSet<JsonObject>();
            boolean isolated = com.gtl.enhancedcore.common.recipe.iv.IvMachineScope.nativeTarget(
                    new net.minecraft.resources.ResourceLocation(spec.get("namespace").getAsString(), name));
            for (var element : source.getAsJsonArray("simple")) {
                var simple = element.getAsJsonObject();
                boolean casing = binding.has("block") && simple.getAsJsonArray("blocks").size() == 1
                        && simple.getAsJsonArray("blocks").get(0).equals(binding.get("block"));
                if (mode.equals("casing") && !casing) continue;
                if (mode.equals("hatch") && casing && !binding.get("block").equals(binding.get("fallback"))) continue;
                if (isolated && materialRule(simple)) continue;
                expected.add(simple);
            }
            if (mode.equals("hatch") && !binding.get("block").equals(binding.get("fallback"))) {
                var fallback = com.gregtechceu.gtceu.api.pattern.Predicates.blocks(ForgeRegistries.BLOCKS.getValue(
                        new net.minecraft.resources.ResourceLocation(binding.get("fallback").getAsString())));
                expected.add(StructureUpgradeBindings.describe(fallback.common.getFirst()));
            }
            if (mode.equals("hatch") && binding.has("additionalSources")) {
                for (var extra : binding.getAsJsonArray("additionalSources")) {
                    var part = extra.getAsJsonObject();
                    for (var candidate : old) if (candidate.getAsJsonObject().get("sample").equals(part.get("sample"))) {
                        for (var element : candidate.getAsJsonObject().getAsJsonArray("simple")) {
                            var simple = element.getAsJsonObject();
                            if (simple.getAsJsonArray("blocks").size() == 1
                                    && simple.getAsJsonArray("blocks").get(0).equals(part.get("block"))) continue;
                            if (!isolated || !materialRule(simple)) expected.add(simple);
                        }
                    }
                }
            }
            if (mode.equals("hatch") && binding.has("requiredParts")) {
                for (var extra : binding.getAsJsonArray("requiredParts")) {
                    var part = extra.getAsJsonObject();
                    for (var candidate : old) if (candidate.getAsJsonObject().get("sample").equals(part.get("sample"))) {
                        var simple = candidate.getAsJsonObject().getAsJsonArray("simple").get(0).getAsJsonObject().deepCopy();
                        for (String field : List.of("min", "max", "preview")) simple.add(field, part.get("count"));
                        expected.add(simple);
                    }
                }
            }
            if (mode.equals("hatch") && isolated) {
                var buffer = com.gregtechceu.gtceu.api.pattern.Predicates.blocks(ForgeRegistries.BLOCKS.getValue(
                        new net.minecraft.resources.ResourceLocation("gtladditions:me_super_pattern_buffer")))
                        .setMinGlobalLimited(1).setPreviewCount(1);
                expected.add(StructureUpgradeBindings.describe(buffer.limited.getFirst()));
            }
            if (mode.equals("hatch") && (name.equals("qft") || name.equals("gravitation_shockburst"))) {
                var maintenance = com.gregtechceu.gtceu.api.pattern.Predicates.abilities(
                        com.gregtechceu.gtceu.api.machine.multiblock.PartAbility.MAINTENANCE)
                        .setMaxGlobalLimited(1).setPreviewCount(1);
                check(actual.contains(StructureUpgradeBindings.describe(maintenance.limited.getFirst())),
                        name + ": optional maintenance limits changed");
                expected.add(StructureUpgradeBindings.describe(maintenance.limited.getFirst()));
            }
            // Gson's parsed numbers and Java integer primitives can have different hash codes.
            check(actual.stream().allMatch(a -> expected.stream().anyMatch(a::equals))
                    && expected.stream().allMatch(e -> actual.stream().anyMatch(e::equals)),
                    name + ": native ability/limit changed at " + entry.getKey()
                    + " expected=" + expected + " actual=" + actual);
        }
        log("native_predicates_limits_and_lamp_candidates=OK " + name);
    }

    private static boolean materialRule(JsonObject simple) {
        for (var block : simple.getAsJsonArray("blocks")) {
            if (com.gtl.enhancedcore.common.recipe.iv.IvNativeHatches.materials(ForgeRegistries.BLOCKS.getValue(
                    new net.minecraft.resources.ResourceLocation(block.getAsString())))) return true;
        }
        return false;
    }

    private static com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate firstHatch(
            com.gregtechceu.gtceu.api.pattern.BlockPattern pattern, String name) throws java.io.IOException {
        var grid = ((com.gtl.enhancedcore.mixin.gtceu.BlockPatternAccessor) pattern).gtlEnhancedcore$getBlockMatches();
        var found = new com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate[1];
        int[] z = {0};
        com.gtl.enhancedcore.common.structure.StructureData.read(
                com.gtl.enhancedcore.common.structure.GtlMegastructurePatterns.class.getResourceAsStream(
                        "/data/gtl_enhancedcore/structures/gtl/" + name + ".pattern.gz")).forEachAisle(rows -> {
            for (int y = 0; y < rows.length && found[0] == null; y++) {
                int x = rows[y].indexOf('#');
                if (x >= 0) found[0] = grid[z[0]][y][x];
            }
            z[0]++;
        });
        return java.util.Objects.requireNonNull(found[0], "Missing hatch markers");
    }

}
