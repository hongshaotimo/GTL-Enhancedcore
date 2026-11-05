package com.gtl.enhancedcore.audit;

import com.google.gson.GsonBuilder;
import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableEnergyContainer;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.misc.EnergyContainerList;
import com.gtl.enhancedcore.common.recipe.iv.IvBuffers;
import com.gtl.enhancedcore.common.recipe.iv.IvMachineScope;
import com.gtl.enhancedcore.common.recipe.iv.IvNativeAccess;
import com.gtladd.gtladditions.common.machine.multiblock.controller.PlanetaryIonisationConvergenceTower;
import java.lang.reflect.Field;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.item.Items;

/** Actual registered native traits in a controlled dummy level; geometry is not exercised. */
public final class CloudNativeIdleRuntimeChecks {
    private CloudNativeIdleRuntimeChecks() {}

    public static void run(Path directory, List<String> checks) throws Exception {
        var rows = new ArrayList<Map<String, Object>>();
        nativeBatch("gtceu:large_chemical_reactor", rows, checks);
        nativeBatch("gtceu:plasma_condenser", rows, checks);
        ownedOrderBoundary(rows, checks);
        explicitIvBoundary(rows, checks);
        biosphereTick(rows, checks);
        pictGenerationTick(rows, checks);
        var result = new LinkedHashMap<String, Object>();
        result.put("status", "PASS"); result.put("cases", rows);
        result.put("scope", "Actual registered suppliers and transformed setupRecipe/serverTick methods in a deterministic dummy Level. Real item output and EU accounting for directly admitted ordinary recipes; actual native BS energy sample and PICT paid-cycle continuation. Separate lambda-seam cases verify routing only.");
        result.put("notCovered", List.of("Geometry/formation", "Full native recipe lookup or PICT drone/fluid cycle startup", "Autonomous miner block destruction requiring ServerLevel", "Heart of the Universe wireless generation", "Independent JVM restart"));
        Files.writeString(directory.resolve("cloud-native-idle-runtime.json"), new GsonBuilder().setPrettyPrinting().create().toJson(result));
    }

    private static void nativeBatch(String id, List<Map<String, Object>> rows, List<String> checks) throws Exception {
        try (var f = new CloudCapacityFixture(id, true)) {
            var machine = f.machine; var logic = machine.getRecipeLogic(); var access = (IvNativeAccess)logic;
            require(IvBuffers.genericCloudTarget(machine) && IvMachineScope.crossRecipeEnabled(machine)
                            && f.cloud.iv$getState().jobs.isEmpty() && !IvBuffers.genericCloudNativeInputs(machine),
                    id + " actual supplier has a cloud part, no owned jobs and no physical input assembly", checks);
            require(access.iv$nativeInputExecution(), id + " empty generic cloud owner permits inherited native helpers", checks);
            var recipe = machine.getRecipeType().recipeBuilder("audit_empty_cloud_native_batch")
                    .outputItems(Items.DIAMOND).duration(4).EUt(8).buildRawRecipe();
            long energyBefore = f.energy.getEnergyStored();
            logic.setupRecipe(recipe);
            require(logic.isWorking() && logic.getLastRecipe() == recipe && logic.getDuration() == 4,
                    id + " actual inherited setupRecipe starts a directly admitted native batch with empty cloud orders", checks);
            f.tick();
            require(logic.getProgress() == 1 && access.iv$nativeInputsActive() && f.energy.getEnergyStored() == energyBefore - 8,
                    id + " actual serverTick advances native progress and pays eight physical EU", checks);
            for (int tick = 0; tick < 3; tick++) f.tick();
            long diamonds = 0;
            for (var handler : machine.getCapabilitiesProxy().get(IO.OUT, ItemRecipeCapability.CAP))
                if (handler instanceof com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler items)
                    for (int slot = 0; slot < items.getSlots(); slot++)
                        if (items.getStackInSlot(slot).is(Items.DIAMOND)) diamonds += items.getStackInSlot(slot).getCount();
            require(diamonds == 1 && f.energy.getEnergyStored() == energyBefore - 32 && f.cloud.iv$getState().jobs.isEmpty(),
                    id + " four real native ticks deliver exactly one physical diamond and pay exactly thirty-two EU", checks);
            rows.add(Map.of("id", id, "evidence", "Direct native setupRecipe + four actual serverTick calls, physical output and EU", "diamonds", diamonds,
                    "euPaid", energyBefore - f.energy.getEnergyStored(), "nativeLookupExercised", false));
        }
    }

    private static void ownedOrderBoundary(List<Map<String, Object>> rows, List<String> checks) throws Exception {
        try (var f = new CloudCapacityFixture("gtceu:large_chemical_reactor", true)) {
            var access = (IvNativeAccess)f.machine.getRecipeLogic(); int[] calls = new int[2]; boolean[] passive = {false};
            access.iv$runMixedInputs(() -> { calls[0]++; passive[0] = access.iv$nativeInputExecution(); }, allow -> calls[1]++, () -> false);
            require(calls[0] == 1 && calls[1] == 0 && passive[0], "Empty generic cloud owner selects a complete passive native tick in the routing seam", checks);
            var unrelated = f.order(0, 1, 8, 20); unrelated.cloudMachine = UUID.randomUUID();
            access.iv$runMixedInputs(() -> calls[0]++, allow -> calls[1]++, () -> false);
            require(calls[0] == 2 && calls[1] == 0, "Another controller's job in a shared cloud buffer does not suppress the idle owner's native tick", checks);
            var owned = f.order(1, 1, 8, 20);
            require(!access.iv$nativeInputExecution(), "An actual owned cloud job closes the idle inherited-helper exemption", checks);
            access.iv$runMixedInputs(() -> calls[0]++, allow -> calls[1]++, () -> false);
            require(calls[0] == 2 && calls[1] == 1 && owned.remaining == 1 && !owned.active(),
                    "Owned cloud job selects the order callback without running native callback or touching stock in the routing seam", checks);
            rows.add(Map.of("id", "gtceu:large_chemical_reactor", "evidence", "iv$runMixedInputs lambda seam only", "nativeCalls", calls[0], "orderCalls", calls[1]));
        }
    }

