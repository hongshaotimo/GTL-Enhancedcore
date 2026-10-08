package com.gtl.enhancedcore.audit;

import com.gregtechceu.gtceu.api.block.MetaMachineBlock;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableEnergyContainer;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.common.recipe.condition.ResearchCondition;
import com.gregtechceu.gtceu.utils.ResearchManager;
import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtl.enhancedcore.common.machine.CausalityTerminalMachine;
import com.gtl.enhancedcore.common.machine.GTLEnhancedcoreMachines;
import com.gtl.enhancedcore.common.machine.hatch.QuantumDataAccessHatchMachine;
import com.gtl.enhancedcore.common.recipe.iv.IvMachineScope;
import com.lowdragmc.lowdraglib.misc.ItemStackTransfer;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.registries.ForgeRegistries;

/** Test-only: full production geometry, real hatches and persisted server state. */
public final class DataCausalityChecks {
    private static final BlockPos DATA_POS = new BlockPos(240, 80, 0);
    private static final BlockPos SAVED_POS = new BlockPos(244, 80, 0);
    private static CausalityTerminalMachine terminal, savedSuccess;
    private static QuantumDataAccessHatchMachine dataHatch;
    private static final List<NotifiableEnergyContainer> energy = new ArrayList<>();
    private static NotifiableItemStackHandler input, otherInput, output;
    private static int checks, readyTicks;
    private static boolean restart;
    private DataCausalityChecks() {}

