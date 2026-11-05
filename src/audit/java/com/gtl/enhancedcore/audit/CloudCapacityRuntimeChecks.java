package com.gtl.enhancedcore.audit;

import appeng.api.stacks.AEItemKey;
import com.google.gson.GsonBuilder;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gtl.enhancedcore.common.cloud.CloudPatternBufferMachine;
import com.gtl.enhancedcore.common.recipe.iv.*;
import com.gtladd.gtladditions.common.machine.multiblock.controller.ForgeOfTheAntichrist;
import com.gtladd.gtladditions.common.machine.multiblock.controller.module.ForgeOfTheAntichristModuleBase;
import java.lang.reflect.*;
import java.math.BigInteger;
import java.nio.file.*;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.Items;

/** Real registered suppliers, transformed logic, GUI projection and Jade providers.
 * Dummy formation and cloud readiness are explicit seams; no ME delivery claim. */
public final class CloudCapacityRuntimeChecks {
    private static final AEItemKey INPUT = AEItemKey.of(Items.APPLE);
    private CloudCapacityRuntimeChecks() {}

    public static void run(Path output, List<String> checks) throws Exception {
        registeredInventory(output, checks);
        var rows = new ArrayList<Map<String, Object>>();
        for (String id : List.of("gtladditions:space_scaling_instrument", "gtladditions:forge_of_the_antichrist",
                "gtladditions:heliofusion_exoticizer", "gtladditions:helioflare_power_forge",
                "gtladditions:heliofluix_melting_core", "gtladditions:heliothermal_plasma_fabricator",
                "gtladditions:heliophase_leyline_crystallizer", "gtceu:large_chemical_reactor",
                "gtceu:mega_extractor", "gtceu:super_blast_smelter",
                "gtladditions:space_infinity_integrated_ore_processor")) {
            try (var fixture = new CloudCapacityFixture(id, true)) {
                var machine = (WorkableElectricMultiblockMachine)fixture.machine;
                check(CloudNativeExecutionCoverage.unsupported(machine).isEmpty(), id + " resolves a supported actual execution family", checks);
                if (id.startsWith("gtladditions:")) {
                    check(IvMachineScope.parallel(machine, null) == Long.MAX_VALUE && IvMachineScope.budget(machine) == Long.MAX_VALUE,
                            id + " retains native long unlimited admission without a parallel hatch", checks);
                    boolean single = id.endsWith("heliofusion_exoticizer");
                    check(IvMachineScope.threads(machine) == (single ? 1 : Integer.MAX_VALUE)
                                    && IvMachineScope.unlimitedThreads(machine) != single,
                            id + " retains " + (single ? "one locked recipe" : "unbounded native recipe threads") + " independently of parallel capacity", checks);
                }
                var jade = jade(machine);
                if (id.startsWith("gtladditions:")) {
                    check(jade.getLong("parallel") == -114514L, id + " transformed Jade keeps native infinite parallel sentinel", checks);
                    check(id.endsWith("heliofusion_exoticizer") ? !jade.contains("threads") : jade.getLong("threads") == -114514L,
                            id + " transformed Jade preserves its actual independent thread policy", checks);
                    var lines = new ArrayList<Component>();
                    IvMachineDisplay.wrap(machine, machine::addDisplayText).accept(lines);
                    check(lines.stream().filter(line -> key(line).equals("gtceu.multiblock.parallel")).count() == 1
                                    && lines.stream().noneMatch(line -> Component.Serializer.toJson(line).contains("2,147,483,647")),
                            id + " complete native GUI callback has one infinity row and no int-limit fallback", checks);
                    if (id.endsWith("heliofusion_exoticizer"))
                        check(lines.stream().noneMatch(line -> key(line).equals("gtladditions.multiblock.threads")),
                                id + " GUI does not invent 128 or infinite cross-recipe threads", checks);
                }
                var row = new LinkedHashMap<String, Object>();
                row.put("id", id); row.put("machine", machine.getClass().getName());
                row.put("logic", machine.getRecipeLogic().getClass().getName());
                row.put("parallel", IvMachineScope.parallel(machine, null)); row.put("threads", IvMachineScope.threads(machine));
                row.put("parallelUnlimited", IvMachineScope.unlimitedParallel(machine));
                row.put("threadsUnlimited", IvMachineScope.unlimitedThreads(machine)); row.put("jade", jade.toString());
                row.put("supported", true); rows.add(row);
            }
        }
        for (String id : List.of("gtladditions:taixu_turbid_array",
                "gtladditions:planetary_ionisation_convergence_tower", "gtceu:cleanroom")) {
            try (var fixture = new CloudCapacityFixture(id, true, false)) {
                String unsupported = CloudNativeExecutionCoverage.unsupported(fixture.machine);
                check(!unsupported.isEmpty(), id + " reports the exact native execution override awaiting focused evidence without changing its existing admission: " + unsupported, checks);
                rows.add(Map.of("id", id, "machine", fixture.machine.getClass().getName(),
                        "logic", fixture.machine.getRecipeLogic().getClass().getName(), "focusedRuntimeVerified", false, "nativeOverride", unsupported));
            }
        }
        check(!CloudNativeExecutionCoverage.unsupported(UnadaptedLogic.class).isEmpty(),
                "new foreign execution override is discovered for the audit matrix without disabling existing machines", checks);
        var failures = new ArrayList<Throwable>();
        scenario("SpaceScaling long batch", failures, () -> longBatch("gtladditions:space_scaling_instrument", checks));
        var previousWirelessData = com.hepdd.gtmthings.data.WirelessEnergySavaedData.INSTANCE;
        com.hepdd.gtmthings.data.WirelessEnergySavaedData.INSTANCE = new com.hepdd.gtmthings.data.WirelessEnergySavaedData(null);
        try { scenario("Forge long batch", failures, () -> longBatch("gtladditions:forge_of_the_antichrist", checks)); }
        finally { com.hepdd.gtmthings.data.WirelessEnergySavaedData.INSTANCE = previousWirelessData; }
        scenario("SpaceScaling native thread count", failures, () -> manyThreads(checks));
        scenario("Independent long capacities", failures, () -> independentWideJobs(checks));
        scenario("Taixu fixed cycle", failures, () -> taixuFixedCycle(checks));
        scenario("Apocalyptic chance", failures, () -> apocalypticChance(checks));
        scenario("Arcanic existing native boundary", failures, () -> arcanicNativeBoundary(checks));
        scenario("Antientropy coolant", failures, () -> antientropyCoolant(checks));
        scenario("HelioFusion native module", failures, () -> moduleHostBoundary(checks));
        Files.createDirectories(output);
        Files.writeString(output.resolve("compatibility-capacity-matrix.json"), new GsonBuilder().setPrettyPrinting().create().toJson(rows));
        Files.writeString(output.resolve("compatibility-capacity-scenarios.json"), new GsonBuilder().setPrettyPrinting().create().toJson(
                Map.of("status", failures.isEmpty() ? "PASS" : "FAIL", "failures", failures.stream().map(Throwable::toString).toList(),
                        "checks", checks.stream().filter(check -> !check.startsWith("topology:")).toList())));
        if (!failures.isEmpty()) {
            var failure = new AssertionError("Native capacity scenarios failed: " + failures.stream().map(Throwable::toString).toList());
            failures.forEach(failure::addSuppressed); throw failure;
        }
    }

