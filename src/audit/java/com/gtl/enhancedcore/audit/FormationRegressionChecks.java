package com.gtl.enhancedcore.audit;

import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.common.machine.multiblock.part.EnergyHatchPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ItemBusPartMachine;
import com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder;
import com.gregtechceu.gtceu.utils.GTUtil;
import com.gtl.enhancedcore.client.preview.LazyMultiblockPreviewWidget;
import com.gtl.enhancedcore.client.preview.PreviewPreparation;
import com.lowdragmc.lowdraglib.utils.BlockInfo;
import com.lowdragmc.lowdraglib.utils.TrackedDummyWorld;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import org.gtlcore.gtlcore.api.gui.PatternPreviewWidget;

/** Native formation contract: never seed formed/parts/tier/proxies or call onStructureFormed manually. */
public final class FormationRegressionChecks {
    private static final String ID = "gtceu:vacuum_freezer";
    private final ServerLevel level;
    private final List<String> checks;
    private final Map<BlockPos, BlockState> layout = new LinkedHashMap<>();
    private final List<net.minecraft.world.item.crafting.Recipe<?>> originalRecipes;
    private WorkableElectricMultiblockMachine machine;
    private final List<EnergyHatchPartMachine> energies = new ArrayList<>();
    private final List<ItemBusPartMachine> inputs = new ArrayList<>();
    private final List<ItemBusPartMachine> outputs = new ArrayList<>();
    private final long deadline;
    private BlockPos shell;
    private BlockState shellState;
    private long energyBefore;
    private long recoveryReadyAt;
    private long recoveryDeadline;
    private int stage;
    private boolean closed;

    public FormationRegressionChecks(ServerLevel level, List<String> checks) throws Exception {
        this.level = level;
        this.checks = checks;
        require(level.getServer().isSameThread(), "formation_world_server_thread", checks);
        originalRecipes = new ArrayList<>(level.getRecipeManager().getRecipes());
        var definition = definition();
        BlockInfo[][][] blocks = definition.getMatchingShapes().getFirst().getBlocks();
        BlockPos origin = new BlockPos(96, 100, 96);
        // This is a newly created disposable world; its fixture must receive real BE ticks.
        level.setChunkForced(origin.getX() >> 4, origin.getZ() >> 4, true);
        WorkableElectricMultiblockMachine found = null;
        for (int x = 0; x < blocks.length; x++) for (int y = 0; y < blocks[x].length; y++) {
            for (int z = 0; z < blocks[x][y].length; z++) {
                BlockInfo info = blocks[x][y][z];
                if (info == null) continue;
                BlockPos pos = origin.offset(x, y, z);
                if (!level.isEmptyBlock(pos)) throw new AssertionError("formation fixture intersects " + pos);
                layout.put(pos, info.getBlockState());
                level.setBlockAndUpdate(pos, info.getBlockState());
                MetaMachine actual = MetaMachine.getMachine(level, pos);
                var template = info.getBlockEntity(pos);
                if (actual != null && template instanceof IMachineBlockEntity holder) {
                    actual.setFrontFacing(holder.getMetaMachine().getFrontFacing());
                }
                if (actual instanceof WorkableElectricMultiblockMachine powered
                        && powered.getDefinition() == definition) found = powered;
                if (actual instanceof EnergyHatchPartMachine energy) energies.add(energy);
                if (actual instanceof ItemBusPartMachine bus) {
                    if (bus.getInventory().getHandlerIO() == IO.IN) inputs.add(bus);
                    if (bus.getInventory().getHandlerIO() == IO.OUT) outputs.add(bus);
                }
                if (shell == null && !info.getBlockState().isAir() && !info.getBlockState().hasBlockEntity()) {
                    shell = pos; shellState = info.getBlockState();
                }
            }
        }
        if (found == null || energies.isEmpty() || shell == null)
            throw new AssertionError("Powered registered shape lacks controller/energy/input/output/shell: "
                    + found + "/" + energies.size() + "/" + inputs.size() + "/" + outputs.size());
        machine = found;
        machine.setWorkingEnabled(false);
        require(machine.getPattern().checkPatternAt(machine.getMultiblockState(), true),
                "world_shape_native_match_before_replacement", checks);
        Map<BlockPos, com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate> predicates =
                machine.getMultiblockState().getMatchContext().get("predicates");
        BlockPos outputPos = rolePosition(IO.OUT, "gtceu:lv_output_bus", predicates, null);
        // Output buses can also expose catalyst IN handlers. Reserve the output position first.
        BlockPos inputPos = rolePosition(IO.IN, "gtceu:lv_input_bus", predicates, outputPos);
        require(!inputPos.equals(outputPos), "world_distinct_native_input_output_positions", checks);
        replaceCandidate(inputPos, "gtceu:lv_input_bus", predicates);
        inputs.clear(); inputs.add((ItemBusPartMachine) MetaMachine.getMachine(level, inputPos));
        replaceCandidate(outputPos, "gtceu:lv_output_bus", predicates);
        outputs.clear(); outputs.add((ItemBusPartMachine) MetaMachine.getMachine(level, outputPos));
        for (int i = 0; i < energies.size(); i++) {
            BlockPos pos = energies.get(i).getPos();
            replaceCandidate(pos, "gtceu:lv_energy_input_hatch", predicates);
            energies.set(i, (EnergyHatchPartMachine) MetaMachine.getMachine(level, pos));
        }
        boolean valid = machine.getPattern().checkPatternAt(machine.getMultiblockState(), true);
        require(valid, "world_native_match_after_real_input_and_lv_energy_replacement " + diagnostic(machine), checks);
        deadline = level.getGameTime() + 1200;
    }

