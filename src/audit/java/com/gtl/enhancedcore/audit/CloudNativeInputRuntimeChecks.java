package com.gtl.enhancedcore.audit;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.*;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.trait.*;
import com.gregtechceu.gtceu.api.misc.EnergyContainerList;
import com.gregtechceu.gtceu.api.recipe.*;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ItemBusPartMachine;
import com.gtl.enhancedcore.common.cloud.CloudPatternBufferMachine;
import com.gtl.enhancedcore.common.cloud.store.CloudMachineIdentity;
import com.gtl.enhancedcore.common.recipe.iv.*;
import com.gtl.enhancedcore.mixin.gtceu.MultiblockPartControllerPositionsAccessor;
import com.lowdragmc.lowdraglib.utils.TrackedDummyWorld;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachine;
import org.gtlcore.gtlcore.api.machine.trait.IRecipeCapabilityMachine;
import org.gtlcore.gtlcore.api.machine.trait.RecipeHandlePart;

/** Actual transformed native/order executors and inventories. The sole cloud readiness seam
 * replaces authority/AE readiness; output remains pending, so this is not an ME delivery test. */
public final class CloudNativeInputRuntimeChecks {
    private static final AEItemKey NATIVE_INPUT = AEItemKey.of(Items.CARROT);
    private static final AEItemKey CLOUD_INPUT = AEItemKey.of(Items.APPLE);
    private static final AEItemKey CLOUD_OUTPUT = AEItemKey.of(Items.DIAMOND);
    private CloudNativeInputRuntimeChecks() {}

    public static List<String> run() throws Exception {
        var checks = new ArrayList<String>();
        run(checks);
        return checks;
    }

    public static void run(List<String> checks) throws Exception {
        var failures = new ArrayList<Throwable>();
        scenario("recognition", () -> recognition(checks), checks, failures);
        scenario("ordinary input bus", () -> mixed(false, checks), checks, failures);
        scenario("ordinary pattern", () -> mixed(true, checks), checks, failures);
        scenario("lookup finally restoration", () -> finallyRestoration(checks), checks, failures);
        scenario("reset and callback interruption", () -> resetAndInterrupt(checks), checks, failures);
        scenario("callback pause and exception", () -> callbackPause(checks), checks, failures);
        scenario("Core Multiple subtype", () -> multipleRecipeSubclass(checks), checks, failures);
        scenario("native whitelist host", () -> nativeWhitelistHost(checks), checks, failures);
        scenario("self-owned IvRecipeLogic", () -> ownIvRecipeLogicHost(checks), checks, failures);
        scenario("self-owned suspend after finish", () -> ownIvSuspendAfterFinish(checks), checks, failures);
        if (!failures.isEmpty()) {
            var failure = new AssertionError("Native input audit failed " + failures.size() + " independent scenarios: "
                    + failures.stream().map(Throwable::getMessage).toList());
            failures.forEach(failure::addSuppressed);
            throw failure;
        }
    }

    @FunctionalInterface private interface Scenario { void run() throws Exception; }
    private static void scenario(String label, Scenario scenario, List<String> checks, List<Throwable> failures) {
        try { scenario.run(); }
        catch (Throwable failure) {
            var named = new AssertionError(label + ": " + failure, failure);
            failures.add(named); checks.add("FAIL native-input scenario " + label + ": " + failure);
            named.printStackTrace();
        }
    }

    private static void recognition(List<String> checks) throws Exception {
        try (var fixture = new Fixture(false)) {
            var machine = fixture.base.machine();
            var parts = new ArrayList<>(machine.getParts());
            parts.clear(); parts.add(fixture.cloud); parts.add(fixture.output);
            fixture.parts(parts);
            check(!IvBuffers.genericCloudNativeInputs(machine), "cloud plus output bus does not invent a physical input lane", checks);
            var stock = (IMultiPart) fixture.place("gtceu:me_stocking_input_bus", 5).getMetaMachine();
            parts.add(stock);
            fixture.parts(parts);
            check(IvBuffers.genericCloudNativeInputs(machine), "actual ME stock input bus IN handler enables native lane", checks);
            parts.remove(stock);
            var pattern = (IMultiPart) fixture.place("gtceu:me_extend_pattern_buffer", 6).getMetaMachine();
            parts.add(pattern);
            fixture.parts(parts);
            check(IvBuffers.genericCloudNativeInputs(machine), "actual ordinary pattern buffer IN handlers enable native lane", checks);
            check(IvBuffers.collect(machine).equals(List.of(fixture.cloud)),
                    "ordinary pattern assembly is excluded from cloud job collection", checks);
        }
    }