    @FunctionalInterface private interface Scenario { void run() throws Exception; }
    private static void scenario(String label, List<Throwable> failures, Scenario scenario) {
        try { scenario.run(); }
        catch (Throwable failure) { failures.add(new AssertionError(label + ": " + failure, failure)); }
    }

    private static void registeredInventory(Path output, List<String> checks) throws Exception {
        var rows = new ArrayList<Map<String, Object>>();
        int workable = 0, constructed = 0;
        try (var f = new CloudCapacityFixture("gtceu:large_chemical_reactor", true)) {
            var cloudControllers = (com.gtl.enhancedcore.mixin.gtceu.MultiblockPartControllerPositionsAccessor)(Object)f.cloud;
            var cloudPos = new net.minecraft.core.BlockPos(8, 80, 0);
            cloudControllers.enhancedcore$getControllerPositions().add(cloudPos);
            for (var definition : com.gregtechceu.gtceu.api.registry.GTRegistries.MACHINES.values().stream()
                    .filter(com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition.class::isInstance)
                    .sorted(Comparator.comparing(def -> def.getId().toString())).toList()) {
                var row = new LinkedHashMap<String, Object>(); row.put("id", definition.getId().toString());
                row.put("evidence", "Actual registered supplier in dummy level; membership/getters only, no geometry or production ticks");
                com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity holder = null;
                try {
                    holder = f.place(definition.getId().toString(), 8); constructed++;
                    var meta = holder.getMetaMachine(); row.put("machine", meta.getClass().getName());
                    if (!(meta instanceof com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine machine)) {
                        row.put("recipeMachine", false); rows.add(row); continue;
                    }
                    workable++; row.put("recipeMachine", true); var logic = machine.getRecipeLogic();
                    row.put("logic", logic.getClass().getName());
                    row.put("nativeParallelGetter", probe(() -> machine instanceof org.gtlcore.gtlcore.api.machine.multiblock.ParallelMachine parallel
                            ? parallel.getMaxParallel() : org.gtlcore.gtlcore.common.data.GTLRecipeModifiers.getHatchParallel(machine)));
                    row.put("nativeThreadsGetter", probe(() -> logic instanceof com.gtladd.gtladditions.api.machine.logic.GTLAddMultipleRecipesLogic add
                            ? add.getMultipleThreads() : logic instanceof com.gtladd.gtladditions.api.machine.logic.MutableRecipesLogic<?> mutable
                            && mutable.isMultipleRecipeMode() ? mutable.getMultipleThreads()
                            : logic instanceof org.gtlcore.gtlcore.common.machine.trait.MultipleRecipesLogic ? 64 : 1));
                    var overrides = new ArrayList<String>();
                    for (Class<?> type = logic.getClass(); type != null && RecipeLogic.class.isAssignableFrom(type); type = type.getSuperclass())
                        for (var method : type.getDeclaredMethods())
                            if (!method.isBridge() && Set.of("serverTick", "handleRecipeWorking", "handleTickRecipe", "onRecipeFinish",
                                    "getGTRecipe", "getRecipe", "calculateParallels", "calculateParallel", "checkRecipe",
                                    "buildFinalNormalRecipe", "buildFinalWirelessRecipe").contains(method.getName()))
                                overrides.add(type.getName() + "." + method.getName());
                    row.put("executionOwners", overrides);
                    var callbacks = new LinkedHashMap<String, Object>();
                    for (String name : List.of("beforeWorking", "onWorking", "afterWorking"))
                        callbacks.put(name, probe(() -> machine.getClass().getMethod(name,
                                name.equals("beforeWorking") ? new Class<?>[]{GTRecipe.class} : new Class<?>[0]).getDeclaringClass().getName()));
                    row.put("machineCallbacks", callbacks);
                    field(machine, "isFormed").setBoolean(machine, true); machine.getMultiblockState().error = null;
                    field(machine, "parts").set(machine, new ArrayList<>(f.machine.getParts()));
                    field(machine, "partPositions").set(machine, f.machine.getParts().stream().map(part -> part.self().getPos())
                            .toArray(net.minecraft.core.BlockPos[]::new));
                    row.put("cloudEligible", probe(() -> IvBuffers.genericCloudTarget(machine) || IvMachineScope.nativeTarget(machine)));
                    row.put("cloudParallel", probe(() -> IvMachineScope.parallel(machine, null)));
                    row.put("cloudThreads", probe(() -> IvMachineScope.threads(machine)));
                    row.put("cloudBudget", probe(() -> IvMachineScope.budget(machine)));
                    row.put("parallelUnlimited", probe(() -> IvMachineScope.unlimitedParallel(machine)));
                    row.put("threadsUnlimited", probe(() -> IvMachineScope.unlimitedThreads(machine)));
                    row.put("nativeOverrideNeedingFocusedEvidence", CloudNativeExecutionCoverage.unsupported(machine));
                } catch (Throwable failure) { row.put("constructionError", failure.toString()); }
                finally { if (holder != null) holder.setLevel(null); }
                rows.add(row);
            }
        }
        Files.createDirectories(output);
        Files.writeString(output.resolve("registered-machine-compatibility.json"), new GsonBuilder().setPrettyPrinting().create().toJson(rows));
        check(constructed > 100 && workable > 100,
                "Full registration supplier inventory constructed " + constructed + " multiblocks and inspected " + workable
                        + " Workable recipe controllers; all errors and evidence boundaries are retained in JSON", checks);
    }
    @FunctionalInterface private interface Probe { Object get() throws Exception; }
    private static Object probe(Probe probe) {
        try { return probe.get(); }
        catch (Throwable failure) { return "UNVERIFIED: " + failure; }
    }

