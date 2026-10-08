package com.gtl.enhancedcore.audit;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtl.enhancedcore.common.machine.BasicOreProcessingPlantMachine;
import com.gtl.enhancedcore.common.machine.LargeFurnaceMachine;
import com.gtl.enhancedcore.common.recipe.ThreadLimitedRecipeLogic;
import com.lowdragmc.lowdraglib.misc.ItemTransferList;
import com.lowdragmc.lowdraglib.side.item.IItemTransfer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import org.gtlcore.gtlcore.api.machine.trait.ILockRecipe;

public final class RecipeInputSemanticsChecks {
    private static final State[] STATES = {new State(), new State()};
    private static ServerLevel world;
    private static String mode = "combined";
    private static String phase = "setup";
    private static String failure = "";
    private static boolean initialized;
    private static boolean restart;
    private static int assertions;
    private static int diagnosticAssertions;
    private static JsonObject freshEvidence;

    private static final class State {
        private BarrelBlockEntity remainder;
        private JsonObject shared = new JsonObject();
        private JsonObject probability = new JsonObject();
        private JsonObject restored = new JsonObject();
        private boolean sharedObserved;
        private boolean sharedDelivered;
        private boolean probabilityObserved;
        private boolean probabilityDelivered;
    }

    private RecipeInputSemanticsChecks() {}

    public static void setup(MinecraftServer server, boolean fresh, String requestedMode) throws Exception {
        check(!initialized, "Input semantics fixture was initialized twice");
        check(requestedMode.equals("combined") || requestedMode.equals("probability_only"), "Unknown input semantics mode");
        mode = requestedMode;
        restart = !fresh;
        world = server.overworld();
        ProcessingRecoveryChecks.setupPaid(server, fresh);
        initialized = true;
        for (int fixtureIndex = 0; fixtureIndex < STATES.length; fixtureIndex++) {
            var position = new BlockPos(-32 + fixtureIndex * 2, 96, 2048);
            world.setChunkForced(position.getX() >> 4, position.getZ() >> 4, true);
            if (fresh) check(world.setBlock(position, Blocks.BARREL.defaultBlockState(), 3), "Cannot place real remainder barrel");
            check(world.getBlockEntity(position) instanceof BarrelBlockEntity, "Saved remainder barrel is missing");
            STATES[fixtureIndex].remainder = (BarrelBlockEntity) world.getBlockEntity(position);
            if (fresh) check(barrelCount(STATES[fixtureIndex].remainder, Items.ROTTEN_FLESH) == 0, "Fresh remainder storage is not empty");
        }
        log("setup mode=" + mode + " restart=" + restart + " real_remainder_barrels=2");
    }

    public static boolean ready() {
        return initialized && ProcessingRecoveryChecks.ready();
    }

    public static void startShared() {
        check(ready() && !restart && mode.equals("combined") && phase.equals("setup"), "Invalid shared-catalyst start");
        verifyMachines();
        for (int fixtureIndex = 0; fixtureIndex < STATES.length; fixtureIndex++) {
            var input = input(fixtureIndex);
            var output = output(fixtureIndex);
            check(empty(input) && empty(output), "Fresh shared-catalyst inventories are not empty");
            check(input.getSlots() >= 3, "Shared-catalyst fixture lacks input slots");
            unlock(fixtureIndex);
            input.setStackInSlot(0, new ItemStack(Items.APPLE, 3));
            input.setStackInSlot(1, new ItemStack(Items.CARROT, 3));
            input.setStackInSlot(2, new ItemStack(Items.STICK, 1));
            check(count(input, Items.APPLE) == 3 && count(input, Items.CARROT) == 3 && count(input, Items.STICK) == 1,
                    "Shared-catalyst supply differs from 3+3 consumables and one catalyst");
            machine(fixtureIndex).setWorkingEnabled(true);
        }
        phase = "shared_running";
        log("shared_start consumables=3+3 catalyst=1 first_batch_only=true");
    }

