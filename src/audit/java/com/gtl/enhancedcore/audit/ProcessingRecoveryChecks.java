package com.gtl.enhancedcore.audit;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.data.GTBlocks;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ItemBusPartMachine;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.ingredient.SizedIngredient;
import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtl.enhancedcore.common.machine.GTLEnhancedcoreMachines;
import com.gtl.enhancedcore.common.machine.IndustrialSteamPlatformMachine;
import com.gtl.enhancedcore.common.recipe.MachineDiagnostics;
import com.lowdragmc.lowdraglib.misc.ItemTransferList;
import com.lowdragmc.lowdraglib.side.item.IItemTransfer;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;
import org.gtlcore.gtlcore.api.machine.trait.ILockRecipe;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient;

public final class ProcessingRecoveryChecks {
    private static final List<Fixture> FIXTURES = new ArrayList<>();
    private static Field steamStored;
    private static int assertions;
    private static final boolean[] paidBlocked = new boolean[2];
    private static final int[] paidGold = new int[2];
    private static final int[] paidDiamond = new int[2];
    private record Fixture(WorkableElectricMultiblockMachine machine, IItemTransfer input,
                           IItemTransfer output) {}

    private ProcessingRecoveryChecks() {}

    public static void setup(MinecraftServer server, boolean fresh) throws Exception {
        setup(server, fresh, false);
    }

    public static void setupPaid(MinecraftServer server, boolean fresh) throws Exception {
        setup(server, fresh, true);
    }

    private static void setup(MinecraftServer server, boolean fresh, boolean paid) throws Exception {
        steamStored = IndustrialSteamPlatformMachine.class.getDeclaredField("steamStored");
        steamStored.setAccessible(true);
        var definitions = List.of(GTLEnhancedcoreMachines.getLargeFurnace(),
                GTLEnhancedcoreMachines.getBasicOrePlant(), GTLEnhancedcoreMachines.getSteamPlatform());
        for (int index = 0; index < definitions.size(); index++)
            FIXTURES.add(place(server.overworld(), definitions.get(index), new BlockPos(index * 64, 96, 2048), fresh, index));
        check(FIXTURES.size() == 3, "Missing production processing fixture");
        if (!fresh && !paid) for (var fixture : FIXTURES) {
            check(count(fixture.output(), Items.GOLD_INGOT) == 3, "Saved processing outputs changed");
            check(count(fixture.input(), Items.APPLE) == 0, "Saved processing inputs changed");
            var lock = (ILockRecipe) (Object) fixture.machine().getRecipeLogic();
            check(lock.isLock() && lock.getLockRecipe() != null && lock.getLockRecipe().id.toString().contains("processing_low"),
                    "Successful processing lock was not saved");
        }
        log("setup production_shapes=3 restart=" + !fresh);
    }