    private static void mixed(boolean pattern, List<String> checks) throws Exception {
        String label = pattern ? "ordinary pattern" : "ordinary input bus";
        try (var fixture = new Fixture(pattern)) {
            var logic = fixture.base.machine().getRecipeLogic(); var access = (IvNativeAccess) logic;
            var first = fixture.cloudOrder(0); var second = fixture.cloudOrder(1);
            fixture.tick();
            check(access.iv$nativeInputsActive() && logic.getLastRecipe() != null && logic.getProgress() == 0,
                    label + " starts a complete paid native batch through actual findAndHandleRecipe; " + fixture.describe(), checks);
            GTRecipe committed = logic.getLastRecipe(); int duration = logic.getDuration();
            check(fixture.nativeStock() == 3 && first.inventory.equals(Map.of(CLOUD_INPUT, 1L))
                    && second.inventory.equals(Map.of(CLOUD_INPUT, 1L)),
                    label + " native admission consumes one carrot and no isolated apples", checks);
            long beforeEnergy = fixture.energy.getEnergyStored();
            fixture.tick();
            check(logic.getProgress() == 1 && fixture.energy.getEnergyStored() == beforeEnergy - RecipeHelper.getInputEUt(committed),
                    label + " next server tick advances native progress and pays actual EU exactly once", checks);
            fixture.energy.setEnergyStored(0);
            for (int tick = 0; tick < 3; tick++) fixture.tick();
            check(access.iv$nativeInputsActive() && logic.getLastRecipe() == committed && logic.getDuration() == duration
                    && fixture.nativeStock() == 3 && !first.active() && !second.active(),
                    label + " missing energy retains paid native batch under upstream damping without changing executors", checks);
            fixture.energy.setEnergyStored(100000);
            if (!pattern) {
                for (int slot = 0; slot < fixture.output.getInventory().getSlots(); slot++)
                    fixture.output.getInventory().setStackInSlot(slot, new ItemStack(Items.COBBLESTONE, 64));
                fixture.until(() -> logic.getProgress() >= logic.getDuration(), 100, "native output reaches blocked finish");
                for (int tick = 0; tick < 4; tick++) fixture.tick();
                check(access.iv$nativeInputsActive() && logic.getLastRecipe() == committed && logic.getProgress() == duration
                        && fixture.nativeStock() == 3 && !first.active() && !second.active(),
                        label + " full native output preserves completed progress and prevents cloud takeover", checks);
                for (int slot = 0; slot < fixture.output.getInventory().getSlots(); slot++)
                    fixture.output.getInventory().setStackInSlot(slot, ItemStack.EMPTY);
                fixture.tick();
            } else {
                // Ordinary Core patterns own their native output buffer. Its IO.BOTH
                // path is intentionally not a bounded physical output-bus simulation.
                fixture.until(() -> !access.iv$nativeInputsActive(), 100, "native pattern batch completes");
            }
            check(!access.iv$nativeInputsActive() && fixture.outputCount(Items.GOLD_NUGGET) == 1 && fixture.nativeStock() == 3,
                    label + " finish delivers once and suppresses native automatic next-batch setup", checks);
            fixture.until(() -> first.active() || second.active(), 30, "cloud receives next turn");
            var active = first.active() ? first : second; var waiting = first.active() ? second : first;
            check(!access.iv$nativeInputsActive() && active.elapsed > 0 && active.inventory.isEmpty()
                    && waiting.inventory.equals(Map.of(CLOUD_INPUT, 1L)) && fixture.nativeStock() == 3,
                    label + " next turn runs actual cloud engine with only its own apple", checks);
            fixture.until(() -> active.completedOperations == 1, 100, "first cloud batch completes");
            check(active.pending.equals(Map.of(CLOUD_OUTPUT, 1L)) && fixture.outputCount(Items.GOLD_NUGGET) == 1,
                    label + " cloud completion is settled once in pending without physical-output leakage", checks);
            fixture.until(access::iv$nativeInputsActive, 30, "native receives turn between waiting cloud jobs");
            check(fixture.nativeStock() == 2 && !waiting.active() && waiting.inventory.equals(Map.of(CLOUD_INPUT, 1L)),
                    label + " native regains a complete turn before queued cloud job starts", checks);
            fixture.until(() -> waiting.completedOperations == 1, 160, "second cloud batch runs after native turn");
            check(fixture.outputCount(Items.GOLD_NUGGET) == 2 && fixture.nativeStock() == 2
                    && waiting.inventory.isEmpty() && waiting.pending.equals(Map.of(CLOUD_OUTPUT, 1L))
                    && active.completedOperations == 1 && logic.isWorkingEnabled(),
                    label + " repeated native/cloud turns preserve exact materials and single production", checks);
            check(!access.iv$message().equals("gtl_enhancedcore.diagnostic.iv_legacy")
                    && !access.iv$message().equals("gtl_enhancedcore.diagnostic.iv_failure"),
                    label + " paid native batch never enters legacy rejection or machine fault", checks);
        }
    }