    public static boolean observeShared() {
        check(phase.equals("shared_running"), "Shared observation is outside its phase");
        for (int fixtureIndex = 0; fixtureIndex < STATES.length; fixtureIndex++) {
            var state = STATES[fixtureIndex];
            if (state.sharedObserved) continue;
            var logic = machine(fixtureIndex).getRecipeLogic();
            GTRecipe batch = logic.getLastRecipe();
            check(count(output(fixtureIndex), Items.GOLD_INGOT) == 0 && count(output(fixtureIndex), Items.DIAMOND) == 0,
                    "Shared batch delivered before its first committed batch was observed");
            if (!logic.isWorking() || batch == null) continue;
            int gold = ProcessingRecoveryChecks.plannedAmount(batch, Items.GOLD_INGOT);
            int diamond = ProcessingRecoveryChecks.plannedAmount(batch, Items.DIAMOND);
            state.shared.addProperty("firstBatchStarted", true);
            state.shared.addProperty("plannedGold", gold);
            state.shared.addProperty("plannedDiamond", diamond);
            state.shared.addProperty("appleRemaining", count(input(fixtureIndex), Items.APPLE));
            state.shared.addProperty("carrotRemaining", count(input(fixtureIndex), Items.CARROT));
            state.shared.addProperty("catalystRemaining", count(input(fixtureIndex), Items.STICK));
            state.shared.addProperty("progress", logic.getProgress());
            state.shared.addProperty("duration", logic.getDuration());
            log("shared_first_batch index=" + fixtureIndex + " observed=" + state.shared);
            check(gold == 3 && diamond == 3, "First shared-catalyst batch is not simultaneous 3+3: index=" + fixtureIndex + " " + state.shared);
            check(count(input(fixtureIndex), Items.APPLE) == 0 && count(input(fixtureIndex), Items.CARROT) == 0,
                    "First shared batch did not pay both consumable inputs");
            check(count(input(fixtureIndex), Items.STICK) == 1, "Shared batch consumed or duplicated its one catalyst");
            state.shared.addProperty("firstBatchVerified", true);
            state.sharedObserved = true;
        }
        return STATES[0].sharedObserved && STATES[1].sharedObserved;
    }

    public static boolean sharedDelivered() {
        check(phase.equals("shared_running"), "Shared delivery is outside its phase");
        for (int fixtureIndex = 0; fixtureIndex < STATES.length; fixtureIndex++) {
            var state = STATES[fixtureIndex];
            if (!state.sharedObserved || state.sharedDelivered) continue;
            int gold = count(output(fixtureIndex), Items.GOLD_INGOT);
            int diamond = count(output(fixtureIndex), Items.DIAMOND);
            check(gold <= 3 && diamond <= 3, "Shared batch duplicated outputs");
            if (gold != 3 || diamond != 3) continue;
            check(count(input(fixtureIndex), Items.APPLE) == 0 && count(input(fixtureIndex), Items.CARROT) == 0,
                    "Shared batch refunded consumables");
            check(count(input(fixtureIndex), Items.STICK) == 1, "Shared batch changed its nonconsumable catalyst");
            state.shared.addProperty("deliveredGold", gold);
            state.shared.addProperty("deliveredDiamond", diamond);
            state.shared.addProperty("deliveredOnce", true);
            state.sharedDelivered = true;
            machine(fixtureIndex).setWorkingEnabled(false);
        }
        boolean complete = STATES[0].sharedDelivered && STATES[1].sharedDelivered;
        if (complete) phase = "shared_complete";
        return complete;
    }

    public static void startProbability() {
        check(ready() && !restart && (phase.equals("shared_complete") || mode.equals("probability_only") && phase.equals("setup")),
                "Invalid probability-input start");
        verifyMachines();
        for (int fixtureIndex = 0; fixtureIndex < STATES.length; fixtureIndex++) {
            verifyPreservedShared(fixtureIndex);
            check(count(input(fixtureIndex), Items.ROTTEN_FLESH) == 0 && count(output(fixtureIndex), Items.EMERALD) == 0,
                    "Probability input or output exists before its first batch");
            unlock(fixtureIndex);
            input(fixtureIndex).setStackInSlot(0, new ItemStack(Items.ROTTEN_FLESH, 4));
            check(count(input(fixtureIndex), Items.ROTTEN_FLESH) == 4, "Probability fixture did not receive exactly four inputs");
            machine(fixtureIndex).setWorkingEnabled(true);
        }
        phase = "probability_running";
        log("probability_start input=4 chance=5000/10000 output_guaranteed_per_cycle=1 first_batch_only=true");
    }

