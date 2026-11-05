package com.gtl.enhancedcore.audit;

import appeng.api.stacks.AEItemKey;
import com.google.gson.GsonBuilder;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine;
import com.gtl.enhancedcore.common.recipe.FusionParallelPolicy;
import com.gtl.enhancedcore.common.recipe.iv.*;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Items;
import org.gtlcore.gtlcore.api.recipe.IGTRecipe;

/** Actual registered factories and native modifier admission in the bounded dummy fixture. */
public final class CloudModifierCapacityRuntimeChecks {
    private CloudModifierCapacityRuntimeChecks() {}
    public static void run(Path directory, List<String> checks) throws Exception {
        var rows = new ArrayList<Map<String, Object>>();
        try (var f = new CloudCapacityFixture("gtceu:assembly_line", true)) {
            require(IvMachineScope.parallel(f.machine, null) == 64 && IvMachineScope.budget(f.machine) == 64,
                    "actual_assembly_cloud_capacity_and_shared_budget_are_fixed64", checks);
            var job = f.order(0, 128, 8, 100000);
            var raw = job.recipe; var original = job.save();
            var input = AEItemKey.of(Items.APPLE); var output = AEItemKey.of(Items.DIAMOND);
            var lowPower = new IvNativeMatchContext(f.machine, job.inventory, Map.of(), 128, 64);
            var limited = lowPower.prepare(raw);
            require(limited != null && IGTRecipe.of(limited).getRealParallels() == 4 && lowPower.reserved() == 4,
                    "actual_assembly_modifier_reduces_fixed64_to_four_on_real_32EU_supply", checks);
            f.highPower();
            var full = new IvNativeMatchContext(f.machine, job.inventory, Map.of(), 128, 64);
            var prepared = full.prepare(raw);
            require(prepared != null && IGTRecipe.of(prepared).getRealParallels() == 64 && full.reserved() == 64,
                    "actual_transformed_assembly_modifier_admits64_from_order_owned_stock_with_sufficient_power", checks);
            var consumed = IvRecipeInputs.plan(prepared, job.inventory, Map.of(), 1);
            var produced = IvRecipeInputs.outputs(prepared.outputs);
            require(consumed != null && consumed.equals(Map.of(input, 64L)) && produced.equals(Map.of(output, 64L))
                            && job.save().equals(original) && prepared.conditions.equals(raw.conditions),
                    "simulated64_has_exact64_input_output_preserves_original_conditions_and_does_not_pay_or_mutate_job", checks);
            var shortStock = new IvNativeMatchContext(f.machine, Map.of(input, 7L), Map.of(), 128, 64);
            var seven = shortStock.prepare(raw);
            require(seven != null && IGTRecipe.of(seven).getRealParallels() == 7 && shortStock.reserved() == 7,
                    "actual_assembly_modifier_reduces64_to_seven_available_order_inputs", checks);
            var share = new IvNativeMatchContext(f.machine, job.inventory, Map.of(), 128, 3);
            var three = share.prepare(raw);
            require(three != null && IGTRecipe.of(three).getRealParallels() == 3 && share.reserved() == 3,
                    "actual_modifier_respects_three_operation_reservation_instead_of_forcing64", checks);
            var oneOrder = new IvNativeMatchContext(f.machine, job.inventory, Map.of(), 1, 64);
            var one = oneOrder.prepare(raw);
            require(one != null && IGTRecipe.of(one).getRealParallels() == 1 && oneOrder.reserved() == 1,
                    "one_operation_order_does_not_steal_other_orders_material_to_fill_fixed64", checks);
            rows.add(Map.of("machine", f.machine.getDefinition().getId().toString(), "capacity", 64,
                    "powerLimited", 4, "stockLimited", 7, "reservedShareLimited", 3,
                    "fullParallel", IGTRecipe.of(prepared).getRealParallels(), "prepared", prepared.data.toString(),
                    "inputPlan", consumed.toString(), "outputPlan", produced.toString()));
            int index = 10;
            for (String path : List.of("luv_fusion_reactor", "zpm_fusion_reactor", "uv_fusion_reactor", "uhv_fusion_reactor", "uev_fusion_reactor")) {
                var fusion = (WorkableMultiblockMachine)f.place("gtceu:" + path, index++).getMetaMachine();
                int expected = FusionParallelPolicy.limit("gtceu", path);
                require(IvNativeCapacity.modifierParallel(fusion) == expected && IvMachineScope.parallel(fusion, null) == expected,
                        path + "_actual_registered_factory_cloud_capacity_matches_existing_native_modifier_policy", checks);
                var context = new IvNativeMatchContext(fusion, Map.of(input, 128L), Map.of(), 128, 7);
                require(context.limit(raw, expected) == 7,
                        path + "_direct_modifier_list_parallel_tail_respects_actual_reserved_share", checks);
                rows.add(Map.of("machine", fusion.getDefinition().getId().toString(), "class", fusion.getClass().getName(),
                        "capacity", expected, "directTailReservedLimit", 7));
            }
        }
        try (var forge = new CloudCapacityFixture("gtceu:star_ultimate_material_forge_factory", true)) {
            require(forge.machine instanceof com.gtl.enhancedcore.common.machine.StarUltimateForgeMutableMachine
                            && IvNativeCapacity.modifierParallel(forge.machine) == -1
                            && IvMachineScope.parallel(forge.machine, null) == 50000,
                    "actual_ADD_forge_retains_native_getter50000_without_fixed_modifier_override", checks);
            rows.add(Map.of("machine", forge.machine.getDefinition().getId().toString(),
                    "class", forge.machine.getClass().getName(), "capacity", IvMachineScope.parallel(forge.machine, null)));
        }
        try (var lowVoltage = new CloudCapacityFixture("gtceu:assembly_line", true)) {
            var job = lowVoltage.order(0, 128, 1000, 100000);
            long beforeEnergy = lowVoltage.energy.getEnergyStored();
            require(com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability.CAP
                            .getMaxParallelRatio(lowVoltage.machine, job.recipe, 64) == 0,
                    "actual_assembly_EU_capability_reports_zero_1000EU_parallel_on_32EU_supply", checks);
            var context = new IvNativeMatchContext(lowVoltage.machine, job.inventory, Map.of(), 128, 64);
            var candidate = context.prepare(job.recipe);
            require(candidate == null || IGTRecipe.of(candidate).getRealParallels() <= 1,
                    "actual_assembly_1000EU_candidate_on_32EU_supply_never_treats_zero_power_ratio_as_unlimited", checks);
            lowVoltage.until(() -> job.error.equals("gtl_enhancedcore.diagnostic.power")
                            || job.error.equals("gtl_enhancedcore.diagnostic.voltage"), 20,
                    "Assembly insufficient input voltage retains an unpaid cloud order");
            require(!job.active() && job.remaining == 128
                            && job.inventory.equals(Map.of(AEItemKey.of(Items.APPLE), 128L))
                            && job.completion.isEmpty() && job.pending.isEmpty()
                            && job.energyTotal.signum() == 0 && lowVoltage.energy.getEnergyStored() == beforeEnergy,
                    "actual_assembly_serverTick_rejects1000EU_on_32EU_before_input_payment_or_output_creation", checks);
            rows.add(Map.of("machine", "gtceu:assembly_line", "recipeEUt", 1000, "inputEUt", 32,
                    "preparedParallel", candidate == null ? 0 : IGTRecipe.of(candidate).getRealParallels(),
                    "started", job.active(), "remaining", job.remaining, "diagnostic", job.error));
        }
        paidAssemblyBatches(rows, checks);
        var result = new LinkedHashMap<String, Object>(); result.put("status", "PASS"); result.put("cases", rows);
        result.put("checks", checks.stream().filter(check -> check.startsWith("cloud_modifier_capacity:")).toList());
        result.put("boundary", "Initialized real registered controller factories in CloudCapacityFixture; geometry and readiness use the existing bounded dummy fixture. Native modifier simulation checks exact64/7/4/3/1 admission and untouched source job/conditions. Actual assembly serverTick executes two successful64 batches, consumes128 apples, settles128 diamonds, and physically pays each committed eut*duration bill exactly once. The first pending64 output is collected by this audit and synthetically acknowledged to permit the second batch; this is not an AE delivery or CPU-plan test. A real rejection path checks1000EU demand against32EU input without payment or output. Research unlocking, fusion heat startup/paid cycles and cross-order combining are not claimed. Fusion checks declared128/512 and reserved-share guard; ADD forge checks actual getter50000.");
        Files.writeString(directory.resolve("cloud-modifier-capacity-runtime.json"), new GsonBuilder().setPrettyPrinting().create().toJson(result));
    }
    private static void paidAssemblyBatches(List<Map<String, Object>> rows, List<String> checks) throws Exception {
        try (var f = new CloudCapacityFixture("gtceu:assembly_line", true)) {
            // Supply exactly 64 * 8 EU/t: enough for fixed64, while retaining
            // the raw twenty working ticks instead of overclocking to one tick.
            f.highPower();
            f.energy.resetBasicInfo(1000000, 512, 1, 0, 0); f.energy.setEnergyStored(1000000);
            field(f.machine, "energyContainer").set(f.machine,
                    new com.gregtechceu.gtceu.api.misc.EnergyContainerList(List.of(f.energy)));
            if (f.machine instanceof org.gtlcore.gtlcore.api.machine.trait.IRecipeCapabilityMachine capabilities)
                capabilities.upDate();
            var input = AEItemKey.of(Items.APPLE); var output = AEItemKey.of(Items.DIAMOND);
            var job = f.order(0, 128, 8, 20);
            long initialEnergy = f.energy.getEnergyStored();
            var batches = new ArrayList<Map<String, Object>>();
            BigInteger ledger = BigInteger.ZERO;
            long collectedDiamonds = 0;
            for (int batch = 1; batch <= 2; batch++) {
                final int index = batch;
                f.until(job::active, 25, "Assembly successful64 batch " + batch + " starts through actual serverTick");
                String admission = "batch=" + batch + " job=" + job.save()
                        + " storedEU=" + f.energy.getEnergyStored() + " nativeSnapshotRealParallels="
                        + (job.nativeRecipe == null ? -1 : IGTRecipe.of(job.nativeRecipe).getRealParallels())
                        + " status=" + f.machine.getRecipeLogic().getStatus();
                System.out.println("ASSEMBLY_PAID_BATCH_DETAILS " + admission);
                require(!job.halted && job.parallel == 64 && job.nativeCapacity == 64
                                && job.remaining == 128L - 64L * batch
                                && job.inventory.equals(batch == 1 ? Map.of(input, 64L) : Map.of())
                                && job.completion.equals(Map.of(output, 64L)) && job.pending.isEmpty()
                                && job.nativeRecipe != null
                                // GTRecipe.copy() resets upstream's temporary realParallels
                                // extension. The committed contents and order own execution.
                                && Map.of(input, 64L).equals(IvRecipeInputs.plan(job.nativeRecipe, Map.of(input, 64L), Map.of(), 1))
                                && IvRecipeInputs.outputs(job.nativeRecipe.outputs).equals(Map.of(output, 64L))
                                && com.gregtechceu.gtceu.api.recipe.RecipeHelper.getInputEUt(job.nativeRecipe) == job.eut,
                        "actual_paid_assembly_batch" + batch + "_commits64_capacity64_consumes_exact64_inputs_and_owns64_outputs; " + admission, checks);
                BigInteger total = job.energyTotal;
                require(job.duration > 1 && job.elapsed > 0 && job.eut > 0
                                && total.equals(BigInteger.valueOf(job.eut).multiply(BigInteger.valueOf(job.duration)))
                                && BigInteger.valueOf(initialEnergy - f.energy.getEnergyStored())
                                .equals(ledger.add(total.subtract(job.energyLeft))),
                        "actual_paid_assembly_batch" + batch + "_first_working_tick_physically_pays_only_committed_EU_once", checks);
                var snapshot = new LinkedHashMap<String, Object>();
                snapshot.put("batch", batch); snapshot.put("parallel", job.parallel); snapshot.put("nativeCapacity", job.nativeCapacity);
                snapshot.put("eut", job.eut); snapshot.put("duration", job.duration); snapshot.put("energyTotal", total.toString());
                snapshot.put("firstObservedElapsed", job.elapsed); snapshot.put("firstObservedEnergyLeft", job.energyLeft.toString());
                batches.add(snapshot);
                f.until(() -> job.completedOperations == 64L * index, job.duration + 25,
                        "Assembly actual paid64 batch " + batch + " completes");
                ledger = ledger.add(total);
                require(!job.active() && !job.halted && job.pending.equals(Map.of(output, 64L))
                                && job.pendingOperations == 64 && job.completion.isEmpty() && job.energyLeft.signum() == 0
                                && BigInteger.valueOf(initialEnergy - f.energy.getEnergyStored()).equals(ledger),
                        "actual_paid_assembly_batch" + batch + "_completes64_outputs_with_exact_physical_EU_debit", checks);
                collectedDiamonds = Math.addExact(collectedDiamonds, job.pending.get(output));
                if (batch == 1) {
                    long afterFirstEnergy = f.energy.getEnergyStored();
                    for (int tick = 0; tick < 3; tick++) f.tick();
                    require(job.completedOperations == 64 && !job.active() && job.remaining == 64
                                    && job.inventory.equals(Map.of(input, 64L)) && job.pending.equals(Map.of(output, 64L))
                                    && f.energy.getEnergyStored() == afterFirstEnergy,
                            "actual_paid_assembly_undelivered_first_batch_blocks_replay_and_preserves_next64_stock", checks);
                    // Collect the first completed output, then let the engine's
                    // normal acknowledgement/start path admit the second batch.
                    job.pending.clear();
                }
            }
            long finalEnergy = f.energy.getEnergyStored();
            for (int tick = 0; tick < 3; tick++) f.tick();
            require(collectedDiamonds == 128 && job.completedOperations == 128 && job.deliveredOperations == 64
                            && job.remaining == 0 && job.inventory.isEmpty() && !job.active() && !job.halted
                            && job.pending.equals(Map.of(output, 64L)) && job.pendingOperations == 64
                            && f.energy.getEnergyStored() == finalEnergy
                            && BigInteger.valueOf(initialEnergy - finalEnergy).equals(ledger),
                    "actual_paid_assembly_two64_batches_conserve128_inputs128_outputs_and_each_full_EU_bill_without_replay", checks);
            var row = new LinkedHashMap<String, Object>(); row.put("machine", "gtceu:assembly_line");
            row.put("case", "two_actual_paid64_batches"); row.put("supplyEUt", 512); row.put("inputConsumed", 128);
            row.put("outputSettled", collectedDiamonds); row.put("firstOutputCollectedByAudit", 64);
            row.put("finalPendingOutput", job.pending.toString()); row.put("completedOperations", job.completedOperations);
            row.put("paidEU", ledger.toString()); row.put("physicalEUdebit", initialEnergy - finalEnergy); row.put("batches", batches);
            row.put("deliveryBoundary", "Audit collected first pending64 and cleared it as an explicit synthetic delivery acknowledgement; final64 remains pending. No AE network claimed.");
            rows.add(row);
        }
    }
    private static java.lang.reflect.Field field(Object object, String name) throws NoSuchFieldException {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try { var field = type.getDeclaredField(name); field.setAccessible(true); return field; }
            catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(name);
    }
    private static void require(boolean condition, String label, List<String> checks) {
        if (!condition) throw new AssertionError("cloud_modifier_capacity:" + label);
        checks.add("cloud_modifier_capacity:" + label);
    }
}
