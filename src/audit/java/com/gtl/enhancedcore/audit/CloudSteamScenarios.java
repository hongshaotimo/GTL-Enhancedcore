package com.gtl.enhancedcore.audit;

import appeng.api.stacks.AEItemKey;
import com.google.gson.GsonBuilder;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableEnergyContainer;
import com.gregtechceu.gtceu.api.misc.EnergyContainerList;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ItemBusPartMachine;
import com.gtl.enhancedcore.common.cloud.CloudPatternBufferMachine;
import com.gtl.enhancedcore.common.cloud.store.CloudMachineIdentity;
import com.gtl.enhancedcore.common.machine.IndustrialSteamPlatformMachine;
import com.gtl.enhancedcore.common.recipe.iv.*;
import com.gtl.enhancedcore.mixin.gtceu.MultiblockPartControllerPositionsAccessor;
import com.lowdragmc.lowdraglib.utils.BlockInfo;
import com.lowdragmc.lowdraglib.utils.TrackedDummyWorld;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.lang.ref.WeakReference;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

/** Reusable steam/primitive cloud scenarios; no mod bootstrap, events, world creation or application stop. */
public final class CloudSteamScenarios {
    private static final String STEAM = "gtl_enhancedcore:industrial_steam_platform";
    private static final AEItemKey INPUT = AEItemKey.of(Items.APPLE);
    private static final AEItemKey OUTPUT = AEItemKey.of(Items.DIAMOND);
    private final List<String> checks;
    private CloudSteamScenarios(List<String> checks) { this.checks = checks; }

    public static void run(Path directory, List<String> checks) throws Exception {
        int first = checks.size();
        var report = new LinkedHashMap<String, Object>();
        try {
            new CloudSteamScenarios(checks).run();
            CloudSteamUiChecks.run(directory, checks);
            report.put("status", "PASS");
        } catch (Exception | Error failure) {
            report.put("status", "FAIL"); report.put("error", failure.toString()); throw failure;
        } finally {
            report.put("checks", new ArrayList<>(checks.subList(first, checks.size())));
            report.put("scope", "Real registered steam/primitive/LCR/explicit-IV controllers, steam modifier and production serverTick, isolated inventories, steam startup/payment and IvJob codec recovery; actual primitive structure predicates/preview, native GUI synchronized tree and framebuffer at three sizes. Synthetic formed membership/readiness in a dummy Level; no full structure formation, live ME output, rendered Jade or independent JVM persistence.");
            Files.writeString(directory.resolve("cloud-steam-scenarios.json"),
                    new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(report), StandardCharsets.UTF_8);
        }
    }

    private void run() throws Exception {
        var failures = new ArrayList<Throwable>();
        scenario("readiness diagnostic recovery", this::readiness, failures);
        scenario("steam admission and paid restart", this::steamLifecycle, failures);
        scenario("steam rejected starts", this::rejectedStarts, failures);
        scenario("last steam bucket", this::lastBucket, failures);
        scenario("steam selected recipe type", this::selectedRecipeType, failures);
        scenario("generic host boundary", this::genericHost, failures);
        scenario("paid native batch after input replacement", this::paidNativeWithoutPhysicalInput, failures);
        scenario("primitive blast furnace", this::primitiveHost, failures);
        scenario("primitive cloud structure quota", this::primitivePattern, failures);
        scenario("native and owned IV boundary", this::ivBoundary, failures);
        if (!failures.isEmpty()) {
            var failure = new AssertionError("Cloud steam audit failed " + failures.size() + " scenarios: "
                    + failures.stream().map(Throwable::getMessage).toList());
            failures.forEach(failure::addSuppressed); throw failure;
        }
    }

    @FunctionalInterface private interface Scenario { void run() throws Exception; }
    private void scenario(String name, Scenario scenario, List<Throwable> failures) {
        try { scenario.run(); }
        catch (Throwable failure) {
            failure.printStackTrace(); failures.add(new AssertionError(name + ": " + failure, failure));
        }
    }