    public static boolean captureProbability() {
        check(phase.equals("probability_running"), "Probability capture is outside its phase");
        for (int fixtureIndex = 0; fixtureIndex < STATES.length; fixtureIndex++) {
            var state = STATES[fixtureIndex];
            if (state.probabilityObserved) continue;
            var machine = machine(fixtureIndex);
            var logic = machine.getRecipeLogic();
            GTRecipe batch = logic.getLastRecipe();
            check(count(output(fixtureIndex), Items.EMERALD) == 0, "Probability batch finished before its first payment was observed");
            if (!logic.isWorking() || batch == null) continue;
            int planned = ProcessingRecoveryChecks.plannedAmount(batch, Items.EMERALD);
            int remaining = count(input(fixtureIndex), Items.ROTTEN_FLESH);
            state.probability.addProperty("firstBatchStarted", true);
            state.probability.addProperty("plannedEmerald", planned);
            state.probability.addProperty("remainingBeforeExtraction", remaining);
            state.probability.addProperty("actualDeduction", 4 - remaining);
            state.probability.addProperty("progress", logic.getProgress());
            state.probability.addProperty("duration", logic.getDuration());
            log("probability_first_batch index=" + fixtureIndex + " observed=" + state.probability);
            check(planned == 4, "Probability first batch is not four parallel guaranteed outputs: index=" + fixtureIndex + " " + state.probability);
            check(remaining == 2, "Four-parallel 50% first payment is not exactly two: index=" + fixtureIndex + " " + state.probability);
            check(ProcessingRecoveryChecks.plannedAmount(batch, Items.GOLD_INGOT) == 0
                    && ProcessingRecoveryChecks.plannedAmount(batch, Items.DIAMOND) == 0, "Probability first plan still contains the prior batch");
            machine.setWorkingEnabled(false);
            check(logic.getLastRecipe() == batch && logic.getDuration() > logic.getProgress(), "Pausing discarded or completed the paid probability batch");
            int extracted = extractRemainder(fixtureIndex);
            state.probability.addProperty("actualExtractedRemainder", extracted);
            state.probability.add("remainderStacks", remainderStacks(state.remainder));
            check(extracted == 2 && count(input(fixtureIndex), Items.ROTTEN_FLESH) == 0
                    && barrelCount(state.remainder, Items.ROTTEN_FLESH) == 2, "Actual probability remainder was not moved exactly once into real storage");
            verifyPreservedShared(fixtureIndex);
            inspectDiagnostic(fixtureIndex);
            state.probability.addProperty("firstPaymentVerified", true);
            state.probabilityObserved = true;
        }
        boolean complete = STATES[0].probabilityObserved && STATES[1].probabilityObserved;
        if (complete) phase = "paid_saved";
        return complete;
    }