    private static Fixture place(ServerLevel world, MultiblockMachineDefinition definition, BlockPos base,
                                 boolean fresh, int machineIndex) {
        var shape = definition.getMatchingShapes().getFirst().getBlocks();
        BlockPos core = null;
        BlockState coreState = null;
        var inputs = new ArrayList<IItemTransfer>();
        var outputs = new ArrayList<IItemTransfer>();
        var casing = machineIndex == 0 ? GTBlocks.CASING_STEEL_SOLID.get()
                : ForgeRegistries.BLOCKS.getValue(new ResourceLocation("gtceu:steam_machine_casing"));
        var ports = new LinkedHashMap<PartAbility, BlockState>();
        if (machineIndex != 2) ports.put(PartAbility.INPUT_ENERGY,
                GTMachines.ENERGY_INPUT_HATCH[machineIndex == 0 ? GTValues.LV : GTValues.HV].defaultBlockState());
        ports.put(PartAbility.IMPORT_ITEMS, GTMachines.ITEM_IMPORT_BUS[5].defaultBlockState());
        ports.put(PartAbility.EXPORT_ITEMS, GTMachines.ITEM_EXPORT_BUS[5].defaultBlockState());
        if (machineIndex != 1) ports.put(PartAbility.MAINTENANCE, GTMachines.AUTO_MAINTENANCE_HATCH.defaultBlockState());
        var existingPorts = new LinkedHashSet<PartAbility>();
        for (var plane : shape) for (var row : plane) for (var info : row) {
            if (info == null) continue;
            for (var ability : ports.keySet())
                if (ability.isApplicable(info.getBlockState().getBlock())) existingPorts.add(ability);
        }
        var missingPorts = new ArrayList<BlockState>();
        ports.forEach((ability, state) -> { if (!existingPorts.contains(ability)) missingPorts.add(state); });
        int installed = 0;
        for (int horizontal = 0; horizontal < shape.length; horizontal++)
            for (int vertical = 0; vertical < shape[horizontal].length; vertical++)
                for (int depth = 0; depth < shape[horizontal][vertical].length; depth++) {
                    var info = shape[horizontal][vertical][depth];
                    if (info == null || info.getBlockState().isAir()) continue;
                    var position = base.offset(horizontal, vertical, depth);
                    check(world.isInWorldBounds(position), "Production shape exceeds world height");
                    world.setChunkForced(position.getX() >> 4, position.getZ() >> 4, true);
                    var blockState = info.getBlockState();
                    if (blockState.is(definition.getBlock())) { core = position; coreState = blockState; continue; }
                    if (machineIndex == 2) for (int hullTier = GTValues.ULV; hullTier <= GTValues.HV; hullTier++) {
                        if (blockState.is(GTMachines.HULL[hullTier].getBlock())) {
                            blockState = GTMachines.HULL[GTValues.LV].defaultBlockState();
                            break;
                        }
                    }
                    if (blockState.is(casing) && installed < missingPorts.size()) blockState = missingPorts.get(installed++);
                    for (var port : ports.entrySet())
                        if (port.getKey().isApplicable(blockState.getBlock())) { blockState = port.getValue(); break; }
                    if (fresh) world.setBlock(position, blockState, 2 | 16);
                    var part = MetaMachine.getMachine(world, position);
                    if (part instanceof ItemBusPartMachine bus) {
                        if (PartAbility.IMPORT_ITEMS.isApplicable(world.getBlockState(position).getBlock())) inputs.add(bus.getInventory());
                        if (PartAbility.EXPORT_ITEMS.isApplicable(world.getBlockState(position).getBlock())) outputs.add(bus.getInventory());
                    }
                }
        check(core != null && installed == missingPorts.size() && !inputs.isEmpty() && !outputs.isEmpty(),
                "Production structure lacks legal fixture IO: " + definition.getId());
        if (fresh) world.setBlock(core, coreState, 2 | 16);
        var machine = (WorkableElectricMultiblockMachine) MetaMachine.getMachine(world, core);
        check(machine != null, "Missing production controller");
        machine.setWorkingEnabled(false);
        if (machineIndex == 2) {
            int furnaceMode = -1;
            for (int recipeIndex = 0; recipeIndex < machine.getRecipeTypes().length; recipeIndex++)
                if (machine.getRecipeTypes()[recipeIndex] == GTRecipeTypes.FURNACE_RECIPES) furnaceMode = recipeIndex;
            check(furnaceMode >= 0, "Steam production definition lacks furnace mode");
            if (fresh) machine.setActiveRecipeType(furnaceMode);
            check(machine.getRecipeType() == GTRecipeTypes.FURNACE_RECIPES, "Steam fixture mode changed across restart");
        }
        log(definition.getId() + " input_buses=" + inputs.size() + " output_buses=" + outputs.size());
        return new Fixture(machine, new ItemTransferList(inputs), new ItemTransferList(outputs));
    }

    public static boolean ready() {
        return FIXTURES.size() == 3 && FIXTURES.stream().allMatch(fixture -> fixture.machine().isRecipeLogicAvailable());
    }

    public static WorkableElectricMultiblockMachine fixtureMachine(int fixtureIndex) {
        return FIXTURES.get(fixtureIndex).machine();
    }

    public static IItemTransfer fixtureInput(int fixtureIndex) {
        return FIXTURES.get(fixtureIndex).input();
    }

    public static IItemTransfer fixtureOutput(int fixtureIndex) {
        return FIXTURES.get(fixtureIndex).output();
    }