    private static Field field(String name) throws Exception {
        var field = CausalityTerminalMachine.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static Method method(String name, Class<?>... args) throws Exception {
        var method = CausalityTerminalMachine.class.getDeclaredMethod(name, args);
        method.setAccessible(true);
        return method;
    }

    private static Block block(String name) {
        var id = new ResourceLocation(name);
        check(ForgeRegistries.BLOCKS.containsKey(id), "Unknown block " + name);
        return ForgeRegistries.BLOCKS.getValue(id);
    }

    public static void setup(MinecraftServer server, boolean saved) throws Exception {
        restart = saved;
        var world = server.overworld();
        var definition = GTLEnhancedcoreMachines.getCausalityTerminal();
        var shape = definition.getMatchingShapes().getFirst().getBlocks();
        Block[] parts = {block("gtceu:auto_maintenance_hatch"),
                block("gtceu:max_4194304a_laser_target_hatch"),
                block("gtceu:iv_input_bus"), block("gtceu:iv_output_bus")};
        int ports = 0, solids = 0;
        BlockPos core = null;
        for (int x = 0; x < shape.length; x++) for (int z = 0; z < shape[x][0].length; z++) {
            if ((x & 15) == 0 && (z & 15) == 0) world.setChunkForced(x >> 4, z >> 4, true);
            for (int y = 0; y < shape[x].length; y++) {
                var info = shape[x][y][z];
                if (info == null || info.getBlockState().isAir()) continue;
                var state = info.getBlockState();
                var pos = new BlockPos(x, 64 + y, z);
                if (state.getBlock() == definition.get()) {
                    core = pos;
                    continue;
                }
                if (state.getBlock() instanceof MetaMachineBlock) {
                    check(ports < parts.length, "Unexpected preview part");
                    state = parts[ports++].defaultBlockState();
                }
                if (!saved) world.setBlock(pos, state, 2 | 16);
                solids++;
            }
        }
        check(core != null && ports == 4, "Missing controller/ports");
        if (!saved) {
            // The 5x5 X panel is in the controller's z plane. Add a second input bus
            // on a spare panel casing before placing the controller.
            BlockPos secondBus = null;
            Block casing = block("gtceu:high_power_casing");
            for (int dx = -2; dx <= 2 && secondBus == null; dx++) {
                for (int dy = -2; dy <= 2; dy++) {
                    BlockPos candidate = core.offset(dx, dy, 0);
                    if (world.getBlockState(candidate).is(casing)) {
                        secondBus = candidate;
                        break;
                    }
                }
            }
            check(secondBus != null, "No spare X-panel casing for second input bus");
            world.setBlock(secondBus, block("gtceu:iv_input_bus").defaultBlockState(), 2 | 16);
            world.setBlock(core, definition.defaultBlockState(), 2 | 16);
        }
        terminal = (CausalityTerminalMachine) MetaMachine.getMachine(world, core);
        world.setChunkForced(DATA_POS.getX() >> 4, 0, true);
        if (!saved) {
            terminal.setWorkingEnabled(false);
            world.setBlock(DATA_POS, GTLEnhancedcoreMachines.getQuantumDataAccessHatch().defaultBlockState(), 3);
            world.setBlock(SAVED_POS, definition.defaultBlockState(), 3);
        }
        dataHatch = (QuantumDataAccessHatchMachine) MetaMachine.getMachine(world, DATA_POS);
        savedSuccess = (CausalityTerminalMachine) MetaMachine.getMachine(world, SAVED_POS);
        if (saved) {
            check(!field("pendingSuccess").getBoolean(terminal) && pending(terminal).getCount() == 1,
                    "Failed outcome lost/rerolled on real restart");
            check(field("progress").getInt(terminal) == 42, "Saved progress changed before formation");
            check(field("pendingSuccess").getBoolean(savedSuccess) && pending(savedSuccess).getCount() == 1,
                    "Successful outcome lost on real restart");
            check(field("progress").getInt(savedSuccess) == 42, "Unformed saved progress lost");
            check(dataHatch.importItems.getStackInSlot(809).is(GTItems.TOOL_DATA_MODULE.get()),
                    "Saved data module lost");
            var research = ResearchManager.readResearchId(dataHatch.importItems.getStackInSlot(809));
            check(research != null, "Saved research NBT lost");
            var recipes = research.getFirst().getDataStickEntry(research.getSecond());
            check(recipes != null && !recipes.isEmpty(), "Saved research ID no longer registered");
            for (var recipe : recipes) check(dataHatch.isRecipeAvailable(recipe, new HashSet<>()),
                    "Saved module no longer unlocks research");
            log("real_restart_success_failure_progress_and_data_module=OK");
        }
        log("full_structure size=" + shape.length + "x" + shape[0].length + "x" + shape[0][0].length
                + " solids=" + solids + " restart=" + saved);
    }

    public static boolean ready() throws Exception {
        if (!terminal.isFormed() || !field("runPartsReady").getBoolean(terminal)) return false;
        return ++readyTicks >= 20;
    }

    public static int run(MinecraftServer server) throws Exception {
        input = otherInput = output = null;
        energy.clear();
        for (var part : terminal.getParts()) for (var handler : part.getRecipeHandlers()) {
            if (handler instanceof NotifiableItemStackHandler items) {
                if (items.getHandlerIO() == IO.IN && items.getSlots() >= 17) {
                    if (input == null) input = items;
                    else if (items != input && otherInput == null) otherInput = items;
                }
                if (items.getHandlerIO() == IO.OUT) output = items;
            }
            if (handler instanceof NotifiableEnergyContainer container) energy.add(container);
        }
        check(input != null && otherInput != null && output != null && !energy.isEmpty(),
                "Real hatches or second input bus missing");
        for (var container : energy) check(container.getEnergyCapacity() >= CausalityTerminalMachine.EUT,
                "Test hatch cannot buffer one tick: " + container.getEnergyCapacity());
        check(terminal.isRecipeLogicAvailable(), "Full machine not ready for recipe ticks");
        if (restart) {
            check(!field("pendingSuccess").getBoolean(terminal) && !pending(terminal).isEmpty(),
                    "Formation canceled/rerolled saved failure");
            check(field("progress").getInt(terminal) >= 42, "Restart did not preserve progress");
            for (int tick = 0; tick <= CausalityTerminalMachine.RUN_TICKS && !pending(terminal).isEmpty(); tick++) tick();
            check(pending(terminal).isEmpty(), "Restarted failure did not complete within one cycle");
            check(count(output, Items.DIAMOND) == 0, "Restart turned a failed attempt into output");
            log("saved_failure_resumed_to_completion=OK");
        }
        researchChecks(server);
        equipmentChecks(server);
        tooltipChecks();
        randomChecks();
        productionChecks();
        checkpoint();
        return checks;
    }

    private static void researchChecks(MinecraftServer server) throws Exception {
        clear(dataHatch.importItems);
        var recipe = server.getRecipeManager().getAllRecipesFor(GTRecipeTypes.ASSEMBLY_LINE_RECIPES).stream()
                .filter(r -> r.conditions.stream().anyMatch(c -> c instanceof ResearchCondition research
                        && research.data.iterator().hasNext())).findFirst().orElseThrow();
        var research = (ResearchCondition) recipe.conditions.stream().filter(ResearchCondition.class::isInstance)
                .findFirst().orElseThrow();
        String id = research.data.iterator().next().getResearchId();
        int[] slots = {0, 80, 81, 809};
        for (var item : List.of(GTItems.TOOL_DATA_STICK, GTItems.TOOL_DATA_ORB, GTItems.TOOL_DATA_MODULE)) {
            for (int slot : slots) {
                var blank = item.asStack();
                check(dataHatch.importItems.isItemValid(slot, blank), "Blank data item rejected by UI filter");
                check(dataHatch.importItems.insertItem(slot, blank, true).isEmpty(), "Simulated insertion rejected");
                check(dataHatch.importItems.getStackInSlot(slot).isEmpty(), "Simulation mutated inventory");
                check(dataHatch.importItems.insertItem(slot, blank, false).isEmpty(), "Blank insertion rejected");
                check(!dataHatch.isRecipeAvailable(recipe, new HashSet<>()), "Blank data unlocked research");
                dataHatch.importItems.extractItem(slot, 1, false);
                var written = item.asStack();
                ResearchManager.writeResearchToNBT(written.getOrCreateTag(), id, GTRecipeTypes.ASSEMBLY_LINE_RECIPES);
                check(dataHatch.getItemTransferCap(null, false).insertItem(slot, written, false).isEmpty(),
                        "Capability insertion rejected written data");
                check(dataHatch.isRecipeAvailable(recipe, new HashSet<>()), "Written data failed to unlock recipe");
                dataHatch.importItems.extractItem(slot, 1, false);
                check(!dataHatch.isRecipeAvailable(recipe, new HashSet<>()), "Extraction retained research");
            }
        }
        var forged = new ItemStack(Items.DIRT);
        ResearchManager.writeResearchToNBT(forged.getOrCreateTag(), id, GTRecipeTypes.ASSEMBLY_LINE_RECIPES);
        check(!dataHatch.importItems.isItemValid(0, forged)
                && !dataHatch.importItems.insertItem(0, forged, false).isEmpty(), "Non-data item accepted");
        var module = GTItems.TOOL_DATA_MODULE.asStack();
        ResearchManager.writeResearchToNBT(module.getOrCreateTag(), id, GTRecipeTypes.ASSEMBLY_LINE_RECIPES);
        check(dataHatch.importItems.insertItem(809, module, false).isEmpty(), "Module insert failed");
        var controllerPos = DATA_POS.offset(8, 0, 0);
        server.overworld().setBlock(controllerPos, GTLEnhancedcoreMachines.getIntegratedUniversalFactory().defaultBlockState(), 3);
        var controller = (MultiblockControllerMachine) MetaMachine.getMachine(server.overworld(), controllerPos);
        dataHatch.addedToController(controller);
        check(dataHatch.isRecipeAvailable(recipe, new HashSet<>()), "Ordinary controller rejected module research");
        MinecraftForge.EVENT_BUS.post(new OnDatapackSyncEvent(server.getPlayerList(), null));
        check(dataHatch.isRecipeAvailable(recipe, new HashSet<>()), "Reload discarded valid module research");
        dataHatch.removedFromController(controller);
        dataHatch.markDirty();
        log("data_stick_orb_module_blank_written_filter_capability_pages_cache=OK");
    }

    private static void randomChecks() throws Exception {
        var roll = method("rollOutputSuccess", Random.class);
        int successes = 0;
        for (int draw = 0; draw < 1000; draw++) {
            int selectedDraw = draw;
            var random = new Random(0) {
                int calls;
                @Override public int nextInt(int bound) {
                    check(bound == 1000, "Success draw must be uniform across 1000 values");
                    calls++;
                    return selectedDraw;
                }
            };
            boolean result = (boolean) roll.invoke(null, random);
            check(random.calls == 1, "Success check must use exactly one draw");
            check(result == (draw == 0), "0.1% probability boundary error");
            if (result) successes++;
        }
        check(successes == 1, "Success probability is not exactly 0.1%");
        log("all_1000_single_draw_outcomes_exactly_one_success=OK");
    }

    private static void equipmentChecks(MinecraftServer server) {
        for (var entry : java.util.Map.of("stellar_confinement_fusion_reactor", "kubejs:cosmic_mainframe",
                "hyperstructural_chemical_distorter", "kubejs:exotic_mainframe").entrySet()) {
            var item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(entry.getValue()));
            check(item != null && item != Items.AIR, "Requested KubeJS mainframe not registered");
            var recipe = server.getRecipeManager().getAllRecipesFor(GTRecipeTypes.ASSEMBLY_LINE_RECIPES).stream()
                    .filter(r -> r.getOutputContents(ItemRecipeCapability.CAP).stream().anyMatch(c ->
                            java.util.Arrays.stream(ItemRecipeCapability.CAP.of(c.content).getItems())
                                    .anyMatch(s -> ForgeRegistries.ITEMS.getKey(s.getItem()).equals(
                                            new ResourceLocation("gtl_enhancedcore", entry.getKey())))))
                    .findFirst().orElseThrow();
            check(recipe.getInputContents(ItemRecipeCapability.CAP).stream().anyMatch(c ->
                    java.util.Arrays.stream(ItemRecipeCapability.CAP.of(c.content).getItems())
                            .anyMatch(s -> s.is(item) && s.getCount() == 16)), "Runtime recipe lacks new mainframe x16");
        }
        log("both_equipment_recipes_registered_with_requested_mainframes_x16=OK");
    }