    public static void resumeProbability(String checkpoint) {
        check(ready() && restart && phase.equals("setup"), "Invalid real-restart recovery phase");
        verifyMachines();
        freshEvidence = JsonParser.parseString(checkpoint).getAsJsonObject();
        check(freshEvidence.get("schema").getAsInt() == 1 && freshEvidence.get("passed").getAsBoolean()
                && freshEvidence.get("phase").getAsString().equals("paid_saved")
                && freshEvidence.get("mode").getAsString().equals(mode), "Checkpoint is not a verified fresh paid batch");
        var savedFixtures = freshEvidence.getAsJsonArray("fixtures");
        check(savedFixtures.size() == STATES.length, "Saved input semantics fixture count changed");
        for (int fixtureIndex = 0; fixtureIndex < STATES.length; fixtureIndex++) {
            var state = STATES[fixtureIndex];
            var saved = savedFixtures.get(fixtureIndex).getAsJsonObject();
            state.shared = saved.getAsJsonObject("shared").deepCopy();
            state.probability = saved.getAsJsonObject("probability").deepCopy();
            if (mode.equals("combined")) {
                check(state.shared.get("firstBatchVerified").getAsBoolean()
                        && state.shared.get("plannedGold").getAsInt() == 3 && state.shared.get("plannedDiamond").getAsInt() == 3
                        && state.shared.get("deliveredOnce").getAsBoolean(), "Fresh shared-catalyst first-batch proof is missing");
                state.sharedObserved = true;
                state.sharedDelivered = true;
            }
            check(state.probability.get("firstPaymentVerified").getAsBoolean()
                    && state.probability.get("plannedEmerald").getAsInt() == 4
                    && state.probability.get("actualDeduction").getAsInt() == 2
                    && state.probability.get("actualExtractedRemainder").getAsInt() == 2, "Fresh probability payment proof is missing");
            verifyPreservedShared(fixtureIndex);
            check(count(input(fixtureIndex), Items.ROTTEN_FLESH) == 0 && count(output(fixtureIndex), Items.EMERALD) == 0,
                    "Real restart refunded probability inputs or finished its paused batch");
            check(barrelCount(state.remainder, Items.ROTTEN_FLESH) == 2
                    && remainderStacks(state.remainder).equals(state.probability.getAsJsonArray("remainderStacks")),
                    "Real restart lost or replaced the actual extracted remainder stacks");
            var logic = machine(fixtureIndex).getRecipeLogic();
            GTRecipe batch = logic.getLastRecipe();
            check(batch != null && ProcessingRecoveryChecks.plannedAmount(batch, Items.EMERALD) == 4,
                    "Real restart lost the paid four-parallel batch");
            check(logic.getProgress() == state.probability.get("progress").getAsInt()
                    && logic.getDuration() == state.probability.get("duration").getAsInt(), "Real restart altered paused probability progress or duration");
            state.restored.addProperty("paidPlanPreserved", true);
            state.restored.addProperty("actualRemainderPreserved", true);
            state.restored.addProperty("progress", logic.getProgress());
            state.restored.addProperty("duration", logic.getDuration());
            inspectDiagnostic(fixtureIndex);
            state.probabilityObserved = true;
            machine(fixtureIndex).setWorkingEnabled(true);
        }
        phase = "restart_running";
        log("restart_real_nbt paid_plans=2 actual_remainder_stacks=2+2 verified=true");
    }

    public static boolean probabilityDelivered() {
        check(restart && phase.equals("restart_running"), "Probability delivery requires the real restart");
        for (int fixtureIndex = 0; fixtureIndex < STATES.length; fixtureIndex++) {
            verifyPreservedShared(fixtureIndex);
            check(count(input(fixtureIndex), Items.ROTTEN_FLESH) == 0 && barrelCount(STATES[fixtureIndex].remainder, Items.ROTTEN_FLESH) == 2,
                    "Probability recovery consumed, refunded or duplicated saved remainder");
            int delivered = count(output(fixtureIndex), Items.EMERALD);
            check(delivered <= 4, "Probability recovery delivered more than four outputs");
            if (delivered == 4) {
                STATES[fixtureIndex].probabilityDelivered = true;
                STATES[fixtureIndex].restored.addProperty("deliveredEmerald", delivered);
            }
        }
        return STATES[0].probabilityDelivered && STATES[1].probabilityDelivered;
    }

    public static int complete(int idleObservationTicks) {
        check(restart && phase.equals("restart_running") && idleObservationTicks >= 100, "Completion lacks a real restart and bounded idle observation");
        check(probabilityDelivered(), "Restarted probability batch did not finish");
        for (int fixtureIndex = 0; fixtureIndex < STATES.length; fixtureIndex++) {
            var state = STATES[fixtureIndex];
            check(!machine(fixtureIndex).getRecipeLogic().isWorking(), "Another probability batch started after remainder extraction");
            check(remainderStacks(state.remainder).equals(state.probability.getAsJsonArray("remainderStacks")), "Stored actual remainder changed during recovery");
            state.restored.addProperty("noSecondBatch", true);
            state.restored.addProperty("idleObservationTicks", idleObservationTicks);
            state.restored.addProperty("conservedInput", state.probability.get("actualDeduction").getAsInt()
                    + barrelCount(state.remainder, Items.ROTTEN_FLESH));
            check(state.restored.get("conservedInput").getAsInt() == 4, "Probability input conservation failed");
            machine(fixtureIndex).setWorkingEnabled(false);
            inspectDiagnostic(fixtureIndex);
        }
        phase = "complete";
        log("restart_delivery_exact_once verified=true assertions=" + assertions + " diagnostic_assertions=" + diagnosticAssertions);
        return assertions;
    }