    public static void status() {
        for (var fixture : FIXTURES) {
            var machine = fixture.machine();
            var state = machine.getMultiblockState();
            log(machine.getDefinition().getId() + " formed=" + machine.isFormed() + " available="
                    + machine.isRecipeLogicAvailable() + " facing=" + machine.getFrontFacing() + " error="
                    + (state.error == null ? "none" : Component.Serializer.toJson(state.error.getErrorInfo()))
                    + " checkedPosition=" + state.getPos());
        }
    }

    public static void supply() {
        for (var fixture : FIXTURES) if (!(fixture.machine() instanceof IndustrialSteamPlatformMachine)) {
            var energy = fixture.machine().getEnergyContainer();
            if (energy != null) energy.addEnergy(energy.getEnergyCapacity());
        }
    }

    public static void startBlocked() throws Exception {
        check(ready(), "Production processing machines did not form");
        for (var fixture : FIXTURES) {
            clear(fixture.input());
            for (int slot = 0; slot < fixture.output().getSlots(); slot++)
                fixture.output().setStackInSlot(slot, new ItemStack(Items.STONE, 64));
            fixture.input().setStackInSlot(0, new ItemStack(Items.APPLE, 5));
            var lock = (ILockRecipe) (Object) fixture.machine().getRecipeLogic();
            lock.setLock(true);
            lock.setLockRecipe(null);
            if (fixture.machine() instanceof IndustrialSteamPlatformMachine steam) {
                steamStored.setLong(steam, 2000);
                steam.markDirty();
            }
            fixture.machine().setWorkingEnabled(true);
        }
    }

    public static void checkBlocked() {
        for (var fixture : FIXTURES) {
            check(count(fixture.input(), Items.APPLE) == 5, "Full outputs consumed processing inputs: " + fixture.machine().getDefinition().getId());
            check(((ILockRecipe) (Object) fixture.machine().getRecipeLogic()).getLockRecipe() == null,
                    "A rejected candidate acquired an automatic lock");
            diagnostic(fixture, "gtl_enhancedcore.diagnostic.output");
            if (fixture.machine() instanceof IndustrialSteamPlatformMachine steam)
                check(steam.getSteamStoredMb() == 2000, "Rejected output charged steam");
            clear(fixture.output());
        }
        log("blocked_output_gui_jade=PASS");
    }

    public static void checkNormal() {
        for (var fixture : FIXTURES) {
            check(count(fixture.input(), Items.APPLE) == 0, "Input remained after output recovery");
            check(count(fixture.output(), Items.GOLD_INGOT) == 5, "Output recovery lost or duplicated processing results");
            check(count(fixture.output(), Items.DIAMOND) == 0, "Rejected same-leaf recipe ran");
            var lock = (ILockRecipe) (Object) fixture.machine().getRecipeLogic();
            check(lock.getLockRecipe() != null && lock.getLockRecipe().id.toString().contains("processing_low"),
                    "Automatic lock did not choose the actually started recipe");
            lock.setLock(false);
            lock.setLockRecipe(null);
            if (fixture.machine() instanceof IndustrialSteamPlatformMachine steam)
                check(steam.getSteamStoredMb() == 1000, "Successful steam batch did not cost exactly 1000mB: stored="
                        + steam.getSteamStoredMb() + " hull=" + steam.getHullTier() + " voltage=" + steam.getOverclockVoltage());
            fixture.input().setStackInSlot(0, new ItemStack(Items.BEETROOT));
        }
        log("normal_and_rejected_leaf=PASS");
    }

    public static void checkVoltage() {
        for (var fixture : FIXTURES) {
            check(count(fixture.input(), Items.BEETROOT) == 1, "Voltage rejection consumed ingredients");
            diagnostic(fixture, "gtl_enhancedcore.diagnostic.voltage");
            fixture.input().setStackInSlot(0, new ItemStack(Items.POTATO));
        }
        log("voltage_detail_gui_jade=PASS");
    }