    /** Call on server END ticks in a disposable fresh world. */
    public boolean tick() throws Exception {
        if (closed) return true;
        if (level.getGameTime() > deadline) throw new AssertionError("formation timeout stage=" + stage
                + " formed=" + machine.isFormed() + " parts=" + machine.getParts().size()
                + " tier=" + machine.getTier() + " error=" + patternError(machine));
        switch (stage) {
            case 0 -> {
                if (!machine.isFormed()) return false;
                assertBindings(machine, "world_initial", checks);
                for (var hatch : energies) require(machine.getParts().contains(hatch), "world_real_energy_hatch_bound", checks);
                for (var bus : inputs) require(machine.getParts().contains(bus), "world_real_input_bus_bound", checks);
                for (var bus : outputs) require(machine.getParts().contains(bus), "world_real_output_bus_bound", checks);
                level.removeBlock(shell, false);
                require(!machine.isFormed(), "world_powered_shell_break_invalidates_immediately", checks);
                level.setBlockAndUpdate(shell, shellState);
                stage++;
            }
            case 1 -> {
                if (!machine.isFormed()) return false;
                assertBindings(machine, "world_reformed", checks);
                machine.onPartUnload();
                require(machine.isFormed() && machine.getMultiblockState().hasError(),
                        "world_part_unload_retains_formed_with_unload_error", checks);
                require(machine.getTier() == 0 && machine.getCapabilitiesProxy().isEmpty(),
                        "world_part_unload_clears_tier_and_capabilities", checks);
                recoveryReadyAt = level.getGameTime() + 40;
                recoveryDeadline = level.getGameTime() + 240;
                stage++;
            }
            case 2 -> {
                if (machine.getMultiblockState().hasError() || level.getGameTime() < recoveryReadyAt) return false;
                if (machine.getTier() == 0 || machine.getCapabilitiesProxy().isEmpty()) {
                    if (level.getGameTime() < recoveryDeadline) return false;
                    throw new AssertionError("world_part_unload_recovery_failed " + diagnostic(machine));
                }
                // The async matcher can clear UNLOAD_ERROR while formed remains true. A genuine
                // recovery must still execute onStructureFormed and rebuild tier/capabilities.
                assertBindings(machine, "world_part_unload_rebound", checks);
                var recipe = GTRecipeBuilder.of(new ResourceLocation("gtl_enhancedcore", "formation_native_io"),
                        machine.getRecipeType()).inputItems(Items.APPLE).outputItems(Items.GOLD_NUGGET)
                        .EUt(8).duration(20).buildRawRecipe();
                var recipes = new ArrayList<>(originalRecipes); recipes.add(recipe);
                level.getRecipeManager().replaceRecipes(recipes);
                require(machine.getRecipeType().getLookup().addRecipe(recipe),
                        "world_native_lookup_registers_unique_fixture_recipe", checks);
                for (var hatch : energies) hatch.energyContainer.setEnergyStored(hatch.energyContainer.getEnergyCapacity());
                energyBefore = energies.stream().mapToLong(h -> h.energyContainer.getEnergyStored()).sum();
                inputs.getFirst().getInventory().setStackInSlot(0, new ItemStack(Items.APPLE));
                machine.setWorkingEnabled(true);
                machine.getRecipeLogic().markLastRecipeDirty();
                require(recipe.matchRecipe(machine).isSuccess() && recipe.matchTickRecipe(machine).isSuccess(),
                        "world_native_recipe_matches_actual_formed_handlers", checks);
                checks.add("world_native_recipe_automatic_search_armed status=" + machine.getRecipeLogic().getStatus());
                stage++;
            }
            case 3 -> {
                int output = outputs.stream().mapToInt(bus -> count(bus, Items.GOLD_NUGGET)).sum();
                if (output == 0) return false;
                machine.setWorkingEnabled(false);
                require(output == 1, "world_native_recipe_outputs_exactly_one", checks);
                require(inputs.stream().mapToInt(bus -> count(bus, Items.APPLE)).sum() == 0,
                        "world_native_recipe_consumes_real_input", checks);
                require(energies.stream().mapToLong(h -> h.energyContainer.getEnergyStored()).sum() < energyBefore,
                        "world_native_recipe_consumes_real_hatch_energy", checks);
                assertBindings(machine, "world_after_recipe", checks);
                var previous = machine;
                BlockPos pos = previous.getPos();
                BlockState state = previous.getBlockState();
                var saved = previous.getHolder().getSelf().saveWithFullMetadata();
                level.removeBlock(pos, false);
                level.setBlockAndUpdate(pos, state);
                var recreated = level.getBlockEntity(pos);
                if (recreated == null) throw new AssertionError("Controller block entity was not recreated");
                recreated.load(saved);
                machine = (WorkableElectricMultiblockMachine) MetaMachine.getMachine(level, pos);
                require(machine != previous && machine.isFormed(),
                        "world_nbt_recreated_controller_restores_persisted_formed", checks);
                require(machine.getCapabilitiesProxy().isEmpty(),
                        "world_nbt_recreated_controller_requires_transient_capability_rebuild", checks);
                checks.add("world_nbt_recreated_before_recovery " + diagnostic(machine));
                recoveryDeadline = level.getGameTime() + 240;
                stage++;
            }
            case 4 -> {
                if (machine.getMultiblockState().hasError() || machine.getTier() == 0
                        || machine.getCapabilitiesProxy().isEmpty()) {
                    if (level.getGameTime() < recoveryDeadline) return false;
                    throw new AssertionError("world_nbt_recreated_recovery_failed " + diagnostic(machine));
                }
                assertBindings(machine, "world_nbt_recreated_rebound", checks);
                close(); return true;
            }
            default -> throw new AssertionError(stage);
        }
        return false;
    }