    private static void longBatch(String id, List<String> checks) throws Exception {
        try (var f = new CloudCapacityFixture(id, true)) {
            if (f.machine instanceof com.gtladd.gtladditions.api.machine.wireless.GTLAddWirelessWorkableElectricMultipleRecipesMachine) {
                UUID owner = UUID.randomUUID(); field(f.machine, "uuid").set(f.machine, owner);
                com.hepdd.gtmthings.api.misc.WirelessEnergyManager.setUserEU(owner, BigInteger.TEN.pow(30));
            }
            f.highPower();
            long operations = 1L << 33;
            var job = f.order(0, operations, 32, 1);
            f.tick();
            check(job.active() && !job.halted && job.parallel == operations && job.nativeCapacity == operations
                            && job.remaining == 0 && job.inventory.isEmpty(),
                    id + " real serverTick admits 8,589,934,592 isolated operations in one native batch; "
                            + ((IvNativeAccess)f.machine.getRecipeLogic()).iv$summary() + "; " + job.save(), checks);
            long committed = job.parallel;
            var committedOutputs = new LinkedHashMap<>(job.completion);
            var restored = IvJob.load(job.save());
            check(restored.parallel == committed && restored.nativeCapacity == committed && restored.inventory.isEmpty()
                            && restored.nativeRecipe != null && restored.energyLeft.equals(job.energyLeft),
                    id + " real order NBT round trip retains wide paid input, modified recipe, capacity and energy", checks);
            f.cloud.iv$getState().jobs.clear(); f.cloud.iv$getState().jobs.add(restored);
            f.until(() -> restored.completedOperations == operations, restored.duration + 4, "wide native order finishes after NBT restart");
            check(!restored.halted && restored.inventory.isEmpty() && restored.pendingOperations == operations
                            && restored.pending.equals(committedOutputs),
                    id + " resumed real server ticks settle the exact wide output once into pending", checks);
            long after = f.energy.getEnergyStored();
            f.tick();
            check(restored.completedOperations == operations && restored.inventory.isEmpty() && f.energy.getEnergyStored() == after,
                    id + " completed pending batch does not take more material or energy", checks);
        }
    }