    private static void tooltipChecks() {
        var ids = new ArrayList<>(IvMachineScope.NATIVE_IDS);
        ids.addAll(List.of("qft", "gravitation_shockburst"));
        for (String name : ids) {
            var definition = GTRegistries.MACHINES.get(new ResourceLocation("gtceu", name));
            var original = definition.getTooltipBuilder();
            var marker = Component.literal("audit-upstream-tooltip");
            try {
                for (int depth = 0; depth < 4; depth++) {
                    var inner = definition.getTooltipBuilder();
                    definition.setTooltipBuilder((stack, lines) -> {
                        inner.accept(stack, lines);
                        lines.add(marker);
                    });
                    var lines = new ArrayList<Component>();
                    definition.getTooltipBuilder().accept(definition.asStack(), lines);
                    boolean isolated = IvMachineScope.NATIVE_IDS.contains(name);
                    for (int index = 0; index < 4; index++) {
                        String key = "gtl_enhancedcore.tooltip.iv_native." + index;
                        check(lines.stream().filter(c -> hasKey(c, key)).count() == (isolated ? 1 : 0),
                                "Duplicate/missing isolation line at nesting depth " + depth + ": " + name);
                    }
                    check(lines.stream().filter(c -> hasKey(c, "gtl_enhancedcore.tooltip.optional_maintenance"))
                            .count() == (isolated ? 0 : 1), "Duplicate/missing maintenance hint");
                    check(lines.stream().filter(c -> c == marker).count() == depth + 1,
                            "Deduplication removed another mod's tooltip");
                }
            } finally {
                definition.setTooltipBuilder(original);
            }
        }
        for (var definition : List.of(GTLEnhancedcoreMachines.getHyperstructuralChemicalDistorter(),
                GTLEnhancedcoreMachines.getStellarConfinementFusionReactor())) {
            var lines = new ArrayList<Component>();
            definition.getTooltipBuilder().accept(definition.asStack(), lines);
            String key = "tooltip.gtl_enhancedcore." + definition.getId().getPath() + ".tips";
            var intro = lines.stream().filter(c -> hasKey(c, key)).findFirst().orElseThrow();
            check(intro.getStyle().getColor() != null
                    && intro.getStyle().getColor().getValue() == net.minecraft.ChatFormatting.AQUA.getColor(),
                    "Requested intro lost its aqua color");
        }
        log("all_ten_isolated_machines_and_optional_maintenance_tooltips_four_nested_wrappers=OK");
    }