    public void close() {
        if (closed) return;
        closed = true;
        level.getRecipeManager().replaceRecipes(originalRecipes);
        machine.setWorkingEnabled(false);
        for (BlockPos pos : layout.keySet()) level.removeBlock(pos, false);
        level.setChunkForced(6, 6, false);
    }

    /** Run on the client render thread. Checks native and staged powered previews independently. */
    public static void runPreview(List<String> checks) throws Exception {
        var world = previewWorld();
        ChamberPreviewUiChecks.seedWorld(world);
        Field levelField = field(PatternPreviewWidget.class, "LEVEL");
        Object before = levelField.get(null);
        LazyMultiblockPreviewWidget lazy = null;
        List<Throwable> failures = new ArrayList<>();
        try {
            // The second ID is the user's exact machine: GTLCore zh_cn says 巨型组装厂.
            for (String id : List.of(ID, "gtceu:mage_assembler")) {
                String prefix = "preview_" + id.replace(':', '_');
                try {
                    var definition = (MultiblockMachineDefinition) GTRegistries.MACHINES.get(new ResourceLocation(id));
                    require(definition != null, prefix + "_registered", checks);
                    if (id.equals("gtceu:mage_assembler")) {
                        var pattern = definition.getPatternFactory().get();
                        var beforeEngines = enginePredicates(pattern);
                        require(com.gtl.enhancedcore.common.machine.galaxy.GalaxyEngineSupport.addMageEngine(pattern) == pattern
                                        && com.gtl.enhancedcore.common.machine.galaxy.GalaxyEngineSupport.addMageEngine(
                                        com.gtl.enhancedcore.common.machine.galaxy.GalaxyEngineSupport.addMageEngine(pattern)) == pattern,
                                prefix + "_engine_extension_preserves_pattern_identity", checks);
                        require(beforeEngines.size() == 1 && beforeEngines.equals(enginePredicates(pattern)),
                                prefix + "_single_engine_predicate_identity_preserved", checks);
                    }
                    var shapes = definition.getMatchingShapes();
                    checks.add(prefix + "_shape_count=" + shapes.size() + " firstDimensions="
                            + shapes.getFirst().getBlocks().length + "x" + shapes.getFirst().getBlocks()[0].length
                            + "x" + shapes.getFirst().getBlocks()[0][0].length);
                    if (id.equals("gtceu:mage_assembler")) {
                        long engines = 0;
                        var ability = com.gtladd.gtladditions.api.machine.GTLAddPartAbility.INSTANCE.getTHREAD_MODIFIER();
                        for (var aisle : shapes.getFirst().getBlocks()) for (var row : aisle) for (var info : row)
                            if (info != null && ability.isApplicable(info.getBlockState().getBlock())) engines++;
                        require(engines == 1, prefix + "_shape_has_exactly_one_thread_modifier=" + engines, checks);
                    }
                    PatternPreviewWidget.clearSharedPreviewState();
                    levelField.set(null, world);
                    PatternPreviewWidget nativeWidget = PatternPreviewWidget.getPatternWidget(definition);
                    inspectPreview(nativeWidget, prefix + "_native", checks, failures);
                    togglePreview(nativeWidget, prefix + "_native_toggle", checks, failures);
                    PatternPreviewWidget.clearSharedPreviewState();
                    levelField.set(null, world);
                    lazy = (LazyMultiblockPreviewWidget) LazyMultiblockPreviewWidget.create(definition);
                    lazy.ensureLoaded();
                    for (int i = 0; i < 20_000 && !lazy.isReady(); i++) {
                        PreviewPreparation.advance(lazy, PreviewPreparation.FRAME_BUDGET_NANOS);
                        if (PreviewPreparation.failed(lazy)) throw new AssertionError(prefix + "_preparation_failed");
                    }
                    require(lazy.isReady(), prefix + "_staged_completed", checks);
                    var ready = (PatternPreviewWidget) field(LazyMultiblockPreviewWidget.class, "delegate").get(lazy);
                    inspectPreview(ready, prefix + "_staged", checks, failures);
                    togglePreview(ready, prefix + "_staged_toggle", checks, failures);
                } catch (Throwable failure) { failures.add(failure); }
                finally {
                    if (lazy != null) { lazy.clearLoaded(); lazy = null; }
                    PatternPreviewWidget.clearSharedPreviewState();
                }
            }
            if (!failures.isEmpty()) {
                var failure = new AssertionError("Powered preview formation failures: " + failures);
                failures.forEach(failure::addSuppressed);
                throw failure;
            }
        } finally {
            if (lazy != null) lazy.clearLoaded();
            PatternPreviewWidget.clearSharedPreviewState();
            levelField.set(null, before);
        }
    }