    private static void manyThreads(List<String> checks) throws Exception {
        try (var f = new CloudCapacityFixture("gtladditions:space_scaling_instrument", true)) {
            var jobs = new ArrayList<IvJob>();
            for (int i = 0; i < 129; i++) jobs.add(f.order(i, 1, 0, 80));
            f.tick();
            check(jobs.stream().allMatch(job -> job.active() && !job.halted && job.parallel == 1 && job.inventory.isEmpty()),
                    "space_scaling_instrument starts 129 real isolated recipe jobs together, exceeding the inherited 128 getter", checks);
            check(((IvNativeAccess)f.machine.getRecipeLogic()).iv$summary().getInt("active") == 129,
                    "published cloud order count agrees with all 129 active jobs", checks);
        }
    }

    private static void independentWideJobs(List<String> checks) throws Exception {
        try (var f = new CloudCapacityFixture("gtladditions:space_scaling_instrument", true)) {
            f.highPower();
            long amount = 1L << 62;
            var jobs = new ArrayList<IvJob>();
            for (int i = 0; i < 3; i++) {
                var job = f.order(i, amount, 0, 1); job.recipe.tickInputs.clear(); jobs.add(job);
            }
            f.tick();
            check(jobs.stream().allMatch(job -> job.active() && !job.halted && job.parallel == amount && job.elapsed == 1),
                    "native per-recipe admission starts and advances three independent 2^62 batches whose combined parallel exceeds Long.MAX_VALUE", checks);
        }
    }