    private void steamLifecycle() throws Exception {
        try (var f = new Fixture(STEAM, false)) {
            var steam = (IndustrialSteamPlatformMachine) f.machine;
            check(steam.getRecipeLogic().getClass().getName().endsWith("SteamRecipeLogic")
                            && IvBuffers.genericCloudTarget(steam) && !IvMachineScope.nativeTarget(steam),
                    "Actual registered steam supplier keeps SteamRecipeLogic and joins only the cloud bridge");
            check(IvMachineScope.parallel(steam) == 64 && IvMachineScope.threads(steam) == 1
                            && IvMachineScope.activeThreads(steam) == 1 && IvMachineScope.budget(steam) == 64,
                    "Steam cloud admission remains exactly 64 parallels and one recipe thread");
            check(steam.getEnergyContainer().getEnergyStored() == 0
                            && steam.getCapabilitiesProxy().get(IO.IN, EURecipeCapability.CAP).stream()
                                    .anyMatch(handler -> handler.getClass().getName().endsWith("SteamCacheEnergyHandler"))
                            && steam.getCapabilitiesProxy().get(IO.IN, EURecipeCapability.CAP).stream()
                                    .noneMatch(handler -> handler instanceof com.gregtechceu.gtceu.api.capability.IEnergyContainer),
                    "Steam fixture retains its native steam capability and has zero physical EU with no energy hatch");
            f.steam(999);
            var first = f.order(0, 128, 32, 8);
            var second = f.order(1, 64, 32, 8);
            for (int tick = 0; tick < 3; tick++) f.tick();
            check(!first.active() && !second.active() && first.inventory.equals(Map.of(INPUT, 128L))
                            && second.inventory.equals(Map.of(INPUT, 64L)) && steam.getSteamStoredMb() == 999
                            && steam.isSteamInsufficient(),
                    "999 mB refuses every cloud start without consuming inputs or steam");
            // Select one pending job deterministically while checking the second cannot run concurrently.
            f.cloud.iv$getState().jobs.remove(second);
            f.steam(5000); f.until(first::active, 25, "first steam admission");
            check(first.parallel == 64 && first.nativeCapacity == 64 && first.remaining == 64
                            && first.inventory.equals(Map.of(INPUT, 64L)) && first.completion.equals(Map.of(OUTPUT, 64L))
                            && steam.getSteamStoredMb() == 4000,
                    "Successful 64-operation cloud batch consumes exactly 64 apples and one 1000 mB startup charge");
            check(first.eut == 0 && first.energyTotal.equals(BigInteger.ZERO) && first.energyLeft.equals(BigInteger.ZERO)
                            && first.duration == 8 && first.nativeRecipe != null
                            && RecipeHelper.getInputEUt(first.nativeRecipe) > 0 && RecipeHelper.getInputEUt(first.recipe) == 32,
                    "Steam ledger owes zero EU while native modified voltage and original eight-tick duration remain intact");
            int elapsed = first.elapsed;
            f.cloud.iv$getState().jobs.add(second); f.tick();
            check(first.elapsed == elapsed + 1 && !second.active() && steam.getSteamStoredMb() == 4000
                            && second.inventory.equals(Map.of(INPUT, 64L)),
                    "No-energy-hatch tick advances committed steam work once and queued recipe stays untouched");
            // Persist the active job and construct a fresh executor, exercising the real codec and payment state.
            var restored = IvJob.load(first.save());
            check(restored.active() && restored.elapsed == first.elapsed && restored.inventory.equals(first.inventory)
                            && restored.energyLeft.signum() == 0 && restored.nativeRecipe != null,
                    "Persisted active steam job restores exact committed materials, progress and zero-EU ledger");
            f.cloud.iv$getState().jobs.remove(first); f.cloud.iv$getState().jobs.remove(second);
            f.cloud.iv$getState().jobs.add(restored);
            var recoveredEngine = new IvNativeEngine(steam, steam.getRecipeLogic(), (IvNativeAccess) steam.getRecipeLogic());
            int restoredElapsed = restored.elapsed; recoveredEngine.tick(false);
            check(restored.elapsed == restoredElapsed + 1 && steam.getSteamStoredMb() == 4000
                            && restored.inventory.equals(Map.of(INPUT, 64L)),
                    "Fresh executor resumes paid steam batch without replaying input or startup steam charge");
            f.until(() -> restored.completedOperations == 64, 25, "restored steam completion");
            check(restored.pending.equals(Map.of(OUTPUT, 64L)) && restored.inventory.equals(Map.of(INPUT, 64L))
                            && restored.remaining == 64 && steam.getSteamStoredMb() == 4000,
                    "Restored steam completion produces 64 diamonds once and leaves next-batch inputs unconsumed");
            for (int tick = 0; tick < 4; tick++) f.tick();
            check(restored.completedOperations == 64 && restored.pending.equals(Map.of(OUTPUT, 64L))
                            && steam.getSteamStoredMb() == 4000 && !restored.active(),
                    "Undelivered output blocks repeated admission and never repeats steam charge or output");
            // Explicitly acknowledge this synthetic delivery; no ME network is claimed by the fixture.
            restored.pending.clear(); f.until(restored::active, 25, "second batch admission");
            check(restored.parallel == 64 && restored.remaining == 0 && restored.inventory.isEmpty()
                            && steam.getSteamStoredMb() == 3000 && restored.deliveredOperations == 64,
                    "Only a new successful batch spends the next 1000 mB and consumes the remaining 64 inputs");
            f.until(() -> restored.completedOperations == 128, 25, "second steam completion");
            check(restored.pending.equals(Map.of(OUTPUT, 64L)) && steam.getSteamStoredMb() == 3000
                            && steam.getRecipeLogic().isWorkingEnabled() && !restored.halted,
                    "Two full steam batches total exactly 2000 mB and 128 outputs without physical EU");
            displayBoundary(f, false);
        }
    }