    public static String resultJson() {
        var result = new JsonObject();
        result.addProperty("schema", 1);
        result.addProperty("source", "real_forge_server");
        result.addProperty("mode", mode);
        result.addProperty("controlOnly", !mode.equals("combined"));
        result.addProperty("phase", phase);
        result.addProperty("restart", restart);
        result.addProperty("passed", phase.equals("paid_saved") || phase.equals("complete"));
        result.addProperty("complete", phase.equals("complete"));
        result.addProperty("assertions", assertions);
        result.addProperty("diagnosticAssertions", diagnosticAssertions);
        result.addProperty("error", failure);
        var fixtures = new JsonArray();
        for (int fixtureIndex = 0; fixtureIndex < STATES.length; fixtureIndex++) {
            var state = STATES[fixtureIndex];
            var record = new JsonObject();
            record.addProperty("index", fixtureIndex);
            if (initialized) {
                record.addProperty("machineId", machine(fixtureIndex).getDefinition().getId().toString());
                record.addProperty("tier", machine(fixtureIndex).getTier());
                record.addProperty("threads", ((ThreadLimitedRecipeLogic) machine(fixtureIndex).getRecipeLogic()).getMultipleThreads());
                record.addProperty("inputRemaining", count(input(fixtureIndex), Items.ROTTEN_FLESH));
                record.addProperty("catalystRemaining", count(input(fixtureIndex), Items.STICK));
                record.addProperty("outputGold", count(output(fixtureIndex), Items.GOLD_INGOT));
                record.addProperty("outputDiamond", count(output(fixtureIndex), Items.DIAMOND));
                record.addProperty("outputEmerald", count(output(fixtureIndex), Items.EMERALD));
                record.addProperty("storedActualRemainder", state.remainder == null ? 0 : barrelCount(state.remainder, Items.ROTTEN_FLESH));
            }
            record.add("shared", state.shared.deepCopy());
            record.add("probability", state.probability.deepCopy());
            record.add("restored", state.restored.deepCopy());
            fixtures.add(record);
        }
        result.add("fixtures", fixtures);
        if (freshEvidence != null) result.add("freshEvidence", freshEvidence.deepCopy());
        return result.toString();
    }

    public static String failureJson(String message) {
        failure = "phase=" + phase + " " + message;
        phase = "failed";
        return resultJson();
    }

    private static void verifyMachines() {
        check(machine(0) instanceof LargeFurnaceMachine && machine(0).getTier() == GTValues.LV, "Fixture is not a real LV large furnace");
        check(machine(1) instanceof BasicOreProcessingPlantMachine && machine(1).getTier() >= GTValues.HV, "Fixture is not a real HV-or-higher ore plant");
        for (int fixtureIndex = 0; fixtureIndex < STATES.length; fixtureIndex++)
            check(machine(fixtureIndex).getRecipeLogic() instanceof ThreadLimitedRecipeLogic logic && logic.getMultipleThreads() >= 2,
                    "Fixture does not use production ThreadLimited logic with at least two threads");
    }

    private static void verifyPreservedShared(int fixtureIndex) {
        int expected = mode.equals("combined") ? 3 : 0;
        check(count(output(fixtureIndex), Items.GOLD_INGOT) == expected && count(output(fixtureIndex), Items.DIAMOND) == expected,
                "Shared first-batch actual outputs were lost or changed");
        check(count(input(fixtureIndex), Items.APPLE) == 0 && count(input(fixtureIndex), Items.CARROT) == 0,
                "Shared consumables remain or were refunded");
        check(count(input(fixtureIndex), Items.STICK) == (mode.equals("combined") ? 1 : 0), "Shared nonconsumable catalyst changed");
    }