    public static void checkCondition() {
        for (var fixture : FIXTURES) {
            check(count(fixture.input(), Items.POTATO) == 1, "Dimension rejection consumed ingredients");
            diagnostic(fixture, "dimension");
            clear(fixture.output());
            fixture.input().setStackInSlot(0, new ItemStack(Items.APPLE, 3));
            ((ILockRecipe) (Object) fixture.machine().getRecipeLogic()).setLock(true);
        }
        log("original_condition_gui_jade=PASS");
    }

    private static void assertRecovered() {
        for (var fixture : FIXTURES) {
            check(count(fixture.input(), Items.APPLE) == 0 && count(fixture.output(), Items.GOLD_INGOT) == 3,
                    "Machine did not recover from rejected candidates without restart");
            if (fixture.machine() instanceof IndustrialSteamPlatformMachine steam)
                check(steam.getSteamStoredMb() == 0, "Steam charged a rejected candidate or missed a successful batch");
        }
    }

    public static void startAdmission() {
        assertRecovered();
        for (var fixture : FIXTURES) {
            if (fixture.machine() instanceof IndustrialSteamPlatformMachine) {
                fixture.machine().setWorkingEnabled(false);
                continue;
            }
            check(((com.gtl.enhancedcore.common.recipe.ThreadLimitedRecipeLogic) fixture.machine().getRecipeLogic()).getMultipleThreads() >= 2,
                    "Combined admission needs at least two actual recipe threads");
            for (int slot = 0; slot < fixture.output().getSlots(); slot++)
                fixture.output().setStackInSlot(slot, new ItemStack(Items.STONE, 64));
            fixture.output().setStackInSlot(0, new ItemStack(Items.GOLD_INGOT, 60));
            fixture.input().setStackInSlot(0, new ItemStack(Items.APPLE, 3));
            fixture.input().setStackInSlot(1, new ItemStack(Items.CARROT, 3));
            var lock = (ILockRecipe) (Object) fixture.machine().getRecipeLogic();
            lock.setLock(false);
            lock.setLockRecipe(null);
        }
        log("admission_start recipes=2 input_cycles=6 output_capacity=4");
    }

    public static void checkJointOutput() {
        for (var fixture : FIXTURES) {
            if (fixture.machine() instanceof IndustrialSteamPlatformMachine) continue;
            check(count(fixture.output(), Items.GOLD_INGOT) == 64, "Combined output admission did not fill exactly four spaces");
            check(count(fixture.input(), Items.APPLE) + count(fixture.input(), Items.CARROT) == 2,
                    "Combined output rejection consumed uncommitted ingredients");
            diagnostic(fixture, "gtl_enhancedcore.diagnostic.output");
            clear(fixture.output());
        }
        log("combined_capacity_single_commit=PASS");
    }

    public static void checkJointRecovery() {
        for (var fixture : FIXTURES) {
            if (fixture.machine() instanceof IndustrialSteamPlatformMachine) continue;
            check(count(fixture.input(), Items.APPLE) + count(fixture.input(), Items.CARROT) == 0,
                    "Remaining combined inputs did not resume without restart");
            check(count(fixture.output(), Items.GOLD_INGOT) == 2, "Combined recovery duplicated or lost results");
            clear(fixture.output());
            fixture.input().setStackInSlot(0, new ItemStack(Items.MELON_SLICE, 3));
        }
        log("combined_recovery=PASS shared_input_cycles=3");
    }

    public static void checkSharedInputs() {
        for (var fixture : FIXTURES) {
            if (fixture.machine() instanceof IndustrialSteamPlatformMachine) continue;
            check(count(fixture.input(), Items.MELON_SLICE) == 0, "Shared input admission did not start");
            check(count(fixture.output(), Items.GOLD_INGOT) + count(fixture.output(), Items.IRON_INGOT) == 3,
                    "Two recipes charged or produced the same shared ingredients twice");
            clear(fixture.output());
            fixture.input().setStackInSlot(0, new ItemStack(Items.ROTTEN_FLESH, 3));
        }
        log("shared_input_single_commit=PASS probability_cycles=3");
    }