    private void readiness() throws Exception {
        try (var f = new Fixture("gtceu:large_chemical_reactor", true)) {
            f.cloud.iv$getState().message = "gtl_enhancedcore.cloud.status.origin";
            check(IvBuffers.bind(f.cloud, f.machine) && f.cloud.iv$getState().message.isEmpty(),
                    "A ready real cloud binding clears a stale origin-readiness message");
            for (String failure : List.of("gtl_enhancedcore.cloud.status.review", "gtl_enhancedcore.diagnostic.iv_receive")) {
                f.cloud.iv$getState().message = failure;
                check(IvBuffers.bind(f.cloud, f.machine) && f.cloud.iv$getState().message.equals(failure),
                        "Successful binding preserves the independent task/import diagnostic " + failure);
            }
            var holder = f.place("gtl_enhancedcore:cloud_pattern_buffer", 7);
            var unattached = (CloudPatternBufferMachine) holder.getMetaMachine();
            check(unattached.cloudReadyFailure(true).equals("gtl_enhancedcore.cloud.status.unbound")
                            && !unattached.cloudReady(true),
                    "Actual unattached cloud endpoint reports unbound rather than an origin-route failure");
        }
    }

    private void rejectedStarts() throws Exception {
        try (var f = new Fixture(STEAM, false)) {
            f.steam(5000);
            var overVoltage = f.order(0, 64, GTValues.V[GTValues.MV], 8);
            check(new IvNativeMatchContext(f.machine, overVoltage.inventory, Map.of(), 64, 64).prepare(overVoltage.recipe) == null,
                    "Actual steam recipe modifier rejects voltage above the effective native hull tier");
            for (int tick = 0; tick < 3; tick++) f.tick();
            check(!overVoltage.active() && overVoltage.inventory.equals(Map.of(INPUT, 64L))
                            && ((IndustrialSteamPlatformMachine) f.machine).getSteamStoredMb() == 5000
                            && !overVoltage.halted && f.machine.getRecipeLogic().isWorkingEnabled(),
                    "Real steam hull-voltage modifier rejects an MV recipe on ULV hull without consuming stock or steam");
            f.cloud.iv$getState().jobs.clear();
            var job = f.order(1, 64, 32, 8);
            var definition = f.machine.getDefinition(); var previous = definition.getBeforeWorking();
            try {
                definition.setBeforeWorking((machine, recipe) -> false);
                f.tick();
                check(!job.active() && job.inventory.equals(Map.of(INPUT, 64L))
                                && ((IndustrialSteamPlatformMachine) f.machine).getSteamStoredMb() == 5000,
                        "Upstream beforeWorking rejection preserves all inputs and startup steam");
            } finally { definition.setBeforeWorking(previous); }
            f.until(job::active, 25, "callback recovery");
            check(job.parallel == 64 && ((IndustrialSteamPlatformMachine) f.machine).getSteamStoredMb() == 4000,
                    "Restored upstream admission starts normally and charges steam exactly once");
        }
    }

    private void selectedRecipeType() throws Exception {
        try (var f = new Fixture(STEAM, false)) {
            check(f.machine.getRecipeTypes().length > 1, "Registered steam controller exposes its native recipe selector");
            f.steam(5000);
            var first = f.order(0, 128, 32, 8);
            f.until(first::active, 25, "selected-type first admission");
            var oldType = first.recipe.recipeType;
            f.machine.setActiveRecipeType(1);
            check(f.machine.getRecipeType() != oldType && !IvBuffers.cloudAllowsRecipeType(f.machine, oldType)
                            && IvBuffers.cloudRecipeTypes(f.machine).equals(List.of(f.machine.getRecipeType())),
                    "Steam cloud claims and new admissions follow only the currently selected native recipe type");
            f.until(() -> first.completedOperations == 64, 25, "committed old-mode completion");
            check(first.pending.equals(Map.of(OUTPUT, 64L)) && first.inventory.equals(Map.of(INPUT, 64L))
                            && ((IndustrialSteamPlatformMachine) f.machine).getSteamStoredMb() == 4000,
                    "Switching native mode lets an already paid old-mode batch complete without another charge");
            first.pending.clear();
            for (int tick = 0; tick < 4; tick++) f.tick();
            check(!first.active() && first.remaining == 64 && first.inventory.equals(Map.of(INPUT, 64L))
                            && ((IndustrialSteamPlatformMachine) f.machine).getSteamStoredMb() == 4000,
                    "After mode switch queued old-mode batches pause with their stock and steam preserved");
            var selected = f.order(1, 64, 32, 8);
            f.until(selected::active, 25, "new selected-type admission");
            check(selected.parallel == 64 && !first.active()
                            && ((IndustrialSteamPlatformMachine) f.machine).getSteamStoredMb() == 3000,
                    "New selected native mode can start while the old-mode order remains paused");
        }
    }