    private static void explicitIvBoundary(List<Map<String, Object>> rows, List<String> checks) throws Exception {
        try (var f = new CloudCapacityFixture("gtceu:huge_incubator", true)) {
            var access = (IvNativeAccess)f.machine.getRecipeLogic(); int[] calls = new int[2];
            require(IvMachineScope.nativeTarget(f.machine) && !IvBuffers.genericCloudTarget(f.machine)
                            && IvMachineScope.crossRecipeEnabled(f.machine) && !access.iv$nativeInputExecution(),
                    "Explicit IV opt-in retains isolated helper guards with empty orders", checks);
            access.iv$runMixedInputs(() -> calls[0]++, allow -> calls[1]++, () -> false);
            require(calls[0] == 0 && calls[1] == 1, "Explicit IV opt-in retains its order callback rather than the generic-cloud idle fallback in the routing seam", checks);
            rows.add(Map.of("id", "gtceu:huge_incubator", "evidence", "iv$runMixedInputs lambda seam only", "nativeCalls", calls[0], "orderCalls", calls[1]));
        }
    }

    private static void biosphereTick(List<Map<String, Object>> rows, List<String> checks) throws Exception {
        try (var f = new CloudCapacityFixture("gtladditions:biosphere_iii", true)) {
            var machine = f.machine; var logic = machine.getRecipeLogic();
            field(machine, "energySample").setLong(machine, Long.MIN_VALUE);
            f.tick();
            require(field(machine, "energySample").getLong(machine) == 0 && logic.getStatus() == RecipeLogic.Status.IDLE,
                    "Actual BS serverTick executes native energy-sample initialization and no-module IDLE state with an empty cloud part", checks);
            field(machine, "currentSampleEnergy").set(machine, BigInteger.valueOf(400));
            for (int tick = 0; tick < 19; tick++) f.tick();
            require(field(machine, "energySample").getLong(machine) == 1
                            && field(machine, "currentSampleEnergy").get(machine).equals(BigInteger.ZERO)
                            && field(machine, "averageEnergyConsumption").get(machine).equals(BigInteger.valueOf(20)),
                    "Twenty actual BS ticks close its native energy window and retain the exact average of twenty EU per tick", checks);
            logic.setWorkingEnabled(false); f.tick();
            require(logic.getStatus() == RecipeLogic.Status.SUSPEND, "Actual BS native tick retains the user's stop state", checks);
            rows.add(Map.of("id", "gtladditions:biosphere_iii", "evidence", "Actual custom serverTick and native energy fields; no module production", "averageEUt", 20));
        }
    }

    private static void pictGenerationTick(List<Map<String, Object>> rows, List<String> checks) throws Exception {
        try (var f = new CloudCapacityFixture("gtladditions:planetary_ionisation_convergence_tower", true)) {
            var machine = f.machine; var logic = machine.getRecipeLogic();
            var coil = PlanetaryIonisationConvergenceTower.CoilToEnergy.TITAN_STEEL;
            field(machine, "coilEnergy").set(machine, coil); field(machine, "maxStorageEUt").setLong(machine, Long.MAX_VALUE);
            field(machine, "startCycle").setBoolean(machine, true); field(machine, "particlePos").set(machine, null);
            field(logic, "progress").setInt(logic, 1);
            var output = new NotifiableEnergyContainer(machine, Long.MAX_VALUE, 0, 0, Long.MAX_VALUE, 1);
            machine.getCapabilitiesProxy().put(IO.OUT, EURecipeCapability.CAP, List.of(output));
            field(machine, "energyContainer").set(machine, new EnergyContainerList(List.of(output)));
            long perTick = coil.getDischargePower();
            require(perTick > 0 && f.cloud.iv$getState().jobs.isEmpty(), "PICT native generation probe uses an actual positive-power coil and empty cloud orders", checks);
            f.tick();
            require(logic.getProgress() == 2 && logic.isWorking() && output.getEnergyStored() == perTick
                            && field(machine, "storageEUt").getLong(machine) == 0,
                    "Actual PICT custom serverTick continues a paid native cycle and transfers the exact coil discharge into physical EU", checks);
            rows.add(Map.of("id", "gtladditions:planetary_ionisation_convergence_tower", "evidence", "Actual custom serverTick paid-cycle continuation; native drone/fluid startup not exercised", "physicalEU", output.getEnergyStored(), "progress", logic.getProgress()));
        }
    }

    private static Field field(Object object, String name) throws Exception {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try { var field = type.getDeclaredField(name); field.setAccessible(true); return field; }
            catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(name);
    }
    private static void require(boolean condition, String label, List<String> checks) {
        if (!condition) throw new AssertionError(label); checks.add(label);
    }
}