    private static void inspectPreview(PatternPreviewWidget widget, String label,
            List<String> checks, List<Throwable> failures) {
        try { assertPattern(widget, label, checks); }
        catch (Throwable failure) { failures.add(failure); }
    }

    private static java.util.Set<com.gregtechceu.gtceu.api.pattern.predicates.SimplePredicate> enginePredicates(
            com.gregtechceu.gtceu.api.pattern.BlockPattern pattern) {
        var result = java.util.Collections.newSetFromMap(
                new java.util.IdentityHashMap<com.gregtechceu.gtceu.api.pattern.predicates.SimplePredicate, Boolean>());
        var ability = com.gtladd.gtladditions.api.machine.GTLAddPartAbility.INSTANCE.getTHREAD_MODIFIER();
        var cells = ((com.gtl.enhancedcore.mixin.gtceu.BlockPatternAccessor) pattern).gtlEnhancedcore$getBlockMatches();
        for (var aisle : cells) for (var row : aisle) for (var predicate : row) {
            var simples = new ArrayList<>(predicate.common); simples.addAll(predicate.limited);
            for (var simple : simples) {
                if (simple.candidates == null) continue;
                var candidates = simple.candidates.get();
                if (candidates != null && java.util.Arrays.stream(candidates).anyMatch(candidate ->
                        ability.isApplicable(candidate.getBlockState().getBlock()))) result.add(simple);
            }
        }
        return result;
    }