    private void lastBucket() throws Exception {
        try (var f = new Fixture(STEAM, false)) {
            f.steam(1000);
            var job = f.order(0, 64, 32, 8);
            f.until(job::active, 25, "last steam bucket admission");
            check(job.parallel == 64 && job.inventory.isEmpty()
                            && ((IndustrialSteamPlatformMachine) f.machine).getSteamStoredMb() == 0,
                    "Exactly one remaining steam bucket admits all 64 operations and is consumed once");
            f.until(() -> job.completedOperations == 64, 25, "last-bucket completion");
            check(job.pending.equals(Map.of(OUTPUT, 64L)) && job.energyTotal.signum() == 0
                            && ((IndustrialSteamPlatformMachine) f.machine).getSteamStoredMb() == 0,
                    "An already paid batch finishes after the last steam bucket is exhausted");
        }
    }

    private void genericHost() throws Exception {
        try (var f = new Fixture("gtceu:large_chemical_reactor", true)) {
            check(IvBuffers.genericCloudTarget(f.machine) && !IvMachineScope.nativeTarget(f.machine)
                            && IvMachineScope.parallel(f.machine) == 1 && IvMachineScope.threads(f.machine) == 1,
                    "Ordinary large chemical reactor cloud bridge grants neither extra parallel nor IV threads");
            var job = f.order(0, 2, 32, 5);
            f.until(job::active, 25, "generic admission");
            check(job.parallel == 1 && job.eut > 0 && job.energyTotal.signum() > 0 && job.remaining == 1,
                    "Ordinary generic cloud recipe retains real one-operation admission and an electric energy ledger");
            long before = f.energy.getEnergyStored(); long eut = job.eut; int elapsed = job.elapsed;
            f.tick();
            check(job.elapsed == elapsed + 1 && f.energy.getEnergyStored() == before - eut,
                    "Ordinary generic cloud work still pays physical EU once per progressing tick");
            displayBoundary(f, false);
        }
    }

    private void ivBoundary() throws Exception {
        for (String id : List.of("gtceu:holy_separator", "gtl_enhancedcore:plasma_machine_tool")) {
            try (var f = new Fixture(id, true)) {
                int expectedThreads = f.machine instanceof com.gtl.enhancedcore.common.machine.TieredParallelMachine own
                        ? own.getThreadsForTier() : IvMachineScope.baseThreadsFor(f.machine.getDefinition().getId().getPath());
                check(IvBuffers.legacyTargetController(f.machine) && !IvBuffers.genericCloudTarget(f.machine)
                                && IvMachineScope.crossRecipeEnabled(f.machine) && IvMachineScope.threads(f.machine) == expectedThreads,
                        id + " retains explicit IV isolation and its established thread baseline");
                displayBoundary(f, true);
            }
        }
    }

    private void primitiveHost() throws Exception {
        try (var f = new Fixture("gtceu:primitive_blast_furnace", false)) {
            check(f.machine instanceof com.gregtechceu.gtceu.common.machine.multiblock.primitive.PrimitiveWorkableMachine
                            && !(f.machine instanceof WorkableElectricMultiblockMachine)
                            && IvBuffers.genericCloudTarget(f.machine) && IvMachineScope.parallel(f.machine) == 1
                            && IvMachineScope.threads(f.machine) == 1,
                    "Actual non-electric primitive blast furnace joins cloud bridge at one parallel and one thread");
            var primitive = (com.gregtechceu.gtceu.common.machine.multiblock.primitive.PrimitiveWorkableMachine) f.machine;
            check(f.machine.getCapabilitiesProxy().get(IO.IN, ItemRecipeCapability.CAP).contains(primitive.importItems)
                            && f.machine.getCapabilitiesProxy().get(IO.OUT, ItemRecipeCapability.CAP).contains(primitive.exportItems),
                    "Primitive fixture retains its actual native internal item traits");
            var job = f.order(0, 2, 0, 8);
            f.until(job::active, 25, "primitive cloud admission");
            check(job.parallel == 1 && job.nativeCapacity == 1 && job.remaining == 1 && job.eut == 0
                            && job.energyTotal.signum() == 0 && job.inventory.equals(Map.of(INPUT, 1L))
                            && job.nativeRecipe != null && RecipeHelper.getInputEUt(job.nativeRecipe) == 0,
                    "Native primitive recipe map accepts a synthetic zero-EU order without an electric cast or EU hatch");
            int elapsed = job.elapsed; f.tick();
            check(job.elapsed == elapsed + 1 && job.energyLeft.signum() == 0,
                    "Real transformed primitive serverTick advances its committed cloud batch with no electric energy source");
            f.until(() -> job.completedOperations == 1, 25, "primitive cloud completion");
            check(job.pending.equals(Map.of(OUTPUT, 1L)) && job.inventory.equals(Map.of(INPUT, 1L))
                            && primitive.importItems.getStackInSlot(0).isEmpty() && primitive.exportItems.getStackInSlot(0).isEmpty()
                            && !job.halted && f.machine.getRecipeLogic().isWorkingEnabled(),
                    "Primitive cloud completion remains isolated from native internal inputs and outputs");
            jadeBoundary(f, false);
        }
    }