    private static boolean hasKey(Component component, String key) {
        return component.getContents() instanceof TranslatableContents content && content.getKey().equals(key)
                || component.getSiblings().stream().anyMatch(sibling -> hasKey(sibling, key));
    }

    private static Item fish() {
        var item = ForgeRegistries.ITEMS.getValue(CausalityTerminalMachine.FISHBIG_ID);
        check(item != null && item != Items.AIR, "Fishbig missing");
        return item;
    }

    private static void mapping(Item sample) throws Exception {
        var ghosts = (ItemStackTransfer) field("ghostSlots").get(terminal);
        for (int slot = 0; slot < ghosts.getSlots(); slot++) ghosts.setStackInSlot(slot, ItemStack.EMPTY);
        ghosts.setStackInSlot(0, new ItemStack(sample));
        ghosts.setStackInSlot(1, new ItemStack(Items.DIAMOND));
        method("onGhostSlotsChanged").invoke(terminal);
    }

    private static void seed(boolean success) throws Exception {
        for (long seed = 0; seed < 10000; seed++) {
            var probe = new Random(seed);
            if ((probe.nextInt(1000) == 0) == success) {
                ((Random) field("random").get(terminal)).setSeed(seed);
                return;
            }
        }
        throw new AssertionError("Missing deterministic random fixture");
    }