    private static void taixuFixedCycle(List<String> checks) throws Exception {
        try (var f = new CloudCapacityFixture("gtladditions:taixu_turbid_array", true)) {
            f.highPower();
            field(f.machine, "maxParallel").setLong(f.machine, 8);
            var mutable = (com.gtladd.gtladditions.api.machine.logic.MutableRecipesLogic<?>)f.machine.getRecipeLogic();
            mutable.setUseMultipleRecipes(true);
            field(f.machine, "successRateA").setDouble(f.machine, 100);
            field(f.machine, "successRateB").setDouble(f.machine, 100);
            field(f.machine, "uuAmplifierAmount").setLong(f.machine, 2);
            field(f.machine, "uuMatterAmount").setLong(f.machine, 3);
            var supply = new FixedWireless();
            ((com.gtladd.gtladditions.api.machine.IWirelessElectricMultiblockMachine)f.machine).setWirelessNetworkEnergyHandler(supply);
            var first = f.order(0, 2, 32, 1); var second = f.order(1, 3, 32, 1);
            // The native final builder is the energy oracle. shouldProcess=false
            // collects supplied outputs without consuming physical inputs or rolling
            // their chances, so only the production cloud path settles the orders.
            var nativeData = new com.gtladd.gtladditions.common.data.ParallelData(
                    List.of(first.recipe), new long[]{1}, false, List.of(first.recipe));
            var nativeBuilder = fieldMethod(mutable.getClass(), "buildFinalWirelessRecipe",
                    com.gtladd.gtladditions.common.data.ParallelData.class,
                    com.gtladd.gtladditions.api.machine.trait.IWirelessNetworkEnergyHandler.class);
            var nativeBatch = (com.gtladd.gtladditions.api.recipe.WirelessGTRecipe)nativeBuilder.invoke(mutable, nativeData, supply);
            BigInteger total = nativeBatch.getWirelessEuTickInputs().negate().multiply(BigInteger.valueOf(nativeBatch.duration));
            ((IvNativeAccess)f.machine.getRecipeLogic()).iv$engine().tick();
            check(first.active() && second.active() && first.cycle.equals(second.cycle)
                            && first.energyTotal.equals(total) && second.energyTotal.equals(total)
                            && first.energyLeft.equals(second.energyLeft) && supply.paid.signum() > 0,
                    "Taixu two successful cloud jobs share native fixed energy and one actual wireless payment per cycle tick", checks);
            check(first.completion.keySet().stream().anyMatch(appeng.api.stacks.AEFluidKey.class::isInstance)
                            && second.completion.keySet().stream().anyMatch(appeng.api.stacks.AEFluidKey.class::isInstance),
                    "Taixu selected batches retain native generated UU fluid outputs from guaranteed upgrade rates", checks);
            int elapsed = second.elapsed; BigInteger beforePause = supply.paid;
            mutable.setUseMultipleRecipes(false); field(f.machine, "maxParallel").setLong(f.machine, 1);
            ((IvNativeAccess)f.machine.getRecipeLogic()).iv$engine().tick();
            check(second.elapsed == elapsed && supply.paid.equals(beforePause),
                    "Taixu native mode/capacity reduction pauses the entire committed fixed cycle before energy or output", checks);
            mutable.setUseMultipleRecipes(true); field(f.machine, "maxParallel").setLong(f.machine, 8);
            BigInteger beforeCancel = supply.paid;
            String survivorCycle = second.cycle;
            first.cancel();
            var restored = IvJob.load(second.save()); f.cloud.iv$getState().jobs.set(1, restored);
            check(restored.cycle.equals(survivorCycle) && restored.energyLeft.equals(total.subtract(beforeCancel)),
                    "Taixu survivor NBT carries the complete remaining fixed bill after cancelling another member", checks);
            f.until(() -> restored.completedOperations == 3, 24, "Taixu survivor completes fixed cycle");
            check(supply.paid.equals(total) && first.completedOperations == 0 && restored.pendingOperations == 3
                            && !restored.pending.isEmpty(),
                    "Taixu cancel plus NBT resume pays native fixed total exactly once before delivering surviving output", checks);
            BigInteger paid = supply.paid; f.tick();
            check(supply.paid.equals(paid), "Taixu completed pending cycle never repeats its fixed payment", checks);
        }
    }