    public static void checkChanceOutputs() {
        for (var fixture : FIXTURES) {
            if (fixture.machine() instanceof IndustrialSteamPlatformMachine) continue;
            check(count(fixture.input(), Items.ROTTEN_FLESH) == 0, "Probability batch did not start");
            check(count(fixture.output(), Items.GOLD_INGOT) == 3, "Probability batch changed guaranteed outputs");
            check(count(fixture.output(), Items.DIAMOND) <= 3, "Probability outputs were rolled more than once");
            for (int slot = 0; slot < fixture.output().getSlots(); slot++)
                if (fixture.output().getStackInSlot(slot).is(Items.GOLD_INGOT)) fixture.output().setStackInSlot(slot, ItemStack.EMPTY);
            fixture.input().setStackInSlot(0, new ItemStack(Items.APPLE, 3));
            var lock = (ILockRecipe) (Object) fixture.machine().getRecipeLogic();
            lock.setLock(true);
            lock.setLockRecipe(null);
        }
        log("settled_probability_and_relock=PASS");
    }

    public static int chanceCount(int fixtureIndex) {
        return count(FIXTURES.get(fixtureIndex).output(), Items.DIAMOND);
    }

    public static void verifySavedChance(int furnaceCount, int oreCount) {
        check(chanceCount(0) == furnaceCount && chanceCount(1) == oreCount, "Restart changed settled probability outputs");
        log("saved_probability_outputs=PASS furnace=" + furnaceCount + " ore=" + oreCount);
    }

    public static void startPaidBatch() {
        check(ready(), "Paid processing fixtures did not form");
        for (int fixtureIndex = 0; fixtureIndex < 2; fixtureIndex++) {
            var fixture = FIXTURES.get(fixtureIndex);
            clear(fixture.input());
            clear(fixture.output());
            var lock = (ILockRecipe) (Object) fixture.machine().getRecipeLogic();
            lock.setLock(false);
            lock.setLockRecipe(null);
            fixture.input().setStackInSlot(0, new ItemStack(Items.ROTTEN_FLESH, 3));
            fixture.machine().setWorkingEnabled(true);
        }
        log("paid_batch_start=PASS");
    }

    public static boolean blockPaidOutputs() {
        for (int fixtureIndex = 0; fixtureIndex < 2; fixtureIndex++) {
            if (paidBlocked[fixtureIndex]) continue;
            var fixture = FIXTURES.get(fixtureIndex);
            var logic = fixture.machine().getRecipeLogic();
            GTRecipe batch = logic.getLastRecipe();
            if (!logic.isWorking() || batch == null || count(fixture.input(), Items.ROTTEN_FLESH) != 0) continue;
            check(count(fixture.output(), Items.GOLD_INGOT) == 0 && count(fixture.output(), Items.DIAMOND) == 0,
                    "Paid batch finished before the mid-cycle output block");
            paidGold[fixtureIndex] = plannedAmount(batch, Items.GOLD_INGOT);
            paidDiamond[fixtureIndex] = plannedAmount(batch, Items.DIAMOND);
            check(paidGold[fixtureIndex] == 3 && paidDiamond[fixtureIndex] <= 3,
                    "Paid batch was not settled to three guaranteed cycles");
            for (int slot = 0; slot < fixture.output().getSlots(); slot++)
                fixture.output().setStackInSlot(slot, new ItemStack(Items.STONE, 64));
            paidBlocked[fixtureIndex] = true;
            log("paid_output_block index=" + fixtureIndex + " gold=" + paidGold[fixtureIndex] + " diamond=" + paidDiamond[fixtureIndex]);
        }
        return paidBlocked[0] && paidBlocked[1];
    }

    public static void verifyPaidWaiting() {
        for (int fixtureIndex = 0; fixtureIndex < 2; fixtureIndex++) {
            var fixture = FIXTURES.get(fixtureIndex);
            check(paidBlocked[fixtureIndex] && fixture.machine().getRecipeLogic().getLastRecipe() != null,
                    "Output backpressure discarded a paid batch");
            check(count(fixture.input(), Items.ROTTEN_FLESH) == 0 && count(fixture.output(), Items.GOLD_INGOT) == 0,
                    "Paid output wait repeated input payment or leaked output");
            diagnostic(fixture, "gtl_enhancedcore.diagnostic.output");
        }
        log("paid_output_wait_and_jade=PASS");
    }