    private static void stock(int fishCount, boolean sampleIsFish) throws Exception {
        clear(input);
        clear(otherInput);
        mapping(sampleIsFish ? fish() : Items.DIRT);
        if (!sampleIsFish) input.setStackInSlot(0, new ItemStack(Items.DIRT));
        fillFish(input, sampleIsFish ? 0 : 1, fishCount);
    }

    private static void stockAcrossInputs(int fishCount, boolean sampleIsFish) throws Exception {
        clear(input);
        clear(otherInput);
        mapping(sampleIsFish ? fish() : Items.DIRT);
        if (!sampleIsFish) input.setStackInSlot(0, new ItemStack(Items.DIRT));
        int firstBusCount = fishCount / 2;
        fillFish(input, sampleIsFish ? 0 : 1, firstBusCount);
        fillFish(otherInput, 0, fishCount - firstBusCount);
    }

    private static void fillFish(NotifiableItemStackHandler handler, int firstSlot, int amount) {
        int stackLimit = Math.min(64, new ItemStack(fish()).getMaxStackSize());
        check(stackLimit > 0, "Fishbig has no valid stack size");
        int remaining = amount;
        for (int slot = firstSlot; slot < handler.getSlots() && remaining > 0; slot++) {
            int inSlot = Math.min(stackLimit, remaining);
            handler.setStackInSlot(slot, new ItemStack(fish(), inSlot));
            remaining -= inSlot;
        }
        check(remaining == 0, "Input bus has too few real slots for " + amount + " Fishbig");
    }

    private static int countAcrossInputs(Item item) {
        return count(input, item) + count(otherInput, item);
    }

    private static void tick() throws Exception {
        for (var container : energy) container.setEnergyStored(container.getEnergyCapacity());
        check(terminal.getEnergyContainer().getEnergyStored() >= CausalityTerminalMachine.EUT,
                "Test fixture lacks power for a run tick");
        method("runTick").invoke(terminal);
    }