    private static final class FixedWireless implements com.gtladd.gtladditions.api.machine.trait.IWirelessNetworkEnergyHandler {
        BigInteger available = BigInteger.TEN.pow(40), paid = BigInteger.ZERO;
        @Override public boolean consumeEnergy(int value) { return consumeEnergy(BigInteger.valueOf(value)); }
        @Override public boolean consumeEnergy(long value) { return consumeEnergy(BigInteger.valueOf(value)); }
        @Override public boolean consumeEnergy(BigInteger value) {
            if (value.signum() > 0 || available.add(value).signum() < 0) throw new AssertionError("Invalid test wireless payment");
            available = available.add(value); paid = paid.subtract(value); return true;
        }
        @Override public BigInteger getMaxAvailableEnergy() { return available; }
        @Override public boolean isOnline() { return true; }
    }

    private static void apocalypticChance(List<String> checks) throws Exception {
        var previousData = com.hepdd.gtmthings.data.WirelessEnergySavaedData.INSTANCE;
        com.hepdd.gtmthings.data.WirelessEnergySavaedData.INSTANCE = new com.hepdd.gtmthings.data.WirelessEnergySavaedData(null);
        try (var f = new CloudCapacityFixture("gtladditions:apocalyptic_torsion_quantum_matrix", true)) {
            f.highPower();
            UUID owner = UUID.randomUUID(); field(f.machine, "uuid").set(f.machine, owner);
            com.hepdd.gtmthings.api.misc.WirelessEnergyManager.setUserEU(owner, BigInteger.TEN.pow(40));
            var job = f.order(0, 100, 32, 1);
            var input = job.recipe.inputs.get(com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability.CAP).getFirst();
            input.chance = 1000;
            var output = job.recipe.outputs.get(com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability.CAP).getFirst();
            output.chance = 1000;
            var prepared = IvNativeBatchRecipe.prepare(f.machine, job.recipe);
            check(prepared.inputs.get(com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability.CAP).getFirst().maxChance == input.maxChance * 10
                            && prepared.outputs.get(com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability.CAP).getFirst().chance == output.maxChance
                            && input.chance == 1000 && output.chance == 1000,
                    "Apocalyptic pure native helper preserves tenfold input chance reduction and guaranteed outputs without mutating the source recipe", checks);
            f.tick();
            check(job.active() && !job.halted && job.parallel == 100 && job.inventory.getOrDefault(INPUT, 0L) >= 98
                            && job.completion.getOrDefault(AEItemKey.of(Items.DIAMOND), 0L) == 100,
                    "Apocalyptic actual native chance planner and copied cache admit all hundred operations, settle reduced input once and guarantee all hundred outputs; " + job.save(), checks);
            var restored = IvJob.load(job.save());
            f.cloud.iv$getState().jobs.clear(); f.cloud.iv$getState().jobs.add(restored);
            f.until(() -> restored.completedOperations == 100, 24, "Apocalyptic chance batch completes after NBT restore");
            check(!restored.halted && restored.pending.getOrDefault(AEItemKey.of(Items.DIAMOND), 0L) == 100,
                    "Apocalyptic paid chance outputs survive NBT and finish without re-rolling", checks);
        } finally { com.hepdd.gtmthings.data.WirelessEnergySavaedData.INSTANCE = previousData; }
    }

    private static void arcanicNativeBoundary(List<String> checks) throws Exception {
        // The existing cloud gate accepts electric controllers and the primitive
        // furnace. Harmony/Arcanic is a NoEnergy machine outside that scope.
        try (var f = new CloudCapacityFixture("gtladditions:arcanic_astrograph", false, false)) {
            var logic = f.machine.getRecipeLogic(); var access = (IvNativeAccess)logic;
            check(!IvBuffers.genericCloudTarget(f.machine) && !IvBuffers.targetController(f.machine)
                            && !IvMachineScope.crossRecipeEnabled(f.machine) && IvBuffers.cloudControllers(f.cloud).isEmpty(),
                    "Arcanic actual NoEnergy supplier retains its existing non-cloud admission boundary with an attached cloud part", checks);
            var recipe = f.machine.getRecipeType().recipeBuilder("audit_arcanic_native_boundary")
                    .outputItems(Items.DIAMOND).duration(4).buildRawRecipe();
            recipe.tickInputs.clear();
            logic.setupRecipe(recipe);
            check(logic.isWorking() && logic.getLastRecipe() == recipe && logic.getDuration() == 4,
                    "Arcanic actual native setupRecipe remains available without creating a cloud order", checks);
            for (int tick = 0; tick < 4; tick++) f.tick();
            long diamonds = 0;
            for (var handler : f.machine.getCapabilitiesProxy().get(
                    com.gregtechceu.gtceu.api.capability.recipe.IO.OUT,
                    com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability.CAP))
                if (handler instanceof com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler items)
                    for (int slot = 0; slot < items.getSlots(); slot++)
                        if (items.getStackInSlot(slot).is(Items.DIAMOND)) diamonds += items.getStackInSlot(slot).getCount();
            check(diamonds == 1 && f.cloud.iv$getState().jobs.isEmpty() && !access.iv$managed()
                            && field(logic, "iv$engine").get(logic) == null,
                    "Arcanic four actual native server ticks retain physical output while no cloud engine or order is created; native lookup/startup and cloud processing are not claimed", checks);
        }
    }