    private static void finallyRestoration(List<String> checks) throws Exception {
        try (var fixture = new Fixture(false)) {
            var machine = fixture.base.machine(); var logic = machine.getRecipeLogic();
            var nativeHandlers = machine.getCapabilitiesProxy().get(IO.IN, ItemRecipeCapability.CAP);
            var delegate = nativeHandlers.getFirst();
            var capabilities = (IRecipeCapabilityMachine) machine;
            var previousShared = capabilities.getSharedRecipeHandlePart();
            var marker = new IllegalStateException("injected native lookup failure");
            var poisoned = (IRecipeHandlerTrait<?>) Proxy.newProxyInstance(CloudNativeInputRuntimeChecks.class.getClassLoader(),
                    new Class<?>[]{IRecipeHandlerTrait.class}, (proxy, method, arguments) -> {
                        if (method.getName().equals("getContents")) throw marker;
                        try { return method.invoke(delegate, arguments); }
                        catch (InvocationTargetException failure) { throw failure.getCause(); }
                    });
            machine.getCapabilitiesProxy().put(IO.IN, ItemRecipeCapability.CAP, List.of(poisoned));
            field(machine, "sharedInputRecipeHandlePart").set(machine, RecipeHandlePart.of(IO.IN, List.of(poisoned)));
            try {
                fixture.tick(); throw new AssertionError("native lookup failure did not propagate");
            } catch (IllegalStateException failure) { check(failure == marker, "native lookup failure propagates unchanged", checks); }
            check(!(boolean) field(logic, "iv$passivePatternLane").get(logic)
                    && !(boolean) field(logic, "iv$finishingNativeInputs").get(logic),
                    "native lookup exception restores every temporary lane flag in finally", checks);
            machine.getCapabilitiesProxy().put(IO.IN, ItemRecipeCapability.CAP, nativeHandlers);
            field(machine, "sharedInputRecipeHandlePart").set(machine, previousShared);
            fixture.tick();
            check(((IvNativeAccess) logic).iv$nativeInputsActive(), "next native lookup succeeds after callback exception", checks);
        }
    }

    private static void resetAndInterrupt(List<String> checks) throws Exception {
        try (var fixture = new Fixture(false)) {
            var logic = fixture.base.machine().getRecipeLogic(); var access = (IvNativeAccess) logic;
            var order = fixture.cloudOrder(0); fixture.tick();
            check(access.iv$nativeInputsActive(), "reset fixture starts actual paid native batch", checks);
            logic.resetRecipeLogic();
            check(!access.iv$nativeInputsActive(), "native lifecycle reset releases native lane flag", checks);
            fixture.until(order::active, 30, "cloud resumes after native reset");
            check(!access.iv$nativeInputsActive() && order.elapsed > 0 && logic.isWorkingEnabled(),
                    "cloud executor can resume after native lifecycle reset", checks);
        }
        try (var fixture = new Fixture(false)) {
            var machine = fixture.base.machine(); var logic = machine.getRecipeLogic(); var access = (IvNativeAccess) logic;
            var order = fixture.cloudOrder(0); fixture.tick();
            var definition = machine.getDefinition(); var original = definition.getOnWorking();
            try {
                definition.setOnWorking(ignored -> false);
                fixture.tick();
            } finally { definition.setOnWorking(original); }
            check(!access.iv$nativeInputsActive() && !order.active() && logic.isWorkingEnabled(),
                    "upstream onWorking false interrupts native batch and releases lane without self-suspending", checks);
            fixture.until(order::active, 30, "cloud resumes after native callback interruption");
            check(order.elapsed > 0, "cloud executor runs after upstream native interruption", checks);
        }
    }

    private static void callbackPause(List<String> checks) throws Exception {
        try (var fixture = new Fixture(false)) {
            var machine = fixture.base.machine(); var logic = machine.getRecipeLogic(); var access = (IvNativeAccess) logic;
            var order = fixture.cloudOrder(0); fixture.tick();
            var committed = logic.getLastRecipe();
            var definition = machine.getDefinition(); var original = definition.getOnWorking();
            try {
                definition.setOnWorking(ignored -> { logic.setWorkingEnabled(false); return true; });
                fixture.tick(); int paused = logic.getProgress();
                check(!logic.isWorkingEnabled() && access.iv$nativeInputsActive() && logic.getLastRecipe() == committed,
                        "upstream callback setWorkingEnabled false is retained outside temporary native lane", checks);
                fixture.tick(); fixture.tick();
                check(logic.getProgress() == paused && !order.active() && fixture.nativeStock() == 3,
                        "callback pause retains paid native recipe and never starts cloud work", checks);
            } finally { definition.setOnWorking(original); }
            logic.setWorkingEnabled(true);
            var marker = new IllegalStateException("injected native onWorking failure");
            try {
                definition.setOnWorking(ignored -> { throw marker; });
                try { fixture.tick(); throw new AssertionError("native callback exception did not propagate"); }
                catch (IllegalStateException failure) { check(failure == marker, "native onWorking exception propagates unchanged", checks); }
                check(!(boolean) field(logic, "iv$passivePatternLane").get(logic)
                        && access.iv$nativeInputsActive() && logic.getLastRecipe() == committed,
                        "native callback exception restores lane flag while retaining paid batch", checks);
            } finally { definition.setOnWorking(original); }
            fixture.until(order::active, 100, "native resumes and cloud receives next turn");
            check(fixture.outputCount(Items.GOLD_NUGGET) == 1 && order.elapsed > 0 && logic.isWorkingEnabled(),
                    "explicit resume completes native output once and reopens cloud execution", checks);
        }
    }