    private static int extractRemainder(int fixtureIndex) {
        var barrel = STATES[fixtureIndex].remainder;
        for (int slot = 0; slot < barrel.getContainerSize(); slot++) check(barrel.getItem(slot).isEmpty(), "Remainder storage is occupied");
        int extracted = 0;
        int destinationSlot = 0;
        for (int slot = 0; slot < input(fixtureIndex).getSlots(); slot++) {
            var present = input(fixtureIndex).getStackInSlot(slot);
            if (!present.is(Items.ROTTEN_FLESH)) continue;
            int expectedCount = present.getCount();
            ItemStack actual = extractInternal(input(fixtureIndex), slot, expectedCount);
            check(actual.is(Items.ROTTEN_FLESH) && actual.getCount() == expectedCount, "Actual probability remainder extraction failed");
            check(destinationSlot < barrel.getContainerSize(), "Remainder storage overflow");
            extracted += actual.getCount();
            barrel.setItem(destinationSlot++, actual);
        }
        barrel.setChanged();
        return extracted;
    }

    private static ItemStack extractInternal(IItemTransfer inventory, int slot, int amount) {
        if (inventory instanceof NotifiableItemStackHandler handler)
            return handler.extractItemInternal(slot, amount, false);
        if (inventory instanceof ItemTransferList combined) {
            int localSlot = slot;
            for (IItemTransfer transfer : combined.transfers) {
                if (localSlot < transfer.getSlots()) return extractInternal(transfer, localSlot, amount);
                localSlot -= transfer.getSlots();
            }
            throw new IllegalStateException("Fixture input slot is out of range: " + slot);
        }
        throw new IllegalStateException("Unexpected fixture input handler: " + inventory.getClass().getName());
    }

    private static JsonArray remainderStacks(BarrelBlockEntity barrel) {
        var stacks = new JsonArray();
        for (int slot = 0; slot < barrel.getContainerSize(); slot++)
            if (!barrel.getItem(slot).isEmpty()) stacks.add(barrel.getItem(slot).save(new CompoundTag()).toString());
        return stacks;
    }

    private static void inspectDiagnostic(int fixtureIndex) {
        int checked = MachineDiagnosticChecks.inspect(machine(fixtureIndex), input(fixtureIndex), output(fixtureIndex));
        assertions += checked;
        diagnosticAssertions += checked;
    }

    private static void unlock(int fixtureIndex) {
        var lock = (ILockRecipe) (Object) machine(fixtureIndex).getRecipeLogic();
        lock.setLock(false);
        lock.setLockRecipe(null);
    }

    private static WorkableElectricMultiblockMachine machine(int fixtureIndex) { return ProcessingRecoveryChecks.fixtureMachine(fixtureIndex); }
    private static IItemTransfer input(int fixtureIndex) { return ProcessingRecoveryChecks.fixtureInput(fixtureIndex); }
    private static IItemTransfer output(int fixtureIndex) { return ProcessingRecoveryChecks.fixtureOutput(fixtureIndex); }

    private static int count(IItemTransfer inventory, Item item) {
        int total = 0;
        for (int slot = 0; slot < inventory.getSlots(); slot++)
            if (inventory.getStackInSlot(slot).is(item)) total += inventory.getStackInSlot(slot).getCount();
        return total;
    }

    private static int barrelCount(BarrelBlockEntity barrel, Item item) {
        int total = 0;
        for (int slot = 0; slot < barrel.getContainerSize(); slot++)
            if (barrel.getItem(slot).is(item)) total += barrel.getItem(slot).getCount();
        return total;
    }

    private static boolean empty(IItemTransfer inventory) {
        for (int slot = 0; slot < inventory.getSlots(); slot++) if (!inventory.getStackInSlot(slot).isEmpty()) return false;
        return true;
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private static void log(String message) {
        GTLEnhancedcore.LOGGER.info("[PROCESSING_INPUT_SEMANTICS] {}", message);
    }
}