    private static void togglePreview(PatternPreviewWidget widget, String label,
            List<String> checks, List<Throwable> failures) {
        try {
            var toggle = PatternPreviewWidget.class.getDeclaredMethod("onFormedSwitch", boolean.class);
            toggle.setAccessible(true);
            toggle.invoke(widget, false);
            toggle.invoke(widget, true);
            assertPattern(widget, label, checks);
        } catch (Throwable failure) { failures.add(failure); }
    }

    private static void assertPattern(PatternPreviewWidget widget, String label, List<String> checks) throws Exception {
        Object[] patterns = (Object[]) field(PatternPreviewWidget.class, "patterns").get(widget);
        require(patterns.length > 0 && patterns[0] != null, label + "_pattern_present", checks);
        var controller = (IMultiController) field(patterns[0].getClass(), "controllerBase").get(patterns[0]);
        require(controller instanceof WorkableElectricMultiblockMachine, label + "_powered_controller", checks);
        String description = label + "_diagnostic " + diagnostic((WorkableElectricMultiblockMachine) controller);
        checks.add(description);
        com.gtl.enhancedcore.GTLEnhancedcore.LOGGER.info("[FORMATION_AUDIT] {}", description);
        if (!controller.isFormed()) {
            var machine = (WorkableElectricMultiblockMachine) controller;
            for (boolean flipped : new boolean[]{false, true}) {
                boolean matched;
                try (var timing = org.gtlcore.gtlcore.api.pattern.PreviewMatcherTiming.begin(
                        label + "_orientation_" + flipped, machine.getMultiblockState())) {
                    matched = machine.getPattern().checkPatternAt(machine.getMultiblockState(), machine.getPos(),
                            machine.getFrontFacing(), machine.getUpwardsFacing(), flipped, true);
                    timing.result(matched);
                }
                String orientation = label + "_explicit_orientation flipped=" + flipped + " matched=" + matched
                        + " " + diagnostic(machine);
                checks.add(orientation);
                com.gtl.enhancedcore.GTLEnhancedcore.LOGGER.info("[FORMATION_AUDIT] {}", orientation);
            }
        }
        assertBindings((WorkableElectricMultiblockMachine) controller, label, checks);
    }