    private static void multipleRecipeSubclass(List<String> checks) throws Exception {
        try (var fixture = new Fixture(false, "gtceu:desulfurizer")) {
            var logic = fixture.base.machine().getRecipeLogic(); var access = (IvNativeAccess) logic;
            check(logic instanceof org.gtlcore.gtlcore.common.machine.trait.MultipleRecipesLogic,
                    "registered desulfurizer retains actual Core MultipleRecipesLogic subtype", checks);
            fixture.input.getInventory().setStackInSlot(0, NATIVE_INPUT.toStack(1));
            var order = fixture.cloudOrder(0); fixture.tick();
            check(access.iv$nativeInputsActive() && fixture.nativeStock() == 0 && !order.active(),
                    "Core Multiple subtype admits native physical batch without touching isolated apple", checks);
            fixture.input.getInventory().setStackInSlot(0, NATIVE_INPUT.toStack(3));
            fixture.until(() -> !access.iv$nativeInputsActive(), 240, "Multiple subtype native batch completes");
            check(fixture.outputCount(Items.GOLD_NUGGET) == 1 && fixture.nativeStock() == 3,
                    "Core Multiple finish yields before getRecipe and does not preconsume ready next-batch carrots", checks);
            fixture.until(order::active, 40, "Multiple subtype yields next turn to cloud order");
            check(fixture.nativeStock() == 3 && order.inventory.isEmpty() && order.elapsed > 0,
                    "real Core Multiple scheduler hands control to isolated engine without hidden native deduction", checks);
        }
    }

    private static void nativeWhitelistHost(List<String> checks) throws Exception {
        try (var fixture = new Fixture(false, "gtceu:holy_separator")) {
            var machine = fixture.base.machine(); var logic = machine.getRecipeLogic(); var access = (IvNativeAccess) logic;
            check(IvMachineScope.nativeTarget(machine) && !IvBuffers.genericCloudTarget(machine)
                    && IvBuffers.genericCloudNativeInputs(machine),
                    "actual holy separator is an IV nativeTarget host with attached cloud and a physical input lane", checks);
            var ordinary = (MEPatternBufferPartMachine) fixture.place("gtceu:me_extend_pattern_buffer", 11).getMetaMachine();
            var parts = new ArrayList<>(machine.getParts());
            parts.add(ordinary); fixture.parts(parts);
            check(IvBuffers.collect(machine).equals(List.of(fixture.cloud)) && !IvBuffers.isolated(ordinary)
                    && IvBuffers.state(ordinary) == null,
                    "nativeTarget plus cloud never enrolls attached ordinary pattern assembly as an isolated job buffer", checks);
            parts.remove(ordinary); fixture.parts(parts);
            int threads = IvMachineScope.threads(machine), parallel = IvMachineScope.parallel(machine);
            long budget = IvMachineScope.budget(machine);
            check(threads == 192 && parallel == 1 && budget == 192,
                    "holy separator keeps actual native 64+128 threads and its existing shared budget", checks);
            var definition = machine.getDefinition();
            var before = definition.getBeforeWorking(); var working = definition.getOnWorking(); var after = definition.getAfterWorking();
            int[] callbacks = new int[3];
            try {
                // Each observer invokes the factory's real callback; none substitutes
                // recipe checks, reduction multipliers, input payment or output handling.
                definition.setBeforeWorking((host, recipe) -> { callbacks[0]++; return before.test(host, recipe); });
                definition.setOnWorking(host -> { callbacks[1]++; return working.test(host); });
                definition.setAfterWorking(host -> { callbacks[2]++; after.accept(host); });
                fixture.input.getInventory().setStackInSlot(0, NATIVE_INPUT.toStack(1));
                var order = fixture.cloudOrder(0); fixture.tick();
                check(access.iv$nativeInputsActive() && fixture.nativeStock() == 0 && !order.active(),
                        "nativeTarget admits a real complete physical batch before waiting cloud order", checks);
                var multiple = (org.gtlcore.gtlcore.common.machine.trait.MultipleRecipesLogic) logic;
                double originalCost = 32D * 8D * multiple.getReductionEUt() * multiple.getReductionDuration();
                long admittedCost = Math.multiplyExact(RecipeHelper.getInputEUt(logic.getLastRecipe()), logic.getDuration());
                check(admittedCost > 0 && admittedCost <= originalCost && originalCost - admittedCost < logic.getDuration(),
                        "nativeTarget uses real Core Multiple reduction multipliers and aggregate timing with upstream EU rounding", checks);
                fixture.input.getInventory().setStackInSlot(0, NATIVE_INPUT.toStack(3));
                fixture.until(() -> !access.iv$nativeInputsActive(), 240, "nativeTarget physical batch completes");
                check(fixture.outputCount(Items.GOLD_NUGGET) == 1 && fixture.nativeStock() == 3
                        && callbacks[0] >= 1 && callbacks[1] > 0 && callbacks[2] == 1,
                        "nativeTarget executes original before/on/after callbacks and yields before prepaying another physical batch", checks);
                fixture.until(order::active, 40, "nativeTarget cloud order receives next turn");
                check(order.inventory.isEmpty() && order.elapsed > 0 && fixture.nativeStock() == 3,
                        "nativeTarget cloud order advances using only its isolated apple", checks);
                fixture.until(() -> order.completedOperations == 1, 240, "nativeTarget cloud batch completes");
                check(order.pending.equals(Map.of(CLOUD_OUTPUT, 1L)) && fixture.outputCount(Items.GOLD_NUGGET) == 1
                        && fixture.nativeStock() == 3 && callbacks[0] >= 2 && callbacks[2] == 2,
                        "nativeTarget cloud completion executes original callbacks and settles one output without native material leakage", checks);
                check(IvMachineScope.threads(machine) == threads && IvMachineScope.parallel(machine) == parallel
                        && IvMachineScope.budget(machine) == budget && logic.isWorkingEnabled(),
                        "nativeTarget physical-to-cloud rotation preserves established threads, parallel and budget", checks);
            } finally {
                definition.setBeforeWorking(before); definition.setOnWorking(working); definition.setAfterWorking(after);
            }
        }
    }