    public static int paidGold(int fixtureIndex) { return paidGold[fixtureIndex]; }
    public static int paidDiamond(int fixtureIndex) { return paidDiamond[fixtureIndex]; }

    public static void resumePaidBatch(int furnaceGold, int furnaceDiamond, int oreGold, int oreDiamond) {
        paidGold[0] = furnaceGold;
        paidDiamond[0] = furnaceDiamond;
        paidGold[1] = oreGold;
        paidDiamond[1] = oreDiamond;
        check(ready(), "Paid processing parts did not reload");
        for (int fixtureIndex = 0; fixtureIndex < 2; fixtureIndex++) {
            var fixture = FIXTURES.get(fixtureIndex);
            GTRecipe batch = fixture.machine().getRecipeLogic().getLastRecipe();
            check(batch != null, "Real JVM restart lost the paid synthetic recipe");
            check(plannedAmount(batch, Items.GOLD_INGOT) == paidGold[fixtureIndex]
                    && plannedAmount(batch, Items.DIAMOND) == paidDiamond[fixtureIndex],
                    "Real restart rerolled or changed settled probability outputs");
            check(count(fixture.input(), Items.ROTTEN_FLESH) == 0 && count(fixture.output(), Items.GOLD_INGOT) == 0,
                    "Restart refunded inputs or delivered into full outputs");
            clear(fixture.output());
            fixture.machine().setWorkingEnabled(true);
        }
        log("paid_recipe_nbt_and_probability=PASS");
    }

    public static int completePaidBatch() {
        for (int fixtureIndex = 0; fixtureIndex < 2; fixtureIndex++) {
            var fixture = FIXTURES.get(fixtureIndex);
            check(count(fixture.output(), Items.GOLD_INGOT) == paidGold[fixtureIndex]
                    && count(fixture.output(), Items.DIAMOND) == paidDiamond[fixtureIndex],
                    "Restarted paid batch did not deliver its exact settled outputs once");
            check(count(fixture.input(), Items.ROTTEN_FLESH) == 0, "Restarted paid batch consumed another set of inputs");
            fixture.machine().setWorkingEnabled(false);
        }
        log("paid_restart_exact_delivery=PASS assertions=" + assertions);
        return assertions;
    }

    public static int plannedAmount(GTRecipe batch, Item item) {
        int total = 0;
        for (var content : batch.outputs.getOrDefault(ItemRecipeCapability.CAP, List.of())) {
            var ingredient = ItemRecipeCapability.CAP.of(content.content);
            if (!ingredient.test(new ItemStack(item))) continue;
            long amount = ingredient instanceof LongIngredient large ? large.getActualAmount()
                    : ingredient instanceof SizedIngredient sized ? sized.getAmount() : 1;
            total += Math.toIntExact(amount);
        }
        return total;
    }

    public static int complete() {
        assertRecovered();
        for (var fixture : FIXTURES) fixture.machine().setWorkingEnabled(false);
        log("COMPLETE assertions=" + assertions + " full_production_shapes=3");
        return assertions;
    }

    private static void diagnostic(Fixture fixture, String expected) {
        var details = MachineDiagnostics.currentDetails(fixture.machine());
        String encoded = details.stream().map(Component.Serializer::toJson).toList().toString();
        check(encoded.contains(expected), "Wrong processing rejection: " + fixture.machine().getDefinition().getId() + " " + encoded);
        assertions += MachineDiagnosticChecks.inspect(fixture.machine(), fixture.input(), fixture.output());
        log(fixture.machine().getDefinition().getId() + " diagnostic=" + encoded);
    }

    private static int count(IItemTransfer inventory, Item item) {
        int count = 0;
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            var stack = inventory.getStackInSlot(slot);
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }

    private static void clear(IItemTransfer inventory) {
        for (int slot = 0; slot < inventory.getSlots(); slot++) inventory.setStackInSlot(slot, ItemStack.EMPTY);
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private static void log(String message) {
        GTLEnhancedcore.LOGGER.info("[PROCESSING_RECOVERY] {}", message);
    }
}