    private static void productionChecks() throws Exception {
        terminal.setWorkingEnabled(true);
        clear(output);
        int cost = CausalityTerminalMachine.FISHBIG_COST;
        check(cost == 1000, "Fishbig cost must be 1000 per attempt");
        stock(cost - 1, false);
        tick();
        check(pending(terminal).isEmpty() && count(input, fish()) == cost - 1 && count(input, Items.DIRT) == 1,
                "Insufficient Fishbig consumed materials or started work");
        stock(cost, true);
        tick();
        check(pending(terminal).isEmpty() && count(input, fish()) == cost, "Fishbig sample double-counted");
        stock(cost + 1, true);
        seed(true);
        tick();
        check(pending(terminal).getCount() == 1 && count(input, fish()) == 0,
                "Fishbig sample case failed: pending=" + pending(terminal) + ", fish=" + count(input, fish())
                        + ", inputSlots=" + input.getSlots() + ", energy=" + terminal.getEnergyContainer().getEnergyStored());
        method("cancelRun").invoke(terminal);
        stockAcrossInputs(cost - 1, false);
        tick();
        check(pending(terminal).isEmpty() && countAcrossInputs(fish()) == cost - 1
                        && countAcrossInputs(Items.DIRT) == 1,
                "Cross-bus simulation consumed an insufficient input");
        stockAcrossInputs(cost, false);
        check(count(input, fish()) < cost && count(otherInput, fish()) < cost,
                "Cross-bus fixture failed to split Fishbig between input hatches");
        seed(true);
        tick();
        check(pending(terminal).getCount() == 1 && countAcrossInputs(fish()) == 0
                        && countAcrossInputs(Items.DIRT) == 0,
                "Combined input hatches did not consume exactly one full attempt");
        method("cancelRun").invoke(terminal);
        for (boolean success : new boolean[]{true, false}) {
            stock(cost, false);
            seed(success);
            tick();
            check(count(input, fish()) == 0 && count(input, Items.DIRT) == 0, "Incorrect input deduction");
            check(pending(terminal).getCount() == 1 && field("pendingSuccess").getBoolean(terminal) == success,
                    "Run output/outcome incorrect");
            check(field("progress").getInt(terminal) == 0, "New run reused previous progress");
            int before = count(output, Items.DIAMOND);
            for (int i = 1; i <= CausalityTerminalMachine.RUN_TICKS; i++) {
                tick();
                if (i < CausalityTerminalMachine.RUN_TICKS) {
                    check(!pending(terminal).isEmpty(), "Run ended before 2000 ticks");
                    check(count(output, Items.DIAMOND) == before, "Early output");
                }
                long stored = terminal.getEnergyContainer().getEnergyStored();
                long capacity = energy.stream().mapToLong(NotifiableEnergyContainer::getEnergyCapacity).sum();
                check(stored == capacity - CausalityTerminalMachine.EUT, "Run tick used wrong energy");
            }
            check(pending(terminal).isEmpty(), "Completed run stayed active");
            check(count(output, Items.DIAMOND) - before == (success ? 1 : 0), "Incorrect output quantity");
        }
        log("1000_fish_real_64_stacks_cross_bus_1001_same_sample_2000_ticks_energy_single_output=OK");
        clear(output);
        for (int slot = 0; slot < output.getSlots(); slot++) output.setStackInSlot(slot, new ItemStack(Items.COBBLESTONE, 64));
        stock(cost, false);
        seed(true);
        tick();
        for (int i = 0; i < CausalityTerminalMachine.RUN_TICKS + 10; i++) tick();
        check(pending(terminal).getCount() == 1 && field("pendingSuccess").getBoolean(terminal),
                "Blocked output lost/rerolled result");
        check(terminal.getDiagnostic() != null, "Blocked output missing diagnostic");
        clear(output);
        tick();
        tick();
        check(count(output, Items.DIAMOND) == 1 && pending(terminal).isEmpty(), "Retry duplicated/lost output");
        stock(cost, false);
        seed(false);
        tick();
        for (var container : energy) container.setEnergyStored(0);
        method("runTick").invoke(terminal);
        check(pending(terminal).isEmpty() && count(input, fish()) == 0, "Power loss did not cancel paid run");
        check(terminal.getDiagnostic() != null, "No power diagnostic");
        clear(output);
        log("blocked_output_no_reroll_exactly_once_and_power_loss=OK");
    }

    private static void checkpoint() throws Exception {
        stock(CausalityTerminalMachine.FISHBIG_COST, false);
        seed(false);
        tick();
        for (int i = 0; i < 42; i++) tick();
        check(!field("pendingSuccess").getBoolean(terminal), "Checkpoint is not a failed run");
        ((TickableSubscription) field("runSubs").get(terminal)).unsubscribe();
        field("pendingOutput").set(savedSuccess, new ItemStack(Items.DIAMOND));
        field("pendingSuccess").setBoolean(savedSuccess, true);
        field("progress").setInt(savedSuccess, 42);
        terminal.markDirty();
        savedSuccess.markDirty();
        log("saved_failure_and_success_pending_at_tick_42=OK");
    }

    private static ItemStack pending(CausalityTerminalMachine machine) throws Exception {
        return (ItemStack) field("pendingOutput").get(machine);
    }
    private static void clear(NotifiableItemStackHandler handler) {
        for (int i = 0; i < handler.getSlots(); i++) handler.setStackInSlot(i, ItemStack.EMPTY);
    }
    private static int count(NotifiableItemStackHandler handler, Item item) {
        int result = 0;
        for (int i = 0; i < handler.getSlots(); i++) {
            var stack = handler.getStackInSlot(i);
            if (stack.is(item)) result += stack.getCount();
        }
        return result;
    }
    private static void check(boolean ok, String message) {
        if (!ok) throw new IllegalStateException(message);
        checks++;
    }
    private static void log(String message) { GTLEnhancedcore.LOGGER.info("[DATA_CAUSALITY] {}", message); }
}