    private static void assertBindings(WorkableElectricMultiblockMachine machine, String label, List<String> checks) {
        require(machine.isFormed() && !machine.getMultiblockState().hasError(), label + "_formed_without_error"
                + " formed=" + machine.isFormed() + " error=" + machine.getMultiblockState().error, checks);
        require(!machine.getParts().isEmpty(), label + "_controller_parts_nonempty", checks);
        long expectedVoltage = 0;
        int highestCount = 0;
        var seenEnergy = java.util.Collections.newSetFromMap(
                new java.util.IdentityHashMap<com.gregtechceu.gtceu.api.capability.IEnergyContainer, Boolean>());
        boolean input = false, output = false;
        for (IMultiPart part : machine.getParts()) {
            require(part.hasController(machine.getPos()), label + "_part_backlink_" + part.self().getDefinition().getId(), checks);
            if (part instanceof ItemBusPartMachine bus) {
                input |= bus.getInventory().getHandlerIO() == IO.IN;
                output |= bus.getInventory().getHandlerIO() == IO.OUT;
            }
            for (var handler : part.getRecipeHandlers()) {
                if (handler.getCapability() == EURecipeCapability.CAP
                        && (handler.getHandlerIO() == IO.IN || handler.getHandlerIO() == IO.BOTH)
                        && handler instanceof com.gregtechceu.gtceu.api.capability.IEnergyContainer energy
                        && seenEnergy.add(energy)) {
                    long voltage = energy.getInputVoltage();
                    if (voltage > expectedVoltage) { expectedVoltage = voltage; highestCount = 1; }
                    else if (voltage == expectedVoltage) highestCount++;
                }
                if (handler.getCapability() == ItemRecipeCapability.CAP) {
                    input |= handler.getHandlerIO() == IO.IN || handler.getHandlerIO() == IO.BOTH;
                    output |= handler.getHandlerIO() == IO.OUT || handler.getHandlerIO() == IO.BOTH;
                }
                if (handler.getHandlerIO() != IO.IN && handler.getHandlerIO() != IO.OUT) continue;
                var bound = machine.getCapabilitiesProxy().get(handler.getHandlerIO(), handler.getCapability());
                require(bound != null && bound.contains(handler), label + "_native_handler_bound_" + part.self().getDefinition().getId(), checks);
            }
        }
        require(expectedVoltage > 0, label + "_native_energy_handler_present", checks);
        if (machine.getDefinition().getId().toString().equals(ID))
            require(input && output, label + "_vacuum_fixture_has_both_item_handlers", checks);
        // GTLCore fix4 raises the machine's voltage tier for multiple highest-voltage input hatches.
        long machineVoltage = highestCount > 1 ? com.gregtechceu.gtceu.api.GTValues.V[
                Math.min(14, GTUtil.getTierByVoltage(expectedVoltage) + 1)] : expectedVoltage;
        require(machine.getMaxVoltage() == machineVoltage, label + "_max_voltage_matches_real_hatches", checks);
        require(machine.getTier() == GTUtil.getFloorTierByVoltage(machineVoltage), label + "_tier_matches_actual_hatch_" + machine.getTier(), checks);
        require(machine.getEnergyContainer().getInputVoltage() >= expectedVoltage, label + "_energy_container_voltage", checks);
        var energy = machine.getCapabilitiesProxy().get(IO.IN, EURecipeCapability.CAP);
        require(energy != null && !energy.isEmpty(), label + "_input_energy_proxy", checks);
        var inputBound = machine.getCapabilitiesProxy().get(IO.IN, ItemRecipeCapability.CAP);
        require(input == (inputBound != null && !inputBound.isEmpty()),
                label + "_input_item_proxy_matches_native_parts=" + input, checks);
        var bound = machine.getCapabilitiesProxy().get(IO.OUT, ItemRecipeCapability.CAP);
        require(output == (bound != null && !bound.isEmpty()),
                label + "_output_item_proxy_matches_native_parts=" + output, checks);
    }