    private static void ownIvRecipeLogicHost(List<String> checks) throws Exception {
        try (var fixture = new Fixture(false, "gtl_enhancedcore:plasma_machine_tool")) {
            var machine = fixture.base.machine();
            check(machine.getRecipeLogic() instanceof IvRecipeLogic,
                    "registered plasma machine tool retains real self-owned IvRecipeLogic supplier", checks);
            var logic = (IvRecipeLogic) machine.getRecipeLogic(); var access = (IvNativeAccess) (Object) logic;
            var definition = machine.getDefinition(); var original = definition.getOnWorking();
            GTRecipe[] nativeSnapshot = new GTRecipe[1]; int[] observedNativeCallbacks = new int[1];
            try {
                definition.setOnWorking(host -> {
                    if (access.iv$nativeInputsActive() && nativeSnapshot[0] != null) {
                        if (logic.getLastRecipe() != nativeSnapshot[0])
                            throw new AssertionError("self-owned isolated publication replaced paid native callback recipe");
                        observedNativeCallbacks[0]++;
                    }
                    return original.test(host);
                });
                fixture.input.getInventory().setStackInSlot(0, NATIVE_INPUT.toStack(2));
                var order = fixture.cloudOrder(0); var summaryBefore = logic.getOrderSummary();
                fixture.tick();
                check(access.iv$nativeInputsActive() && fixture.nativeStock() == 1 && !order.active()
                        && order.inventory.equals(Map.of(CLOUD_INPUT, 1L)),
                        "self-owned IV starts one native batch and retains isolated apple ownership", checks);
                nativeSnapshot[0] = logic.getLastRecipe();
                int nativeDuration = logic.getDuration(); long nativeEUt = RecipeHelper.getInputEUt(nativeSnapshot[0]);
                long nativeEnergyBefore = fixture.energy.getEnergyStored();
                for (int tick = 0; tick < 3; tick++) fixture.tick();
                check(access.iv$nativeInputsActive() && logic.getLastRecipe() == nativeSnapshot[0]
                        && logic.getProgress() == 3 && logic.getDuration() == nativeDuration
                        && logic.getOrderSummary().equals(summaryBefore) && observedNativeCallbacks[0] == 3
                        && fixture.energy.getEnergyStored() == nativeEnergyBefore - 3 * nativeEUt,
                        "self-owned native callbacks retain exact recipe/progress and pay each full tick without isolated display publication", checks);
                fixture.until(() -> !access.iv$nativeInputsActive(), 240, "self-owned native batch completes");
                check(fixture.outputCount(Items.GOLD_NUGGET) == 1 && fixture.nativeStock() == 1 && !order.active()
                        && fixture.energy.getEnergyStored() == nativeEnergyBefore - Math.multiplyExact(nativeEUt, nativeDuration),
                        "self-owned native complete batch delivers once and settles exact observed EU without prepaying next input", checks);
                nativeSnapshot[0] = null;
                long cloudEnergyBefore = fixture.energy.getEnergyStored();
                fixture.until(order::active, 40, "self-owned cloud order receives next turn");
                check(!access.iv$nativeInputsActive() && order.inventory.isEmpty() && order.elapsed > 0
                        && fixture.nativeStock() == 1 && logic.getOrderSummary().getInt("count") == 1,
                        "self-owned isolated executor receives the next turn and publishes only after native batch finishes", checks);
                fixture.until(() -> order.completedOperations == 1, 240, "self-owned cloud order completes");
                check(order.pending.equals(Map.of(CLOUD_OUTPUT, 1L)) && order.energyLeft.signum() == 0
                        && java.math.BigInteger.valueOf(cloudEnergyBefore - fixture.energy.getEnergyStored()).equals(order.energyTotal)
                        && fixture.nativeStock() == 1 && fixture.outputCount(Items.GOLD_NUGGET) == 1,
                        "self-owned cloud batch conserves isolated material, physical output and complete energy ledger", checks);
                fixture.until(access::iv$nativeInputsActive, 40, "self-owned physical input receives another turn");
                nativeSnapshot[0] = logic.getLastRecipe();
                check(fixture.nativeStock() == 0 && order.completedOperations == 1 && order.pending.equals(Map.of(CLOUD_OUTPUT, 1L)),
                        "self-owned native-cloud-native rotation starts only its remaining carrot without replaying cloud completion", checks);
                fixture.until(() -> !access.iv$nativeInputsActive(), 240, "self-owned second native batch completes");
                check(fixture.outputCount(Items.GOLD_NUGGET) == 2 && fixture.nativeStock() == 0
                        && order.completedOperations == 1 && order.pending.equals(Map.of(CLOUD_OUTPUT, 1L))
                        && logic.isWorkingEnabled() && !logic.getMessage().equals("gtl_enhancedcore.diagnostic.iv_failure"),
                        "self-owned IV completes two native batches around one cloud batch with exact materials and outputs", checks);
            } finally { definition.setOnWorking(original); }
        }
    }