    private void paidNativeWithoutPhysicalInput() throws Exception {
        try (var f = new Fixture("gtceu:large_chemical_reactor", true)) {
            var input = (ItemBusPartMachine) f.place("gtceu:lv_input_bus", 3).getMetaMachine();
            var output = (ItemBusPartMachine) f.holders.get(2).getMetaMachine();
            f.tick();
            var initialAccess = (IvNativeAccess) f.machine.getRecipeLogic();
            check(!initialAccess.iv$managed(),
                    "An idle empty cloud bridge keeps complete native execution ownership");
            // Reproduce the stale display ownership left by a previously drained
            // cloud batch before removing its final cloud assembly.
            initialAccess.iv$managed(true);
            f.parts(List.of(input, output));
            f.tick();
            check(!((IvNativeAccess) f.machine.getRecipeLogic()).iv$managed(),
                    "Removing cloud from an idle controller releases its prior managed ownership before native work");
            input.getInventory().setStackInSlot(0, new net.minecraft.world.item.ItemStack(Items.CARROT, 1));
            f.machine.getCapabilitiesProxy().put(IO.IN, ItemRecipeCapability.CAP, List.of(input.getInventory()));
            if (f.machine instanceof org.gtlcore.gtlcore.api.machine.trait.IRecipeCapabilityMachine capabilities) capabilities.upDate();
            var origin = f.machine.getRecipeType().recipeBuilder("cloud_steam_audit_already_paid_native")
                    .inputItems(Items.CARROT).outputItems(Items.GOLD_NUGGET).EUt(32).duration(8).buildRawRecipe();
            var modified = f.machine.fullModifyRecipe(origin.copy(), new com.gregtechceu.gtceu.api.recipe.logic.OCParams(),
                    new com.gregtechceu.gtceu.api.recipe.logic.OCResult());
            if (modified == null) throw new AssertionError("Paid native fixture modifier rejected its real input");
            var logic = f.machine.getRecipeLogic();
            field(logic, "lastOriginRecipe").set(logic, origin);
            logic.setupRecipe(modified);
            check(logic.getLastRecipe() == modified && logic.isWorking() && input.getInventory().getStackInSlot(0).isEmpty(),
                    "Real native setup pays its carrot exactly once before the last input bus is replaced");
            f.parts(List.of(f.cloud, output));
            f.machine.getCapabilitiesProxy().remove(IO.IN, ItemRecipeCapability.CAP);
            if (f.machine instanceof org.gtlcore.gtlcore.api.machine.trait.IRecipeCapabilityMachine capabilities) capabilities.upDate();
            check(IvMachineScope.crossRecipeEnabled(f.machine) && !IvBuffers.genericCloudNativeInputs(f.machine),
                    "Replacement leaves only cloud input and an already paid native batch");
            var order = f.order(0, 1, 32, 8);
            f.tick();
            var access = (IvNativeAccess) logic;
            check(access.iv$nativeInputsActive() && logic.getLastRecipe() == modified && logic.getProgress() == 1
                            && !order.active() && order.inventory.equals(Map.of(INPUT, 1L)) && logic.isWorkingEnabled(),
                    "Cloud handoff resumes the already paid native batch even when no physical input lane remains");
            f.until(() -> !access.iv$nativeInputsActive(), 30, "last-input-replacement native completion");
            long nuggets = 0;
            for (int slot = 0; slot < output.getInventory().getSlots(); slot++) {
                var stack = output.getInventory().getStackInSlot(slot);
                if (stack.is(Items.GOLD_NUGGET)) nuggets += stack.getCount();
            }
            check(nuggets == 1 && input.getInventory().getStackInSlot(0).isEmpty() && logic.isWorkingEnabled(),
                    "Replaced-input native batch delivers exactly one output and leaves the machine enabled");
            f.until(order::active, 30, "cloud work after last-input-replacement native completion");
            check(order.inventory.isEmpty() && order.elapsed >= 0 && !access.iv$nativeInputsActive()
                            && !access.iv$message().equals("gtl_enhancedcore.diagnostic.iv_legacy"),
                    "Queued cloud order starts after native completion without the legacy permanent-stop rejection");
        }
    }