    private static int count(ItemBusPartMachine bus, net.minecraft.world.item.Item item) {
        int result = 0;
        for (int i = 0; i < bus.getInventory().getSlots(); i++) {
            ItemStack stack = bus.getInventory().getStackInSlot(i);
            if (stack.is(item)) result += stack.getCount();
        }
        return result;
    }

    private static MultiblockMachineDefinition definition() {
        return (MultiblockMachineDefinition) GTRegistries.MACHINES.get(new ResourceLocation(ID));
    }

    private BlockPos rolePosition(IO role, String id,
            Map<BlockPos, com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate> predicates, BlockPos excluded) {
        var definition = GTRegistries.MACHINES.get(new ResourceLocation(id));
        for (var entry : predicates.entrySet()) {
            if (entry.getKey().equals(excluded)) continue;
            var actual = MetaMachine.getMachine(level, entry.getKey());
            if (!(actual instanceof IMultiPart)) continue;
            var ability = role == IO.IN ? com.gregtechceu.gtceu.api.machine.multiblock.PartAbility.IMPORT_ITEMS
                    : com.gregtechceu.gtceu.api.machine.multiblock.PartAbility.EXPORT_ITEMS;
            if (!ability.isApplicable(actual.getBlockState().getBlock())) continue;
            if (candidateAllowed(entry.getValue(), definition)) return entry.getKey();
        }
        throw new AssertionError("No existing native " + role + " item position accepting " + id);
    }

    private static boolean candidateAllowed(com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate predicate,
            com.gregtechceu.gtceu.api.machine.MachineDefinition definition) {
        var options = new ArrayList<>(predicate.common); options.addAll(predicate.limited);
        return options.stream().filter(simple -> simple.candidates != null)
                .flatMap(simple -> java.util.Arrays.stream(simple.candidates.get()))
                .anyMatch(info -> info.getBlockState().is(definition.getBlock()));
    }

    private void replaceCandidate(BlockPos pos, String id,
            Map<BlockPos, com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate> predicates) {
        var predicate = predicates.get(pos);
        if (predicate == null) throw new AssertionError("Missing native predicate at " + pos);
        var definition = GTRegistries.MACHINES.get(new ResourceLocation(id));
        boolean allowed = candidateAllowed(predicate, definition);
        require(allowed, "world_native_candidate_allowed_" + id + " at=" + pos, checks);
        level.setBlockAndUpdate(pos, definition.defaultBlockState());
        layout.put(pos, definition.defaultBlockState());
    }

    private static String patternError(WorkableElectricMultiblockMachine machine) {
        var error = machine.getMultiblockState().error;
        if (error == null) return "null";
        if (error == com.gregtechceu.gtceu.api.pattern.MultiblockState.UNINIT_ERROR
                || error == com.gregtechceu.gtceu.api.pattern.MultiblockState.UNLOAD_ERROR)
            return error.getClass().getSimpleName() + ":" + error.getErrorInfo().getString();
        var pos = error.getPos();
        return error.getClass().getSimpleName() + ":" + error.getErrorInfo().getString() + " at=" + pos
                + " controller=" + machine.getPos() + " relative=" + pos.subtract(machine.getPos())
                + " actualState=" + machine.getLevel().getBlockState(pos)
                + " candidates=" + error.getCandidates();
    }

