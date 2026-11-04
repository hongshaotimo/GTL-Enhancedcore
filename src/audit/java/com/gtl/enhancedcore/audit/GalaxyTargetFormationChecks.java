package com.gtl.enhancedcore.audit;

import com.google.gson.GsonBuilder;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.pattern.*;
import com.gregtechceu.gtceu.api.pattern.predicates.SimplePredicate;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gtl.enhancedcore.common.config.GalaxyConnectionConfig;
import com.gtl.enhancedcore.common.machine.GTLEnhancedcoreMachines;
import com.gtl.enhancedcore.common.structure.GalaxyConnectionPatterns;
import com.gtl.enhancedcore.mixin.gtceu.BlockPatternAccessor;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;

/** Real registered patterns and predicate execution, in a marked title-screen fixture. */
@Mod("enhancedcore_audit")
public final class GalaxyTargetFormationChecks {
    private boolean done;
    private final List<String> checks = new ArrayList<>();
    public GalaxyTargetFormationChecks() { MinecraftForge.EVENT_BUS.addListener(this::tick); }
    private void check(boolean valid, String message) {
        if (!valid) throw new AssertionError(message);
        checks.add(message);
    }
    private void tick(TickEvent.ClientTickEvent event) {
        var mc = Minecraft.getInstance();
        if (done || event.phase != TickEvent.Phase.END || !Boolean.getBoolean("gtl.galaxyTargetFormationAudit")
                || mc.getOverlay() != null || mc.screen == null) return;
        done = true;
        var directory = mc.gameDirectory.toPath().toAbsolutePath().normalize();
        var result = new LinkedHashMap<String, Object>();
        try {
            check(directory.startsWith(Path.of("C:/IDEA/GTL-Enhancedcore/_audit/galaxy-target-formation-20261029"))
                    && Files.isRegularFile(directory.resolve("galaxy-target.marker"))
                    && mc.level == null && mc.getSingleplayerServer() == null, "Marked fixture; no player world opened");
            boolean baseline = Boolean.getBoolean("gtl.galaxyTargetBaseline");
            var target = GTLEnhancedcoreMachines.getGalaxyTargetHatch().getBlock();
            var inventory = new LinkedHashMap<String, Object>();
            var missing = new ArrayList<String>();
            var ids = new TreeSet<>(GalaxyConnectionConfig.defaults());
            // Include the two additional opt-ins already present in the development instance.
            ids.add("gtceu:mega_wiremill"); ids.add("gtceu:pcb_factory");
            for (var id : ids) {
                var definition = (MultiblockMachineDefinition)GTRegistries.MACHINES.get(new ResourceLocation(id));
                check(definition != null, "Registered: " + id);
                var pattern = definition.getPatternFactory().get();
                var unique = Collections.newSetFromMap(new IdentityHashMap<TraceabilityPredicate, Boolean>());
                var grid = ((BlockPatternAccessor)pattern).gtlEnhancedcore$getBlockMatches();
                for (var plane : grid) for (var row : plane) Collections.addAll(unique, row);
                int inputs = 0, accepts = 0, misses = 0;
                for (var rule : unique) {
                    if (rule.isController || !input(rule)) continue;
                    inputs++;
                    if (has(rule, target)) {
                        var cell = new Cell(target.defaultBlockState(), rule);
                        check(rule.test(cell), "Real input predicate accepts target: " + id + " / " + inputs);
                        accepts++;
                    } else misses++;
                }
                if (misses > 0 || accepts == 0) missing.add(id);
                inventory.put(id, Map.of("allowed", GalaxyConnectionConfig.allows(definition.getId()),
                        "size", List.of(grid[0][0].length, grid[0].length, grid.length),
                        "inputRules", inputs, "acceptingRules", accepts, "missingRules", misses));
            }
            result.put("inventory", inventory); result.put("missing", missing);
            fullDraconic(baseline);
            if (baseline) check(missing.contains("gtladditions:draconic_collapse_core"), "4.2.7 reproduces missing draconic target admission");
            else {
                check(missing.isEmpty(), "Every configured input predicate accepts the target across all 58 machines");
                isolation(target);
                nestedFactories();
                catalystClassification();
            }
            result.put("status", "PASS");
        } catch (Throwable failure) {
            result.put("status", "FAIL"); result.put("error", failure.toString()); failure.printStackTrace();
        }
        result.put("checks", checks);
        result.put("scope", "Real Forge factories and input predicate execution; complete draconic pattern in isolated dummy world; quota/copy and nested factory checks; no player world or linked recipe run");
        try { Files.writeString(directory.resolve("galaxy-target.json"), new GsonBuilder().setPrettyPrinting().create().toJson(result)); }
        catch (Exception failure) { failure.printStackTrace(); }
        mc.stop();
    }
    private void isolation(Block target) {
        var fluid = PartAbility.IMPORT_FLUIDS.getAllBlocks().stream()
                .filter(block -> !PartAbility.EXPORT_ITEMS.isApplicable(block) && !PartAbility.EXPORT_FLUIDS.isApplicable(block))
                .findFirst().orElseThrow();
        var material = Predicates.blocks(fluid).setMinGlobalLimited(1).setMaxGlobalLimited(2).setSlotName("feed");
        var rule = Predicates.blocks(Blocks.IRON_BLOCK).or(material);
        var controller = Predicates.blocks(Blocks.GOLD_BLOCK).setController();
        var output = Predicates.abilities(PartAbility.EXPORT_FLUIDS);
        var grid = new TraceabilityPredicate[][][]{{{controller, rule, rule, output}}};
        var source = new BlockPattern(grid, new com.gregtechceu.gtceu.api.pattern.util.RelativeDirection[]{
                com.gregtechceu.gtceu.api.pattern.util.RelativeDirection.LEFT,
                com.gregtechceu.gtceu.api.pattern.util.RelativeDirection.UP,
                com.gregtechceu.gtceu.api.pattern.util.RelativeDirection.FRONT}, new int[][]{{1, 1}}, new int[]{0,0,0,0,0});
        var copy = GalaxyConnectionPatterns.extend(source);
        var copied = ((BlockPatternAccessor)copy).gtlEnhancedcore$getBlockMatches();
        check(copy != source && copied != grid && copied[0] != grid[0] && copied[0][0] != grid[0][0], "All modified pattern array levels copied");
        check(grid[0][0][1] == rule && !has(rule, target), "Original fluid input predicate remains unchanged");
        check(copied[0][0][0] == controller && copied[0][0][3] == output, "Controller and output-only slots remain unchanged");
        check(copied[0][0][1] == copied[0][0][2], "Repeated input positions share one replacement predicate");
        var counter = material.limited.getFirst();
        check(copied[0][0][1].limited.contains(counter), "Material quota object and slot metadata retained");
        check(copied[0][0][1].common.getLast().previewCount == 0, "Optional target does not replace native preview material hatches");
        var cell = new Cell(target.defaultBlockState(), copied[0][0][1]);
        check(copied[0][0][1].test(cell), "Fluid-only input accepts target through independent alternative");
        check(cell.getGlobalCount().getOrDefault(counter, 0) == 0, "Target does not satisfy the required material hatch minimum");
        cell.block = fluid.defaultBlockState();
        check(copied[0][0][1].test(cell) && copied[0][0][2].test(cell)
                && !copied[0][0][1].test(cell), "Shared two-material maximum still rejects a third material hatch");
        check(GalaxyConnectionPatterns.extend(copy) == copy, "Repeated factory wrapping is idempotent");
        var denied = MultiblockMachineDefinition.createDefinition(new ResourceLocation("gtl_enhancedcore", "formation_denied_audit"));
        denied.setPatternFactory(() -> source);
        check(denied.getPatternFactory().get() == source, "Unlisted controller factory is left unchanged");
    }
    private void nestedFactories() throws Exception {
        if (!com.gtl.enhancedcore.common.config.MachineOverrideConfig.current().replaceOriginalStructures()) return;
        var id = new ResourceLocation("gtceu", "mega_fluid_heater");
        var registered = (MultiblockMachineDefinition)GTRegistries.MACHINES.get(id);
        var method = org.gtlcore.gtlcore.common.data.machines.MultiBlockMachineA.class
                .getDeclaredMethod("lambda$static$152", MultiblockMachineDefinition.class);
        method.setAccessible(true);
        var raw = (BlockPattern)method.invoke(null, registered);
        var probe = MultiblockMachineDefinition.createDefinition(id);
        probe.setBlockSupplier(registered::getBlock);
        probe.setPatternFactory(() -> raw);
        var first = probe.getPatternFactory();
        size(first.get(), 42, 29, 40, "First final factory");
        first.get().getPreview(Arrays.stream(first.get().aisleRepetitions).mapToInt(range -> range[0]).toArray());
        probe.setPatternFactory(() -> {
            var nested = first.get();
            size(nested, 11, 17, 11, "Addon still receives original after an earlier preview");
            return nested;
        });
        var second = probe.getPatternFactory();
        size(second.get(), 42, 29, 40, "Second final factory");
        probe.setPatternFactory(() -> {
            var nested = second.get();
            size(nested, 11, 17, 11, "Second addon receives original after repeated setter");
            return nested;
        });
        size(probe.getPatternFactory().get(), 42, 29, 40, "Third final factory");
        check(probe.getPatternFactory().get() == probe.getPatternFactory().get(), "Final pattern supplier remains memoized");
    }
    private void size(BlockPattern pattern, int width, int height, int depth, String label) {
        var grid = ((BlockPatternAccessor)pattern).gtlEnhancedcore$getBlockMatches();
        check(grid.length == depth && grid[0].length == height && grid[0][0].length == width, label);
    }
    private void fullDraconic(boolean baseline) throws Exception {
        var definition = (MultiblockMachineDefinition)GTRegistries.MACHINES.get(new ResourceLocation("gtladditions", "draconic_collapse_core"));
        var pattern = definition.getPatternFactory().get();
        var fixture = StellarIvGuiChecks.fixture("gtladditions:draconic_collapse_core", false, false, false);
        var world = (com.lowdragmc.lowdraglib.utils.TrackedDummyWorld)fixture.player().level();
        var holders = new ArrayList<net.minecraft.world.level.block.entity.BlockEntity>(fixture.holders());
        world.renderedBlocks.clear(); world.blockEntities.clear();
        try {
            var preview = pattern.getPreview(Arrays.stream(pattern.aisleRepetitions).mapToInt(range -> range[0]).toArray());
            BlockPos controller = null, input = null, laser = null;
            for (int x = 0; x < preview.length; x++) for (int y = 0; y < preview[x].length; y++)
                for (int z = 0; z < preview[x][y].length; z++) {
                    var info = preview[x][y][z];
                    if (info == null || info.getBlockState().isAir()) continue;
                    var pos = new BlockPos(x, 80 + y, z);
                    var state = info.getBlockState();
                    place(world, holders, pos, state);
                    if (state.is(definition.getBlock())) controller = pos;
                    if (PartAbility.IMPORT_ITEMS.isApplicable(state.getBlock())) input = pos;
                    if (PartAbility.INPUT_LASER.isApplicable(state.getBlock())) laser = pos;
                }
            // Preview selection may choose the newly admitted target for every optional input.
            // Seed one real native input before the before/after replacement comparison.
            var access = (BlockPatternAccessor)pattern;
            var grid = access.gtlEnhancedcore$getBlockMatches();
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
            for (int x : new int[]{0, grid[0][0].length - 1}) for (int y : new int[]{0, grid[0].length - 1})
                for (int z : new int[]{0, grid.length - 1}) {
                    var relative = access.enhanced$chamberOffset(x, y, z, net.minecraft.core.Direction.NORTH, net.minecraft.core.Direction.UP, false);
                    minX = Math.min(minX, relative.getX()); minY = Math.min(minY, relative.getY()); minZ = Math.min(minZ, relative.getZ());
                }
            for (int z = 0; z < grid.length; z++) for (int y = 0; y < grid[z].length; y++) for (int x = 0; x < grid[z][y].length; x++) {
                var rule = grid[z][y][x];
                if (input != null || rule.isController || !input(rule)) continue;
                var candidate = parts(rule).filter(part -> part.candidates != null).flatMap(part -> Arrays.stream(part.candidates.get()))
                        .filter(info -> !com.gtl.enhancedcore.common.machine.galaxy.GalaxyConnectionHatch.isConnectionPortBlock(info.getBlockState().getBlock()))
                        .filter(info -> PartAbility.IMPORT_ITEMS.isApplicable(info.getBlockState().getBlock())).findFirst().orElseThrow();
                var relative = access.enhanced$chamberOffset(x, y, z, net.minecraft.core.Direction.NORTH, net.minecraft.core.Direction.UP, false);
                input = relative.offset(-minX, 80 - minY, -minZ);
                place(world, holders, input, candidate.getBlockState());
            }
            check(controller != null && input != null && laser != null, "Draconic fixture controller=" + controller + ", input=" + input + ", mandatory laser=" + laser);
            var state = new MultiblockState(world, controller);
            check(pattern.checkPatternAt(state, controller, net.minecraft.core.Direction.NORTH, net.minecraft.core.Direction.UP, false, false),
                    "Complete draconic native preview forms before replacing input; error=" + state.error);
            var original = world.getBlockState(input);
            place(world, holders, input, GTLEnhancedcoreMachines.getGalaxyTargetHatch().defaultBlockState());
            boolean formed = pattern.checkPatternAt(state, controller, net.minecraft.core.Direction.NORTH, net.minecraft.core.Direction.UP, false, false);
            check(formed != baseline, baseline ? "Old complete draconic rejects target at its real input slot"
                    : "Complete draconic forms with target at its real input slot; error=" + state.error);
            if (!baseline) {
                Object matched = state.getMatchContext().get("parts");
                check(matched instanceof Collection<?> parts && parts.stream().anyMatch(
                        com.gtl.enhancedcore.common.machine.galaxy.GalaxyConnectionHatch.class::isInstance), "Successful full pattern collects the real target machine as a part");
                var laserState = world.getBlockState(laser);
                place(world, holders, laser, Blocks.AIR.defaultBlockState());
                check(!pattern.checkPatternAt(state, controller, net.minecraft.core.Direction.NORTH, net.minecraft.core.Direction.UP, false, false),
                        "Target does not bypass the mandatory laser hatch");
                place(world, holders, laser, laserState);
                check(pattern.checkPatternAt(state, controller, net.minecraft.core.Direction.NORTH, net.minecraft.core.Direction.UP, false, false),
                        "Restoring mandatory laser reforms target structure");
            }
            place(world, holders, input, original);
        } finally { holders.forEach(holder -> holder.setLevel(null)); world.renderedBlocks.clear(); world.blockEntities.clear(); }
    }
    private static void place(com.lowdragmc.lowdraglib.utils.TrackedDummyWorld world,
            List<net.minecraft.world.level.block.entity.BlockEntity> holders, BlockPos pos, BlockState state) {
        world.renderedBlocks.put(pos, new com.lowdragmc.lowdraglib.utils.BlockInfo(state));
        world.blockEntities.remove(pos);
        if (state.getBlock() instanceof net.minecraft.world.level.block.EntityBlock block) {
            var entity = block.newBlockEntity(pos, state);
            if (entity != null) { entity.setLevel(world); world.blockEntities.put(pos, entity); holders.add(entity); }
        }
    }
    // This preexisting regression needs remapped GT/MC registries; execute it in Forge rather than JavaExec.
    private void catalystClassification() {
        var catalyst = new com.gregtechceu.gtceu.api.recipe.content.Content(
                net.minecraft.world.item.crafting.Ingredient.of(net.minecraft.world.item.Items.APPLE), 0, 10000, 0, null, null);
        var output = new com.gregtechceu.gtceu.api.recipe.content.Content(
                net.minecraft.world.item.crafting.Ingredient.of(net.minecraft.world.item.Items.DIAMOND), 1, 10000, 0, null, null);
        var recipe = new com.gregtechceu.gtceu.api.recipe.GTRecipe(null, new ResourceLocation("gtl_enhancedcore", "catalyst_route"),
                Map.of(com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability.CAP, List.of(catalyst)),
                Map.of(com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability.CAP, List.of(output)), Map.of(), Map.of(),
                Map.of(), Map.of(), Map.of(), Map.of(), List.of(), List.of(), new net.minecraft.nbt.CompoundTag(), 20, false);
        var expected = Map.<appeng.api.stacks.AEKey, Long>of(
                appeng.api.stacks.AEItemKey.of(net.minecraft.world.item.Items.DIAMOND.getDefaultInstance()), 1L);
        check(com.gtl.enhancedcore.common.recipe.iv.IvRecipeInputs.patternOperations(recipe, Map.of(), Map.of(), expected) == 0,
                "Strict execution matching still requires physical reusable catalyst");
        check(com.gtl.enhancedcore.common.recipe.iv.IvRecipeInputs.patternOperationsIgnoringReusableInputs(recipe, Map.of(), Map.of(), expected) == 1,
                "Classification publishes reusable catalyst pattern without catalyst stock");
    }
    private static Stream<SimplePredicate> parts(TraceabilityPredicate rule) {
        return Stream.concat(rule.common.stream(), rule.limited.stream());
    }
    private static boolean has(TraceabilityPredicate rule, Block block) {
        return parts(rule).filter(part -> part.candidates != null).flatMap(part -> Arrays.stream(part.candidates.get()))
                .anyMatch(info -> info.getBlockState().is(block));
    }
    private static boolean input(TraceabilityPredicate rule) {
        return parts(rule).filter(part -> part.candidates != null).flatMap(part -> Arrays.stream(part.candidates.get()))
                .map(info -> info.getBlockState().getBlock())
                .filter(block -> !com.gtl.enhancedcore.common.machine.galaxy.GalaxyConnectionHatch.isConnectionPortBlock(block))
                .anyMatch(block -> (PartAbility.IMPORT_ITEMS.isApplicable(block) || PartAbility.IMPORT_FLUIDS.isApplicable(block))
                        && !PartAbility.EXPORT_ITEMS.isApplicable(block) && !PartAbility.EXPORT_FLUIDS.isApplicable(block));
    }
    private static final class Cell extends MultiblockState {
        BlockState block;
        Cell(BlockState block, TraceabilityPredicate rule) { super(null, BlockPos.ZERO); this.block = block; clean(); predicate = rule; }
        @Override public BlockState getBlockState() { return block; }
        @Override public net.minecraft.world.level.block.entity.BlockEntity getTileEntity() { return null; }
        @Override public BlockPos getPos() { return BlockPos.ZERO; }
    }
}