    private void primitivePattern() throws Exception {
        try (var f = new Fixture("gtceu:primitive_blast_furnace", false)) {
            var definition = (com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition) f.machine.getDefinition();
            var pattern = definition.getPatternFactory().get();
            var grid = ((com.gtl.enhancedcore.mixin.gtceu.BlockPatternAccessor) pattern).gtlEnhancedcore$getBlockMatches();
            var cloudBlock = f.cloud.getDefinition().getBlock();
            var quota = Collections.newSetFromMap(new IdentityHashMap<com.gregtechceu.gtceu.api.pattern.predicates.SimplePredicate, Boolean>());
            var ports = new ArrayList<com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate>();
            int controllers = 0, air = 0, cells = 0;
            for (var plane : grid) for (var row : plane) for (var rule : row) {
                cells++;
                if (rule.isController) { controllers++; continue; }
                boolean brick = rule.common.stream().anyMatch(simple -> simple.candidates != null
                        && Arrays.stream(simple.candidates.get()).anyMatch(candidate -> candidate.getBlockState()
                                .is(com.gregtechceu.gtceu.common.data.GTBlocks.CASING_PRIMITIVE_BRICKS.get())));
                if (brick) {
                    ports.add(rule);
                    rule.limited.stream().filter(simple -> simple.candidates != null
                            && Arrays.stream(simple.candidates.get()).anyMatch(candidate -> candidate.getBlockState().is(cloudBlock)))
                            .forEach(quota::add);
                } else if (rule.isAir()) air++;
            }
            check(cells == 36 && ports.size() == 32 && air == 3 && controllers == 1,
                    "Final primitive furnace pattern retains 36 cells, 32 native brick positions, three air cells and one controller"
                            + " [actual cells=" + cells + ", brick=" + ports.size() + ", air=" + air + ", controllers=" + controllers + "]");
            check(quota.size() == 1 && quota.iterator().next().maxCount == 1 && quota.iterator().next().minCount <= 0,
                    "Every original primitive brick position shares one optional globally limited cloud endpoint predicate");
            var state = f.machine.getMultiblockState();
            var invoker = (org.gtlcore.gtlcore.mixin.gtm.api.machine.IMultiblockStateInvoker) state;
            invoker.cleanState();
            var first = new BlockPos(20, 80, 0); var second = new BlockPos(21, 80, 0);
            f.world.renderedBlocks.put(first, new BlockInfo(cloudBlock.defaultBlockState()));
            f.world.renderedBlocks.put(second, new BlockInfo(cloudBlock.defaultBlockState()));
            check(invoker.updateState(first, ports.getFirst()) && ports.getFirst().test(state),
                    "Actual final primitive brick rule accepts the first real cloud block");
            check(invoker.updateState(second, ports.getLast()) && !ports.getLast().test(state),
                    "Actual final primitive brick rule rejects a second cloud block through its shared quota");
            int previewCloud = 0;
            var shapes = definition.getMatchingShapes();
            for (var shape : shapes) for (var plane : shape.getBlocks()) for (var row : plane) for (var block : row)
                if (block.getBlockState().is(cloudBlock)) previewCloud++;
            check(shapes.size() == 1 && previewCloud == 1,
                    "Final native primitive preview includes exactly one cloud endpoint");
        }
    }

    private void displayBoundary(Fixture f, boolean isolated) {
        var originalMode = Component.translatable("gtceu.gui.machinemode", Component.literal("audit-original-machine-mode"));
        var lines = new ArrayList<Component>();
        var electric = (WorkableElectricMultiblockMachine) f.machine;
        IvMachineDisplay.wrap(electric, list -> {
            electric.addDisplayText(list);
            list.add(originalMode);
        }).accept(lines);
        boolean iv = lines.stream().map(Component.Serializer::toJson).anyMatch(json -> json.contains(IvMachineDisplay.MODE_TRANSLATION));
        check(iv == isolated && (isolated || lines.stream().anyMatch(line -> line == originalMode)),
                f.machine.getDefinition().getId() + " GUI projection " + (isolated ? "retains IV mode" : "preserves original mode without an IV label"));
        jadeBoundary(f, isolated);
    }