    private static String diagnostic(WorkableElectricMultiblockMachine machine) {
        Object matched = machine.getMultiblockState().getMatchContext().get("parts");
        var pattern = machine.getPattern();
        var access = (com.gtl.enhancedcore.mixin.gtceu.BlockPatternAccessor) pattern;
        var cells = access.gtlEnhancedcore$getBlockMatches();
        return "id=" + machine.getDefinition().getId() + " formed=" + machine.isFormed()
                + " class=" + machine.getClass().getName()
                + " patternDimensions=" + cells[0][0].length + "x" + cells[0].length + "x" + cells.length
                + " center=" + java.util.Arrays.toString(access.enhanced$getCenterOffset())
                + " structureDir=" + java.util.Arrays.toString(pattern.structureDir)
                + " front=" + machine.getFrontFacing() + " up=" + machine.getUpwardsFacing() + " flipped=" + machine.isFlipped()
                + " invalid=" + machine.isInValid() + " loaded="
                + (machine.getLevel() instanceof ServerLevel serverLevel ? serverLevel.isLoaded(machine.getPos()) : "preview")
                + " actualHolder=" + (machine.getLevel().getBlockEntity(machine.getPos()) == machine.getHolder().getSelf())
                + " error=" + patternError(machine) + " tier=" + machine.getTier()
                + " maxVoltage=" + machine.getMaxVoltage() + " matched=" + matched
                + " parts=" + machine.getParts().stream().map(p -> p.self().getDefinition().getId()
                + "@" + p.self().getPos() + ":backlink=" + p.hasController(machine.getPos())).toList()
                + " proxies=" + machine.getCapabilitiesProxy();
    }

    private static PreviewWorld previewWorld() throws Exception {
        var unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); unsafeField.setAccessible(true);
        var unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        var world = (PreviewWorld) unsafe.allocateInstance(PreviewWorld.class);
        field(TrackedDummyWorld.class, "blockEntities").set(world, new java.util.HashMap<>());
        field(TrackedDummyWorld.class, "renderedBlocks").set(world, new java.util.HashMap<>());
        field(TrackedDummyWorld.class, "proxyWorld").set(world, new java.lang.ref.WeakReference<>(null));
        net.minecraftforge.fml.util.ObfuscationReflectionHelper.setPrivateValue(
                net.minecraft.world.level.Level.class, world, true, "f_46443_");
        return world;
    }

    /** Keep TrackedDummyWorld.getBlockEntity intact: native previews create hatches lazily. */
    private static final class PreviewWorld extends TrackedDummyWorld {
        private net.minecraft.core.RegistryAccess auditRegistryAccess;
        private PreviewWorld() { super(null); }
        @Override public net.minecraft.world.level.Level getLevel() { return this; }
        @Override public net.minecraft.core.RegistryAccess registryAccess() {
            return auditRegistryAccess == null ? net.minecraft.core.RegistryAccess.EMPTY : auditRegistryAccess;
        }
        @Override public net.minecraft.world.flag.FeatureFlagSet enabledFeatures() {
            return net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS;
        }
        @Override public net.minecraft.world.item.crafting.RecipeManager getRecipeManager() {
            return new net.minecraft.world.item.crafting.RecipeManager();
        }
        @Override public long getGameTime() { return 1000; }
        @Override public BlockPos getSharedSpawnPos() { return BlockPos.ZERO; }
        @Override public void blockEntityChanged(BlockPos pos) {}
        @Override public void setBlocksDirty(BlockPos pos, BlockState before, BlockState after) {}
        @Override public BlockState getBlockState(BlockPos pos) {
            var block = renderedBlocks.get(pos);
            return block == null ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState() : block.getBlockState();
        }
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name); field.setAccessible(true); return field;
    }

    private static void require(boolean value, String message, List<String> checks) {
        if (!value) throw new AssertionError(message);
        checks.add(message);
    }
}