    private static void ownIvSuspendAfterFinish(List<String> checks) throws Exception {
        try (var fixture = new Fixture(false, "gtl_enhancedcore:plasma_machine_tool")) {
            var machine = fixture.base.machine();
            var logic = (IvRecipeLogic) machine.getRecipeLogic(); var access = (IvNativeAccess) (Object) logic;
            fixture.input.getInventory().setStackInSlot(0, NATIVE_INPUT.toStack(2));
            var order = fixture.cloudOrder(0);
            fixture.tick();
            check(access.iv$nativeInputsActive() && fixture.nativeStock() == 1,
                    "self-owned suspend fixture starts one real paid native batch", checks);
            ((org.gtlcore.gtlcore.api.machine.ISuspendableMachine) machine).gtlcore$setSuspendAfterFinish(true);
            fixture.until(() -> !access.iv$nativeInputsActive(), 240, "self-owned suspend-after-finish completes native batch");
            check(!logic.isWorkingEnabled() && !field(logic, "ivWorkingEnabled").getBoolean(logic)
                    && !field(logic, "iv$workingEnabled").getBoolean(logic)
                    && fixture.outputCount(Items.GOLD_NUGGET) == 1 && fixture.nativeStock() == 1 && !order.active(),
                    "real suspend-after-finish synchronizes self-owned and base enabled fields after one native output", checks);
            for (int tick = 0; tick < 4; tick++) fixture.tick();
            check(!logic.isWorkingEnabled() && !order.active() && order.inventory.equals(Map.of(CLOUD_INPUT, 1L))
                    && fixture.nativeStock() == 1 && fixture.outputCount(Items.GOLD_NUGGET) == 1,
                    "self-owned finish pause preserves queued cloud stock and blocks both executors", checks);
            logic.setWorkingEnabled(true);
            fixture.until(order::active, 40, "self-owned explicit resume reopens cloud turn");
            check(logic.isWorkingEnabled() && order.elapsed > 0 && order.inventory.isEmpty()
                    && fixture.nativeStock() == 1 && fixture.outputCount(Items.GOLD_NUGGET) == 1,
                    "self-owned explicit resume advances cloud without duplicating native input or output; stock="
                            + fixture.nativeStock() + ", output=" + fixture.outputCount(Items.GOLD_NUGGET)
                            + ", elapsed=" + order.elapsed + ", enabled=" + logic.isWorkingEnabled(), checks);
        }
    }

    private static final class Fixture implements AutoCloseable {
        final StellarIvGuiChecks.Fixture base;
        final ReadyCloud cloud;
        final ItemBusPartMachine input, output;
        final MEPatternBufferPartMachine pattern;
        final NotifiableEnergyContainer energy;
        final GTRecipe cloudRecipe;
        final GTRecipe nativeRecipe;
        final UUID machineIdentity;
        int ticks;