    private void jadeBoundary(Fixture f, boolean isolated) {
        var data = new CompoundTag();
        var accessor = (snownee.jade.api.BlockAccessor) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{snownee.jade.api.BlockAccessor.class}, (proxy, method, arguments) -> switch (method.getName()) {
                    case "getBlockEntity" -> f.machine.getHolder();
                    case "getServerData" -> data;
                    case "getLevel" -> f.world;
                    case "getPosition" -> f.machine.getPos();
                    case "toString" -> "CloudSteamAuditAccessor";
                    default -> null;
                });
        new com.gregtechceu.gtceu.integration.jade.provider.MachineModeProvider().appendServerData(data, accessor);
        boolean synthetic = data.getList("RecipeTypes", net.minecraft.nbt.Tag.TAG_STRING).stream()
                .anyMatch(tag -> tag.getAsString().equals(IvMachineDisplay.MODE_ID));
        check(synthetic == isolated,
                f.machine.getDefinition().getId() + " transformed Jade provider "
                        + (isolated ? "keeps explicit IV mode" : "keeps native modes without synthetic IV entry"));
    }

    static final class Fixture implements AutoCloseable {
        final AuditWorld world;
        final List<MetaMachineBlockEntity> holders = new ArrayList<>();
        final WorkableMultiblockMachine machine;
        final ReadyCloud cloud;
        final NotifiableEnergyContainer energy;
        int ticks;

        Fixture(String id, boolean electric) throws Exception {
            var unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); unsafeField.setAccessible(true);
            var unsafe = (sun.misc.Unsafe) unsafeField.get(null);
            world = (AuditWorld) unsafe.allocateInstance(AuditWorld.class);
            field(world, "blockEntities").set(world, new HashMap<>());
            field(world, "renderedBlocks").set(world, new HashMap<>());
            field(world, "proxyWorld").set(world, new WeakReference<Level>(null));
            ObfuscationReflectionHelper.setPrivateValue(Level.class, world, false, "f_46443_");
            ObfuscationReflectionHelper.setPrivateValue(Level.class, world, Level.OVERWORLD, "f_46421_");
            machine = (WorkableMultiblockMachine) place(id, 0).getMetaMachine();
            field(machine, "isFormed").setBoolean(machine, true); machine.getMultiblockState().error = null;
            var cloudHolder = place("gtl_enhancedcore:cloud_pattern_buffer", 1);
            cloud = new ReadyCloud(cloudHolder); field(cloudHolder, "metaMachine").set(cloudHolder, cloud);
            ((MultiblockPartControllerPositionsAccessor) (Object) cloud).enhancedcore$getControllerPositions().add(machine.getPos());
            var output = (ItemBusPartMachine) place("gtceu:lv_output_bus", 2).getMetaMachine();
            field(machine, "parts").set(machine, new ArrayList<IMultiPart>(List.of(cloud, output)));
            field(machine, "partPositions").set(machine, new BlockPos[]{cloud.getPos(), output.getPos()});
            machine.getCapabilitiesProxy().clear();
            if (machine instanceof com.gregtechceu.gtceu.common.machine.multiblock.primitive.PrimitiveWorkableMachine primitive) {
                machine.getCapabilitiesProxy().put(IO.IN, ItemRecipeCapability.CAP, List.of(primitive.importItems));
                machine.getCapabilitiesProxy().put(IO.OUT, ItemRecipeCapability.CAP, List.of(primitive.exportItems));
            } else machine.getCapabilitiesProxy().put(IO.OUT, ItemRecipeCapability.CAP, List.of(output.getInventory()));
            energy = electric ? new NotifiableEnergyContainer(machine, 1000000, 32, 1, 0, 0) : null;
            if (energy != null) {
                energy.setEnergyStored(1000000); machine.getCapabilitiesProxy().put(IO.IN, EURecipeCapability.CAP, List.of(energy));
                field(machine, "energyContainer").set(machine, new EnergyContainerList(List.of(energy)));
                field(machine, "tier").setInt(machine, 1);
            } else if (machine instanceof WorkableElectricMultiblockMachine)
                field(machine, "energyContainer").set(machine, new EnergyContainerList(List.of()));
            if (machine instanceof IndustrialSteamPlatformMachine steam)
                field(machine, "tier").setInt(machine, steam.getEffectiveTier());
            ((CloudMachineIdentity) machine).cloud$machineInstance(true);
            cloud.iv$getState().owner = IvBuffers.ownerKey(machine);
            if (machine instanceof org.gtlcore.gtlcore.api.machine.trait.IRecipeCapabilityMachine capabilities) capabilities.upDate();
            if (machine instanceof IndustrialSteamPlatformMachine) {
                var registration = IndustrialSteamPlatformMachine.class.getDeclaredMethod("registerSteamEnergyHandler");
                registration.setAccessible(true); registration.invoke(machine);
            }
            machine.getRecipeLogic().markLastRecipeDirty();
            machine.getRecipeLogic().setWorkingEnabled(true);
            if (!IvMachineScope.crossRecipeEnabled(machine) || !machine.hasProxies() || !machine.isRecipeLogicAvailable())
                throw new AssertionError("Fixture has no real cloud entry path: " + id);
        }
        MetaMachineBlockEntity place(String id, int x) {
            var definition = GTRegistries.MACHINES.get(new ResourceLocation(id));
            if (definition == null) throw new AssertionError("Missing actual registered fixture machine " + id);
            var pos = new BlockPos(x, 80, 0);
            var holder = (MetaMachineBlockEntity) definition.getBlockEntityType().create(pos, definition.defaultBlockState());
            holder.setLevel(world); world.blockEntities.put(pos, holder);
            world.renderedBlocks.put(pos, new BlockInfo(definition.defaultBlockState())); holders.add(holder); return holder;
        }
        void parts(List<? extends IMultiPart> parts) throws Exception {
            field(machine, "parts").set(machine, new ArrayList<>(parts));
            field(machine, "partPositions").set(machine, parts.stream().map(part -> part.self().getPos()).toArray(BlockPos[]::new));
        }
        void steam(long amount) throws Exception { field(machine, "steamStored").setLong(machine, amount); }
        IvJob order(int slot, long operations, long eut, int duration) {
            GTRecipe recipe = machine.getRecipeType().recipeBuilder("cloud_steam_audit_" + slot)
                    .inputItems(Items.APPLE).outputItems(Items.DIAMOND).EUt(eut).duration(duration).buildRawRecipe();
            var job = new IvJob(slot, recipe, new CompoundTag(), Map.of(INPUT, operations), Map.of(), operations);
            job.cloudMachine = ((CloudMachineIdentity) machine).cloud$machineInstance(false);
            cloud.iv$getState().jobs.add(job); return job;
        }
        void tick() throws Exception {
            field(machine.getHolder(), "offset").setLong(machine.getHolder(), ++ticks - 1000L);
            machine.getRecipeLogic().serverTick();
        }
        void until(java.util.function.BooleanSupplier condition, int limit, String label) throws Exception {
            for (int tick = 0; !condition.getAsBoolean() && tick < limit; tick++) tick();
            if (!condition.getAsBoolean()) throw new AssertionError(label + ": " + ((IvNativeAccess) machine.getRecipeLogic()).iv$summary()
                    + " remote=" + machine.isRemote() + " formed=" + machine.isFormed() + " available=" + machine.isRecipeLogicAvailable()
                    + " cross=" + IvMachineScope.crossRecipeEnabled(machine) + " managed=" + ((IvNativeAccess) machine.getRecipeLogic()).iv$managed()
                    + " enabled=" + machine.getRecipeLogic().isWorkingEnabled() + " status=" + machine.getRecipeLogic().getStatus()
                    + " tick=" + machine.getOffsetTimer() + " engine=" + field(machine.getRecipeLogic(), "iv$engine").get(machine.getRecipeLogic())
                    + " jobs=" + cloud.iv$getState().jobs.stream().map(job -> job.save().toString()).toList());
        }
        @Override public void close() { for (var holder : holders) holder.setLevel(null); }
    }

    /** Only readiness is deterministic; real inherited inventory, binding and callbacks are retained. */
    private static final class ReadyCloud extends CloudPatternBufferMachine {
        ReadyCloud(IMachineBlockEntity holder) { super(holder); }
        @Override public boolean cloudReady(boolean checkController) { return true; }
        @Override public String cloudReadyFailure(boolean checkController) { return ""; }
    }
    private static final class AuditWorld extends TrackedDummyWorld {
        private AuditWorld() { super(null); }
        @Override public Level getLevel() { return this; }
        @Override public net.minecraft.core.RegistryAccess registryAccess() { return net.minecraft.core.RegistryAccess.EMPTY; }
        @Override public net.minecraft.world.flag.FeatureFlagSet enabledFeatures() { return net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS; }
        @Override public net.minecraft.world.item.crafting.RecipeManager getRecipeManager() { return new net.minecraft.world.item.crafting.RecipeManager(); }
        @Override public long getGameTime() { return 1000; }
        @Override public BlockPos getSharedSpawnPos() { return BlockPos.ZERO; }
        @Override public void blockEntityChanged(BlockPos pos) {}
        @Override public void setBlocksDirty(BlockPos pos, net.minecraft.world.level.block.state.BlockState before,
                net.minecraft.world.level.block.state.BlockState after) {}
        @Override public net.minecraft.world.level.block.state.BlockState getBlockState(BlockPos pos) {
            var block = renderedBlocks.get(pos);
            return block == null ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState() : block.getBlockState();
        }
        @Override public net.minecraft.world.level.block.entity.BlockEntity getBlockEntity(BlockPos pos) { return blockEntities.get(pos); }
    }
    private static Field field(Object object, String name) throws Exception {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try { var field = type.getDeclaredField(name); field.setAccessible(true); return field; }
            catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(name);
    }
    private void check(boolean valid, String label) {
        if (!valid) throw new AssertionError(label); checks.add(label);
    }
}