    private static Method fieldMethod(Class<?> start, String name, Class<?>... parameters) throws NoSuchMethodException {
        for (Class<?> type = start; type != null; type = type.getSuperclass()) {
            try { var method = type.getDeclaredMethod(name, parameters); method.setAccessible(true); return method; }
            catch (NoSuchMethodException ignored) {}
        }
        throw new NoSuchMethodException(name);
    }

    private static void antientropyCoolant(List<String> checks) throws Exception {
        try (var f = new CloudCapacityFixture("gtladditions:antientropy_condensation_center", true)) {
            f.highPower();
            var input = (com.gregtechceu.gtceu.common.machine.multiblock.part.ItemBusPartMachine)
                    f.place("gtceu:hv_input_bus", 7).getMetaMachine();
            var parts = new ArrayList<>(f.machine.getParts()); parts.add(input); f.parts(parts);
            f.machine.getCapabilitiesProxy().put(com.gregtechceu.gtceu.api.capability.recipe.IO.IN,
                    com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability.CAP, List.of(input.getInventory()));
            ((org.gtlcore.gtlcore.api.machine.trait.IRecipeCapabilityMachine)f.machine).upDate();
            var dust = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation("kubejs:dust_cryotheum"));
            check(dust != null && dust != Items.AIR, "Antientropy test resolves the actual KubeJS cryotheum item", checks);
            long before = 0;
            for (int slot = 0; slot < input.getInventory().getSlots(); slot++) {
                input.getInventory().setStackInSlot(slot, new net.minecraft.world.item.ItemStack(dust, 64)); before += 64;
            }
            var jobs = new ArrayList<IvJob>();
            for (int i = 0; i < 8; i++) jobs.add(f.order(i, 1, 32, 1));
            ((IvNativeAccess)f.machine.getRecipeLogic()).iv$engine().tick();
            long actualParallel = jobs.stream().mapToLong(job -> job.parallel).sum();
            int expected = ((com.gtl.enhancedcore.mixin.gtladditions.IvNativeAntientropyAccessor)(Object)f.machine)
                    .iv$cryotheumCost(BigInteger.valueOf(actualParallel));
            long after = 0;
            for (int slot = 0; slot < input.getInventory().getSlots(); slot++) after += input.getInventory().getStackInSlot(slot).getCount();
            check(actualParallel == 8 && before - after == expected && expected > 0,
                    "Antientropy actual eight-order admission consumes exactly native f(8)=" + expected + " auxiliary cryotheum, once for the combined batch", checks);
            var saved = jobs.getFirst().save(); var restored = IvJob.load(saved);
            f.cloud.iv$getState().jobs.set(0, restored);
            ((IvNativeAccess)f.machine.getRecipeLogic()).iv$engine().tick();
            long afterResume = 0;
            for (int slot = 0; slot < input.getInventory().getSlots(); slot++) afterResume += input.getInventory().getStackInSlot(slot).getCount();
            check(afterResume == after && !restored.halted && restored.elapsed > jobs.getFirst().elapsed,
                    "Antientropy paid NBT batch resumes without charging cryotheum again", checks);
        }
    }

    private static void moduleHostBoundary(List<String> checks) throws Exception {
        var previousData = com.hepdd.gtmthings.data.WirelessEnergySavaedData.INSTANCE;
        com.hepdd.gtmthings.data.WirelessEnergySavaedData.INSTANCE = new com.hepdd.gtmthings.data.WirelessEnergySavaedData(null);
        try (var host = new CloudCapacityFixture("gtladditions:forge_of_the_antichrist", true);
             var f = new CloudCapacityFixture("gtladditions:heliofusion_exoticizer", true)) {
            var module = (ForgeOfTheAntichristModuleBase)f.machine;
            var job = f.order(0, 2, 0, 1);
            f.tick();
            check(!job.active() && job.inventory.equals(Map.of(INPUT, 2L)) && !job.halted,
                    "HelioFusion missing host rejects before payment and retains exact isolated stock", checks);
            module.setHost((ForgeOfTheAntichrist)host.machine);
            String original = job.recipe.toString();
            GTRecipe prepared = IvNativeBatchRecipe.prepare(module, job.recipe);
            check(prepared != job.recipe && job.recipe.toString().equals(original),
                    "HelioFusion reuses the native host-output transformation on a copy without mutating the registered recipe", checks);
            var expected = ForgeOfTheAntichristModuleBase.Companion.copyAndModifyRecipe(job.recipe,
                    com.gregtechceu.gtceu.api.recipe.content.ContentModifier.multiplier(((ForgeOfTheAntichrist)host.machine).getRecipeOutputMultiply()));
            check(IvRecipeInputs.outputs(prepared.outputs).equals(IvRecipeInputs.outputs(expected.outputs)),
                    "HelioFusion cloud preparation produces the same output amounts as ADD's native module helper", checks);
            field(host.machine, "stasisAnchoredActive").setBoolean(host.machine, true);
            UUID owner = UUID.randomUUID(); field(module, "uuid").set(module, owner);
            com.hepdd.gtmthings.api.misc.WirelessEnergyManager.setUserEU(owner, BigInteger.TEN.pow(30));
            f.highPower(); f.tick();
            check(job.active() && job.parallel == 2 && job.inventory.isEmpty()
                            && ((org.gtlcore.gtlcore.api.machine.trait.ILockRecipe)module.getRecipeLogic()).isLock(),
                    "HelioFusion actual tick consumes two isolated inputs, retains native host gate and locks one recipe", checks);
            var restored = IvJob.load(job.save());
            f.cloud.iv$getState().jobs.clear(); f.cloud.iv$getState().jobs.add(restored);
            f.until(() -> restored.completedOperations == 2, restored.duration + 4, "HelioFusion NBT batch completes");
            var expectedOutputs = new LinkedHashMap<>(IvRecipeInputs.outputs(expected.outputs));
            expectedOutputs.replaceAll((key, amount) -> Math.multiplyExact(amount, 2));
            check(restored.pending.equals(expectedOutputs) && restored.inventory.isEmpty() && !restored.halted,
                    "HelioFusion resumed actual ticks settle the exact native host-multiplied output once", checks);
        } finally { com.hepdd.gtmthings.data.WirelessEnergySavaedData.INSTANCE = previousData; }
    }

    private static CompoundTag jade(WorkableElectricMultiblockMachine machine) {
        var data = new CompoundTag();
        var accessor = (snownee.jade.api.BlockAccessor)Proxy.newProxyInstance(CloudCapacityRuntimeChecks.class.getClassLoader(),
                new Class<?>[]{snownee.jade.api.BlockAccessor.class}, (proxy, method, arguments) -> switch (method.getName()) {
                    case "getBlockEntity" -> machine.getHolder(); case "getServerData" -> data;
                    case "getLevel" -> machine.getLevel(); case "getPosition" -> machine.getPos();
                    case "toString" -> "CloudCapacityAuditAccessor"; default -> null;
                });
        new com.gregtechceu.gtceu.integration.jade.provider.ParallelProvider().appendServerData(data, accessor);
        return data;
    }
    private static String key(Component line) {
        return line.getContents() instanceof TranslatableContents translated ? translated.getKey() : "";
    }
    private static Field field(Object object, String name) throws Exception {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try { var field = type.getDeclaredField(name); field.setAccessible(true); return field; }
            catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(name);
    }
    private static void check(boolean valid, String label, List<String> checks) {
        if (!valid) throw new AssertionError(label); checks.add(label);
    }
    private static final class UnadaptedLogic extends RecipeLogic {
        private UnadaptedLogic(com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine machine) { super(machine); }
        @Override public void onRecipeFinish() { super.onRecipeFinish(); }
    }
}