        Fixture(boolean usePattern) throws Exception { this(usePattern, "gtceu:large_chemical_reactor"); }
        Fixture(boolean usePattern, String machineId) throws Exception {
            base = StellarIvGuiChecks.fixture(machineId, false, false, false);
            var machine = base.machine();
            machineIdentity = ((CloudMachineIdentity) machine).cloud$machineInstance(true);
            var cloudHolder = place("gtl_enhancedcore:cloud_pattern_buffer", 3);
            cloud = new ReadyCloud(cloudHolder);
            field(cloudHolder, "metaMachine").set(cloudHolder, cloud);
            ((MultiblockPartControllerPositionsAccessor) (Object) cloud).enhancedcore$getControllerPositions().add(machine.getPos());
            output = (ItemBusPartMachine) place("gtceu:lv_output_bus", 4).getMetaMachine();
            input = usePattern ? null : (ItemBusPartMachine) place("gtceu:lv_input_bus", 7).getMetaMachine();
            pattern = usePattern ? (MEPatternBufferPartMachine) place("gtceu:me_extend_pattern_buffer", 8).getMetaMachine() : null;
            parts(List.of(cloud, output, usePattern ? pattern : input));
            var originalType = machine.getRecipeType();
            // A private type/index keeps shared registries and global lookups untouched.
            // Its native recipe must retain this exact type object: Core excludes
            // cached pattern slots from stock search and filters cached recipes by ==.
            var lookupType = new GTRecipeType(originalType.registryName, "cloud_native_input_audit").setMaxIOSize(4, 4, 4, 4);
            field(machine, "recipeTypes").set(machine, new GTRecipeType[]{lookupType});
            nativeRecipe = lookupType.recipeBuilder("cloud_native_input_carrot")
                    .inputItems(Items.CARROT).outputItems(Items.GOLD_NUGGET).EUt(32).duration(8).buildRawRecipe();
            lookupType.getLookup().addRecipe(nativeRecipe);
            cloudRecipe = originalType.recipeBuilder("cloud_native_input_apple")
                    .inputItems(Items.APPLE).outputItems(Items.DIAMOND).EUt(32).duration(5).buildRawRecipe();
            machine.getCapabilitiesProxy().clear();
            if (usePattern) {
                var encoded = PatternDetailsHelper.encodeProcessingPattern(new GenericStack[]{new GenericStack(NATIVE_INPUT, 1)},
                        new GenericStack[]{new GenericStack(AEItemKey.of(Items.GOLD_NUGGET), 1)});
                pattern.getPatternInventory().setStackInSlot(0, encoded);
                var change = MEPatternBufferPartMachine.class.getDeclaredMethod("onPatternChange", int.class);
                change.setAccessible(true); change.invoke(pattern, 0);
                Object slot = ((Object[]) pattern.getInternalInventory())[0];
                var add = slot.getClass().getMethod("add", AEKey.class, long.class); add.setAccessible(true);
                add.invoke(slot, NATIVE_INPUT, 4L);
            } else {
                input.getInventory().setStackInSlot(0, NATIVE_INPUT.toStack(4));
                // Non-distinct formed buses expose their real inventory. Their
                // combined proxy is disabled while no constituent is distinct.
                machine.getCapabilitiesProxy().put(IO.IN, ItemRecipeCapability.CAP, List.of(input.getInventory()));
            }
            machine.getCapabilitiesProxy().put(IO.OUT, ItemRecipeCapability.CAP, List.of(output.getInventory()));
            energy = new NotifiableEnergyContainer(machine, 100000, 32, 1, 0, 0);
            energy.setEnergyStored(100000);
            machine.getCapabilitiesProxy().put(IO.IN, EURecipeCapability.CAP, List.of(energy));
            field(machine, "energyContainer").set(machine, new EnergyContainerList(List.of(energy)));
            field(machine, "tier").set(machine, 1);
            ((IRecipeCapabilityMachine) machine).upDate();
            cloud.iv$getState().owner = IvBuffers.ownerKey(machine);
            machine.getRecipeLogic().setWorkingEnabled(true);
            if (!IvBuffers.genericCloudNativeInputs(machine) || !machine.hasProxies())
                throw new AssertionError("fixture must have actual native input handlers and live capability proxies");
            if (!IvMachineScope.crossRecipeEnabled(machine) || !IvBuffers.collect(machine).equals(List.of(cloud)))
                throw new AssertionError("fixture must enroll its actual cloud endpoint: " + describe());
        }
        MetaMachineBlockEntity place(String id, int x) {
            var definition = GTRegistries.MACHINES.get(new ResourceLocation(id));
            if (definition == null) throw new AssertionError("Missing real registered fixture machine " + id);
            var position = new BlockPos(x, 80, 0);
            var holder = (MetaMachineBlockEntity) definition.getBlockEntityType().create(position, definition.defaultBlockState());
            var world = (TrackedDummyWorld) base.machine().getLevel(); holder.setLevel(world);
            world.blockEntities.put(position, holder);
            world.renderedBlocks.put(position, new com.lowdragmc.lowdraglib.utils.BlockInfo(definition.defaultBlockState()));
            base.holders().add(holder); return holder;
        }
        void parts(List<? extends IMultiPart> parts) throws Exception {
            // GTCEu rebuilds getParts() from partPositions whenever their lengths
            // differ, so both halves of synthetic formation must change together.
            field(base.machine(), "parts").set(base.machine(), new ArrayList<>(parts));
            field(base.machine(), "partPositions").set(base.machine(),
                    parts.stream().map(part -> part.self().getPos()).toArray(BlockPos[]::new));
        }
        IvJob cloudOrder(int slot) {
            var job = new IvJob(slot, cloudRecipe, new CompoundTag(), Map.of(CLOUD_INPUT, 1L), Map.of(), 1);
            job.cloudMachine = machineIdentity;
            cloud.iv$getState().jobs.add(job); return job;
        }
        long nativeStock() throws Exception {
            return pattern == null ? input.getInventory().getStackInSlot(0).getCount()
                    : ((it.unimi.dsi.fastutil.objects.Object2LongMap<?>) field(((Object[]) pattern.getInternalInventory())[0],
                            "itemInventory").get(((Object[]) pattern.getInternalInventory())[0])).getLong(NATIVE_INPUT);
        }
        long outputCount(Item item) {
            long count = pattern == null ? 0 : pattern.getBuffer().getLong(AEItemKey.of(item));
            for (int slot = 0; slot < output.getInventory().getSlots(); slot++) {
                var stack = output.getInventory().getStackInSlot(slot); if (stack.is(item)) count += stack.getCount();
            }
            return count;
        }
        void tick() throws Exception {
            field(base.machine().getHolder(), "offset").set(base.machine().getHolder(), ++ticks - 1000L);
            base.machine().getRecipeLogic().serverTick();
            if ((boolean) field(base.machine().getRecipeLogic(), "iv$passivePatternLane").get(base.machine().getRecipeLogic()))
                throw new AssertionError("temporary native lane flag leaked after serverTick");
        }
        String describe() throws Exception {
            var machine = base.machine(); var logic = machine.getRecipeLogic();
            var access = (IvNativeAccess) logic;
            return "tick=" + machine.getOffsetTimer() + ", remote=" + machine.isRemote()
                    + ", logic=" + logic.getClass().getName() + ", cross=" + IvMachineScope.crossRecipeEnabled(machine)
                    + ", physical=" + IvBuffers.genericCloudNativeInputs(machine) + ", managed=" + access.iv$managed()
                    + ", prefer=" + field(logic, "iv$preferNativeInputs").get(logic)
                    + ", probe=" + field(logic, "iv$nextNativeProbe").get(logic)
                    + ", buffers=" + IvBuffers.collect(machine).stream().map(buffer -> buffer.getDefinition().getId()).toList()
                    + ", available=" + machine.isRecipeLogicAvailable() + ", enabled=" + logic.isWorkingEnabled()
                    + ", active=" + access.iv$nativeInputsActive() + ", status=" + logic.getStatus()
                    + ", last=" + logic.getLastRecipe() + ", duration=" + logic.getDuration()
                    + ", stock=" + nativeStock() + ", tier=" + machine.getTier()
                    + ", rawInput=" + nativeRecipe.matchRecipeContents(IO.IN, machine, nativeRecipe.inputs, false).isSuccess()
                    + ", rawTick=" + nativeRecipe.matchTickRecipe(machine).isSuccess()
                    + ", lookup=" + machine.getRecipeType().getLookup().getRecipeIterator(machine, recipe -> true).hasNext()
                    + ", shared=" + ((IRecipeCapabilityMachine) machine).getSharedRecipeHandlePart()
                    + ", proxies=" + machine.getCapabilitiesProxy() + ", summary=" + access.iv$summary();
        }
        void until(java.util.function.BooleanSupplier condition, int maximum, String stage) throws Exception {
            for (int attempt = 0; !condition.getAsBoolean() && attempt < maximum; attempt++) tick();
            if (!condition.getAsBoolean()) throw new AssertionError(stage + ": " + ((IvNativeAccess) base.machine().getRecipeLogic()).iv$summary()
                    + ", last=" + base.machine().getRecipeLogic().getLastRecipe());
        }
        @Override public void close() { for (var holder : base.holders()) holder.setLevel(null); }
    }

    /** Authority/AE readiness is outside this executor audit. No payment/inventory callback is overridden. */
    private static final class ReadyCloud extends CloudPatternBufferMachine {
        ReadyCloud(IMachineBlockEntity holder) { super(holder); }
        @Override public boolean cloudReady(boolean checkController) { return true; }
    }
    private static Field field(Object target, String name) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try { var field = type.getDeclaredField(name); field.setAccessible(true); return field; }
            catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(name);
    }
    private static void check(boolean valid, String label, List<String> checks) {
        if (!valid) throw new AssertionError(label); checks.add(label);
    }
}
