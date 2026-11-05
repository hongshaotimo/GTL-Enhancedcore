package com.gtl.enhancedcore.audit;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.service.CraftingService;
import com.google.gson.GsonBuilder;
import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.EnergyHatchPartMachine;
import com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder;
import com.gtl.enhancedcore.common.cloud.CloudPatternBufferMachine;
import com.gtl.enhancedcore.common.cloud.CloudPatternLibraryMachine;
import com.gtl.enhancedcore.common.cloud.store.CloudLedger;
import com.gtl.enhancedcore.common.cloud.store.CloudMachineIdentity;
import com.gtl.enhancedcore.common.cloud.store.CloudPatternStore;
import com.gtl.enhancedcore.common.recipe.iv.IvBuffers;
import com.gtl.enhancedcore.common.recipe.iv.IvJob;
import com.lowdragmc.lowdraglib.gui.modular.ModularUIContainer;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Future;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import org.gtlcore.gtlcore.integration.ae2.pattern.PatternQuickUploadMetadata;

/** Actual AE CPU work is frozen by block removal and resumed on a different, empty real LCR. */
public final class CloudFrozenCpuRecoveryRuntimeChecks {
    private static final ResourceLocation RECIPE = new ResourceLocation("gtl_enhancedcore", "audit_cloud_frozen_cpu_recovery");
    private static ResourceLocation registeredRecipeId;
    private static final AEKey INPUT = AEItemKey.of(Items.RABBIT_FOOT);
    private static final AEKey OUTPUT = AEItemKey.of(Items.HEART_OF_THE_SEA);
    private static ServerLevel level;
    private static CloudPatternLibraryMachine main;
    private static CloudPatternBufferMachine source, target, replacement;
    private static WorkableElectricMultiblockMachine sourceController, targetController;
    private static ServerPlayer player;
    private static List<String> checks;
    private static Path directory;
    private static BlockPos sourcePos;
    private static BlockState sourceBlock;
    private static Direction sourceFacing;
    private static IGrid acceptedGrid;
    private static IGridNode survivingAnchor;
    private static List<PhysicalHatch> sourceHatches, targetHatches;
    private static long sourceInitial, targetInitial, sourceFrozen, targetAtRestore, targetFinal;
    private static UUID entryId, frozenNode, targetMachine, craftingId;
    private static ItemStack original;
    private static IPatternDetails pattern;
    private static Future<ICraftingPlan> calculation;
    private static ICraftingCPU cpu;
    private static ICraftingLink link;
    private static IvJob sourceJob, recoveredJob;
    private static CompoundTag frozenJob, frozenState, expectedRestoredJob;
    private static BigInteger totalEnergy, frozenEnergyLeft;
    private static int stage, ticks, since;
    private static boolean sawBusy;
    private static final Map<String, Object> evidence = new LinkedHashMap<>();
    private record PhysicalHatch(EnergyHatchPartMachine hatch, IEnergyContainer container) {}

    private CloudFrozenCpuRecoveryRuntimeChecks() {}

    /** Register before any lookup cache is constructed, alongside the ordinary CPU audit recipes. */
    public static void prepareRecipes(ServerLevel world, WorkableElectricMultiblockMachine controller) {
        var recipes = new ArrayList<net.minecraft.world.item.crafting.Recipe<?>>(world.getRecipeManager().getRecipes());
        // Keep the real modifier; the chosen duration still has to pass the actual partial-progress and physical-reserve checks below.
        var recipe = GTRecipeBuilder.of(RECIPE, controller.getRecipeType()).inputItems(Items.RABBIT_FOOT)
                .outputItems(Items.HEART_OF_THE_SEA).duration(15360).EUt(8).buildRawRecipe();
        registeredRecipeId = recipe.id;
        recipes.add(recipe); controller.getRecipeType().getLookup().addRecipe(recipe);
        world.getRecipeManager().replaceRecipes(recipes);
    }

    public static void begin(ServerLevel world, CloudPatternLibraryMachine core, List<CloudPatternBufferMachine> buffers,
                             ServerPlayer actor, List<String> results) throws Exception {
        level = world; main = core; player = actor; checks = results;
        directory = world.getServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getParent().getParent();
        stage = 0; ticks = 0; since = 0; replacement = null; sourceJob = null; recoveredJob = null;
        calculation = null; cpu = null; link = null; pattern = null; frozenJob = null; frozenState = null;
        expectedRestoredJob = null; totalEnergy = null; frozenEnergyLeft = null; craftingId = null; sawBusy = false; evidence.clear();
        evidence.put("registeredRecipeId", registeredRecipeId.toString());
        require(buffers.size() == 2, "fixture_has_two_real_cloud_endpoints");
        source = buffers.get(0); target = buffers.get(1);
        sourceController = (WorkableElectricMultiblockMachine)IvBuffers.controller(source);
        targetController = (WorkableElectricMultiblockMachine)IvBuffers.controller(target);
        require(sourceController != targetController && sourceController.isFormed() && targetController.isFormed()
                        && main.isFormed() && source.cloudReady(true) && target.cloudReady(true)
                        && source.cloudMode().equals(target.cloudMode())
                        && sourceController.getDefinition() == targetController.getDefinition(),
                "distinct_real_LCRs_are_physically_formed_and_compatible");
        require(source.iv$getState().jobs.isEmpty() && target.iv$getState().jobs.isEmpty(), "both_real_order_ledgers_start_empty");
        require(!org.gtlcore.gtlcore.integration.ae2.graph.CraftingEngineRouter.useGraph(), "real_legacy_AE_planner_and_CPU_are_selected");
        require(crafting().getCpus().stream().anyMatch(candidate -> candidate instanceof CraftingCPUCluster
                        && !candidate.isBusy() && candidate.getAvailableStorage() >= 65536 && candidate.getCoProcessors() > 0),
                "real_64K_CPU_with_coprocessor_is_available");
        sourcePos = source.getPos().immutable(); sourceBlock = source.self().getBlockState(); sourceFacing = source.getFrontFacing();
        frozenNode = source.cloudNodeId(); targetMachine = ((CloudMachineIdentity)targetController).cloud$machineInstance(false);
        acceptedGrid = source.getGrid(); survivingAnchor = main.getMainNode().getNode();
        require(acceptedGrid == target.getGrid() && survivingAnchor != null && survivingAnchor.getGrid() == acceptedGrid,
                "source_target_and_surviving_library_anchor_share_the_original_real_grid");
        action(target, "pause", new CompoundTag());
        require(!target.iv$getState().accepting && target.iv$getState().jobs.isEmpty(), "empty_target_is_paused_through_authorized_production_UI");
        if (!source.iv$getState().accepting) action(source, "resume", new CompoundTag());
        require(source.iv$getState().accepting, "source_admission_is_enabled_through_production_state");
        sourceHatches = hatches(sourceController); targetHatches = hatches(targetController);
        fillOnce(sourceHatches); fillOnce(targetHatches);
        sourceInitial = energy(sourceHatches); targetInitial = energy(targetHatches);
        evidence.put("sourcePhysicalHatches", hatchDescriptions(sourceHatches));
        evidence.put("targetPhysicalHatches", hatchDescriptions(targetHatches));
        evidence.put("sourceInitialEU", sourceInitial); evidence.put("targetInitialEU", targetInitial);
        require(stock(INPUT) == 0 && stock(OUTPUT) == 0, "physical_ME_starts_without_audit_input_or_output");
        original = PatternDetailsHelper.encodeProcessingPattern(new GenericStack[]{new GenericStack(INPUT, 1)},
                new GenericStack[]{new GenericStack(OUTPUT, 1)});
        PatternQuickUploadMetadata.writeRecipeTypeId(original, source.cloudMode());
        original.getOrCreateTag().putBoolean("cloudFrozenCpuRecoveryAudit", true);
        var physical = original.copy(); var operation = UUID.randomUUID(); var store = store();
        entryId = store.upload(main.libraryId(), player.getUUID(), physical, new CompoundTag(), operation).id();
        require(physical.isEmpty(), "central_upload_consumes_exactly_one_physical_original");
        store.acknowledge(main.libraryId(), player.getUUID(), operation); store.checkpointLibrary(main.libraryId());
        writeSnapshot("RUNNING", "");
    }

    /** The runner must suppress its normal GT container refill throughout this stage. */
    public static boolean wantOuterPower() { return false; }

    /** Replace fixture children[0] with this object only after step() returns true. */
    public static CloudPatternBufferMachine replacementSource() {
        if (replacement == null) throw new IllegalStateException("Source cloud part has not been rebuilt");
        return replacement;
    }

    public static boolean step() throws Exception {
        try { return stepInternal(); }
        catch (Exception | Error failure) { writeSnapshot("FAIL", failure.toString()); throw failure; }
    }

    private static boolean stepInternal() throws Exception {
        if (++ticks > 1800) throw new AssertionError("Frozen CPU recovery timeout stage=" + stage + " " + diagnostics());
        if (ticks % 20 == 0) writeSnapshot("RUNNING", "");
        if (link != null) {
            sawBusy |= cpu.isBusy();
            guard(!link.isCanceled() && craftingId.equals(link.getCraftingID()), "original_CPU_link_was_cancelled_or_replaced");
            if (cpu.isBusy()) guard(((CraftingCPUCluster)cpu).craftingLogic.getLastLink() == link, "busy_CPU_no_longer_owns_original_standalone_link");
        }
        switch (stage) {
            case 0 -> {
                pattern = pattern(source);
                if (pattern == null || providerCount() != 1 || !crafting().isCraftable(OUTPUT)) return false;
                var leases = store().library(main.libraryId()).entry(entryId).leases();
                require(leases.size() == 1 && leases.getFirst().nodeId().equals(frozenNode)
                                && target.getAvailablePatterns().stream().noneMatch(pattern::equals)
                                && sourceIsIndexed(),
                        "paused_target_has_no_route_and_actual_AE_provider_pool_contains_only_source");
                require(target.iv$getState().jobs.isEmpty() && energy(sourceHatches) == sourceInitial && energy(targetHatches) == targetInitial,
                        "physical_GT_energy_is_not_refilled_or_spent_before_submission");
                require(target.getGrid().getStorageService().getInventory().insert(INPUT, 1, Actionable.MODULATE, IActionSource.ofPlayer(player)) == 1
                                && stock(INPUT) == 1, "one_actual_input_is_inserted_into_real_ME_storage");
                calculation = crafting().beginCraftingCalculation(level, () -> IActionSource.ofPlayer(player), OUTPUT, 1,
                        CalculationStrategy.REPORT_MISSING_ITEMS); stage = 1;
            }
            case 1 -> {
                if (!calculation.isDone()) return false;
                var plan = calculation.get();
                require(!plan.simulation() && plan.missingItems().isEmpty() && plan.patternTimes().size() == 1
                                && plan.patternTimes().getOrDefault(pattern, 0L) == 1
                                && plan.finalOutput().what().equals(OUTPUT) && plan.finalOutput().amount() == 1,
                        "real_AE_plan_contains_exactly_one_whole_processing_pattern_repetition");
                cpu = crafting().getCpus().stream().filter(candidate -> candidate instanceof CraftingCPUCluster
                        && !candidate.isBusy() && candidate.getAvailableStorage() >= Math.max(65536, plan.bytes())
                        && candidate.getCoProcessors() > 0).findFirst().orElseThrow();
                var submitted = crafting().submitJob(plan, null, cpu, false, IActionSource.ofPlayer(player));
                evidence.put("submissionSuccessful", submitted.successful());
                evidence.put("submissionErrorCode", String.valueOf(submitted.errorCode()));
                evidence.put("submissionReturnedLink", submitted.link() != null);
                require(submitted.successful(), "real_64K_CPU_accepts_calculated_plan_" + submitted.errorCode());
                // AE2 successful(null) is the normal result without a requester; the real CPU link is in its live job.
                link = ((CraftingCPUCluster)cpu).craftingLogic.getLastLink();
                require(submitted.link() == null && link != null && link.isStandalone() && cpu.isBusy(),
                        "successful_standalone_submission_has_its_original_real_CPU_job_link");
                craftingId = link.getCraftingID(); sawBusy = cpu.isBusy(); stage = 2;
                evidence.put("CPUclass", cpu.getClass().getName()); evidence.put("craftingId", craftingId.toString());
                evidence.put("CPUlinkIsStandalone", link.isStandalone());
            }
            case 2 -> {
                guard(target.iv$getState().jobs.isEmpty(), "paused_target_received_a_job");
                if (source.iv$getState().jobs.isEmpty()) {
                    guard(cpu.isBusy() && stock(OUTPUT) == 0, "native_batch_completed_before_partial_payment_could_be_observed");
                    return false;
                }
                guard(source.iv$getState().jobs.size() == 1, "single_CPU_repetition_created_multiple_orders");
                sourceJob = source.iv$getState().jobs.getFirst(); sourceJob.validateAccounting();
                guard(entryId.equals(sourceJob.cloudPattern) && sourceJob.recipe.id.equals(registeredRecipeId)
                                && ((CloudMachineIdentity)sourceController).cloud$machineInstance(false).equals(sourceJob.cloudMachine)
                                && sourceJob.totalOperations == 1 && sourceJob.cloudPatternOperations == 1
                                && !sourceJob.cancelled && !sourceJob.halted, "actual_source_job_identity_or_quantity_is_incorrect");
                if (!sourceJob.active() || sourceJob.elapsed == 0) return false;
                require(sourceJob.nativeRecipe != null && !sourceJob.nativeWireless && sourceJob.duration >= 2
                                && sourceJob.energyLeft.signum() > 0 && sourceJob.energyLeft.compareTo(sourceJob.energyTotal) < 0
                                && sourceJob.energyTotal.equals(BigInteger.valueOf(sourceJob.eut).multiply(BigInteger.valueOf(sourceJob.duration)))
                                && sourceJob.energyTotal.compareTo(BigInteger.valueOf(Math.min(sourceInitial, targetInitial))) <= 0,
                        "actual_committed_native_batch_has_observable_partial_payment_and_fits_both_real_hatch_reserves");
                require(stock(INPUT) == 0 && stock(OUTPUT) == 0 && sourceJob.parallel == 1 && sourceJob.remaining == 0
                                && sourceJob.inventory.isEmpty() && sourceJob.pending.isEmpty() && sourceJob.refunds.isEmpty()
                                && sourceJob.completion.size() == 1 && sourceJob.completion.getOrDefault(OUTPUT, 0L) == 1
                                && sourceJob.completedOperations == 0 && sourceJob.deliveredOperations == 0,
                        "one_physical_input_is_committed_once_and_exactly_one_unfinished_output_belongs_to_real_job");
                freezeSource(); stage = 3; since = ticks;
            }
            case 3 -> {
                assertFrozenEnergy();
                if (ticks - since < 3 || sourceController.isFormed() || sourceController.getParts().contains(source)
                        || !target.cloudReady(true)) return false;
                require(main.getMainNode().getNode() == survivingAnchor && survivingAnchor.getGrid() == acceptedGrid
                                && target.getGrid() == acceptedGrid && target.iv$getState().jobs.isEmpty(),
                        "empty_compatible_target_retains_the_original_surviving_physical_ME_grid");
                targetAtRestore = energy(targetHatches);
                var payload = new CompoundTag(); payload.putString("recoveryId", frozenNode.toString());
                action(target, "recover", payload);
                require(target.iv$getState().jobs.size() == 1, "authorized_production_recover_UI_restores_real_frozen_CPU_job");
                recoveredJob = target.iv$getState().jobs.getFirst(); expectedRestoredJob = frozenJob.copy();
                expectedRestoredJob.putUUID("cloudMachine", targetMachine);
                require(recoveredJob.save().equals(expectedRestoredJob),
                        "restore_preserves_exact_job_recipe_cycle_material_buckets_progress_and_paid_debt_except_target_machine_assignment");
                var recovery = recovery();
                require(recovery.status().equals("RESTORED") && target.cloudNodeId().equals(recovery.restoredTo())
                                && store().node(frozenNode).ivState().equals(frozenState),
                        "authority_records_one_restored_destination_and_retains_exact_frozen_source_snapshot");
                rejectReplay("busy_target");
                action(target, "resume", new CompoundTag());
                require(target.iv$getState().accepting && target.cloudMayUseNetwork(), "recovered_target_resumes_through_authorized_production_UI");
                evidence.put("restoredJob", recoveredJob.save().toString()); stage = 4;
            }
            case 4 -> {
                assertFrozenEnergy(); recoveredJob.validateAccounting();
                guard(!recoveredJob.cancelled && !recoveredJob.halted, "recovered_job_was_cancelled_or_halted_" + recoveredJob.error);
                guard(stock(INPUT) == 0 && stock(OUTPUT) <= 1, "recovery_returned_committed_input_or_duplicated_physical_output");
                guard(target.iv$getState().jobs.size() <= 1
                                && target.iv$getState().jobs.stream().allMatch(job -> job == recoveredJob), "recovery_created_an_additional_job");
                if (recoveredJob.active()) verifyNaturalPaidProgress();
                if (cpu.isBusy() || ((CraftingCPUCluster)cpu).craftingLogic.getLastLink() != null
                        || stock(OUTPUT) != 1 || !target.iv$getState().jobs.isEmpty()) return false;
                finishPaidWork(); cleanupAndRebuildSource(); stage = 5;
            }
            case 5 -> {
                assertFinishedEnergy();
                if (!sourceController.isFormed() || !replacement.cloudReady(true)) return false;
                require(IvBuffers.controller(replacement) == sourceController && sourceController.getParts().contains(replacement)
                                && replacement.getGrid() == acceptedGrid && !replacement.cloudNodeId().equals(frozenNode)
                                && replacement.iv$getState().jobs.isEmpty()
                                && !replacement.iv$getState().identity.equals(target.iv$getState().identity),
                        "source_cloud_part_rebuilds_at_original_position_with_natural_formation_and_a_new_empty_task_identity");
                action(replacement, "resume", new CompoundTag()); action(target, "resume", new CompoundTag());
                require(replacement.iv$getState().accepting && target.iv$getState().accepting,
                        "both_real_endpoints_resume_admission_before_existing_long_order_restart_scenario");
                since = ticks; stage = 6;
            }
            case 6 -> {
                assertFinishedEnergy();
                if (ticks - since < 20 || pattern(replacement) != null || pattern(target) != null || providerCount() != 0) return false;
                require(stock(INPUT) == 0 && stock(OUTPUT) == 0 && replacement.iv$getState().jobs.isEmpty()
                                && target.iv$getState().jobs.isEmpty() && !cpu.isBusy()
                                && ((CraftingCPUCluster)cpu).craftingLogic.getLastLink() == null
                                && replacement.cloudReady(true) && target.cloudReady(true),
                        "settled_fixture_has_no_delayed_duplicate_output_or_jobs_and_is_ready_for_unchanged_restart_boundary");
                evidence.put("replacementSourceNode", replacement.cloudNodeId().toString()); writeSnapshot("PASS", ""); return true;
            }
            default -> throw new AssertionError("Unknown frozen CPU recovery stage " + stage);
        }
        return false;
    }

    private static void freezeSource() {
        frozenJob = sourceJob.save(); frozenState = source.iv$getState().save(); frozenState.putBoolean("accepting", false);
        totalEnergy = sourceJob.energyTotal; frozenEnergyLeft = sourceJob.energyLeft; sourceFrozen = energy(sourceHatches);
        require(BigInteger.valueOf(Math.subtractExact(sourceInitial, sourceFrozen)).equals(totalEnergy.subtract(frozenEnergyLeft))
                        && energy(targetHatches) == targetInitial, "source_physical_EU_debit_equals_exact_paid_prefix_and_empty_target_spends_zero");
        require(level.setBlockAndUpdate(sourcePos, Blocks.AIR.defaultBlockState()) && MetaMachine.getMachine(level, sourcePos) == null,
                "actual_setBlock_removal_runs_production_source_machine_lifecycle");
        require(recovery().status().equals("FROZEN") && store().node(frozenNode).detached()
                        && store().node(frozenNode).ivState().equals(frozenState)
                        && store().library(main.libraryId()).entry(entryId).lease(frozenNode) == null
                        && sourceJob.save().equals(frozenJob),
                "real_block_removal_freezes_exact_started_paid_job_and_releases_only_source_lease");
        evidence.put("frozenNode", frozenNode.toString()); evidence.put("frozenJob", frozenJob.toString());
        evidence.put("sourcePaidEU", Math.subtractExact(sourceInitial, sourceFrozen));
        evidence.put("nativeDuration", sourceJob.duration); evidence.put("nativeEUt", sourceJob.eut);
        evidence.put("energyTotal", totalEnergy.toString()); evidence.put("frozenEnergyLeft", frozenEnergyLeft.toString());
    }

    private static void verifyNaturalPaidProgress() {
        var now = recoveredJob.save(); now.putInt("elapsed", frozenJob.getInt("elapsed")); now.putString("energyLeft", frozenEnergyLeft.toString());
        guard(now.equals(expectedRestoredJob), "natural_active_ticks_changed_committed_recipe_cycle_material_or_operation_identity");
        BigInteger paid = frozenEnergyLeft.subtract(recoveredJob.energyLeft);
        guard(recoveredJob.elapsed >= frozenJob.getInt("elapsed") && paid.signum() >= 0
                        && paid.equals(BigInteger.valueOf(recoveredJob.eut).multiply(BigInteger.valueOf(recoveredJob.elapsed - frozenJob.getInt("elapsed"))))
                        && paid.equals(BigInteger.valueOf(Math.subtractExact(targetAtRestore, energy(targetHatches)))),
                "natural_target_ticks_do_not_match_preserved_paid_debt_and_actual_physical_EU_debit");
    }

    private static void finishPaidWork() {
        // A standalone link has no nexus: markDone() leaves isDone() false; finishJob clears the actual CPU job/link.
        require(sawBusy && !link.isCanceled() && link.isStandalone() && craftingId.equals(link.getCraftingID()) && !cpu.isBusy()
                        && ((CraftingCPUCluster)cpu).craftingLogic.getLastLink() == null,
                "same_original_real_CPU_job_naturally_completes_and_clears_standalone_link_after_cross_machine_frozen_recovery");
        require(recoveredJob.id.equals(sourceJob.id) && recoveredJob.done() && !recoveredJob.cancelled && !recoveredJob.halted
                        && recoveredJob.totalOperations == 1 && recoveredJob.completedOperations == 1 && recoveredJob.deliveredOperations == 1
                        && recoveredJob.remaining == 0 && recoveredJob.parallel == 0 && recoveredJob.pendingOperations == 0
                        && recoveredJob.cancelledOperations == 0 && recoveredJob.refunds.isEmpty()
                        && recoveredJob.cycle.equals(frozenJob.getString("cycle")) && recoveredJob.energyTotal.equals(totalEnergy)
                        && recoveredJob.energyLeft.signum() == 0 && recoveredJob.elapsed == frozenJob.getInt("duration")
                        && stock(INPUT) == 0 && stock(OUTPUT) == 1,
                "same_job_and_native_cycle_deliver_exactly_one_output_with_all_operations_and_paid_energy_settled");
        targetFinal = energy(targetHatches);
        require(BigInteger.valueOf(Math.subtractExact(targetAtRestore, targetFinal)).equals(frozenEnergyLeft)
                        && BigInteger.valueOf(Math.addExact(Math.subtractExact(sourceInitial, sourceFrozen), Math.subtractExact(targetInitial, targetFinal))).equals(totalEnergy),
                "source_paid_prefix_plus_target_remaining_physical_EU_debit_equals_one_native_batch_exactly_once");
        require(store().node(frozenNode).ivState().equals(frozenState), "completion_does_not_resume_or_mutate_the_detached_source_ledger");
        rejectReplay("empty_completed_target");
        require(target.iv$getState().jobs.isEmpty() && stock(OUTPUT) == 1, "completed_empty_target_cannot_recover_the_same_source_a_second_time");
        evidence.put("targetPaidEU", Math.subtractExact(targetInitial, targetFinal)); evidence.put("completedJob", recoveredJob.save().toString());
        evidence.put("sameOriginalStandaloneCpuNaturallyCompleted", true);
        evidence.put("originalStandaloneLinkIsDone", link.isDone());
        evidence.put("cpuCurrentJobLinkCleared", ((CraftingCPUCluster)cpu).craftingLogic.getLastLink() == null);
        evidence.put("completionSemantics", "Standalone completion is the original CPU becoming idle and clearing its current job/link while the original link remains uncanceled with the same crafting ID; isDone is recorded but does not apply without a nexus.");
        evidence.put("outputInRealME", stock(OUTPUT));
    }

    private static void rejectReplay(String phase) {
        var jobs = target.iv$getState().jobs.stream().map(IvJob::save).toList(); long before = energy(targetHatches);
        var payload = new CompoundTag(); payload.putString("recoveryId", frozenNode.toString()); action(target, "recover", payload);
        require(target.iv$getState().jobs.stream().map(IvJob::save).toList().equals(jobs)
                        && recovery().status().equals("RESTORED") && target.cloudNodeId().equals(recovery().restoredTo())
                        && store().node(frozenNode).ivState().equals(frozenState) && energy(targetHatches) == before,
                phase + "_fresh_revision_UI_replay_cannot_transfer_jobs_materials_or_paid_energy_again");
    }

    private static void cleanupAndRebuildSource() {
        var operation = UUID.randomUUID(); var store = store();
        var returned = store.extractFromCore(main.libraryId(), player.getUUID(), entryId, operation);
        require(ItemStack.matches(original, returned) && returned.getCount() == 1, "cleanup_returns_exactly_the_single_physical_audit_original");
        store.acknowledge(main.libraryId(), player.getUUID(), operation); store.checkpointLibrary(main.libraryId());
        require(store.library(main.libraryId()).entry(entryId).status() == CloudLedger.Status.EXPORTED
                        && store.library(main.libraryId()).entry(entryId).leases().isEmpty()
                        && store.extractFromCore(main.libraryId(), player.getUUID(), entryId, operation).isEmpty(),
                "cleanup_revokes_temporary_routes_and_original_export_replay_cannot_duplicate_original");
        require(target.getGrid().getStorageService().getInventory().extract(OUTPUT, 2, Actionable.MODULATE, IActionSource.ofPlayer(player)) == 1
                        && stock(OUTPUT) == 0, "cleanup_extracts_exactly_one_completed_output_from_real_ME");
        require(level.isEmptyBlock(sourcePos) && level.setBlockAndUpdate(sourcePos, sourceBlock), "cleanup_replaces_source_cloud_block_at_its_original_physical_position");
        replacement = (CloudPatternBufferMachine)MetaMachine.getMachine(level, sourcePos);
        require(replacement != null && replacement != source, "cleanup_constructs_a_real_new_source_cloud_part");
        replacement.setFrontFacing(sourceFacing); replacement.onMachinePlaced(player, ItemStack.EMPTY);
    }

    private static void assertFrozenEnergy() {
        guard(energy(sourceHatches) == sourceFrozen, "detached_source_spent_or_regained_physical_EU");
        guard(sourceJob.save().equals(frozenJob), "removed_source_job_kept_advancing_after_its_exact_snapshot_was_frozen");
        if (stage == 3) guard(energy(targetHatches) == targetInitial, "empty_target_spent_or_regained_EU_before_restore");
    }
    private static void assertFinishedEnergy() {
        guard(energy(sourceHatches) == sourceFrozen && energy(targetHatches) == targetFinal, "settled_physical_EU_changed_after_exact_completion");
    }
    private static List<PhysicalHatch> hatches(WorkableElectricMultiblockMachine machine) {
        var result = new ArrayList<PhysicalHatch>();
        for (var part : machine.getParts()) if (part instanceof EnergyHatchPartMachine hatch) {
            require(hatch.energyContainer.getInputVoltage() > 0 && hatch.energyContainer.getEnergyCapacity() > 0,
                    "formed_fixture_part_is_a_real_input_energy_hatch_" + hatch.getPos());
            result.add(new PhysicalHatch(hatch, hatch.energyContainer));
        }
        require(!result.isEmpty() && energy(result) == machine.getEnergyContainer().getEnergyStored(),
                "actual_hatch_containers_account_for_entire_controller_energy_reserve_" + machine.getPos());
        return List.copyOf(result);
    }
    private static void fillOnce(List<PhysicalHatch> hatches) {
        for (var item : hatches) item.container.addEnergy(item.container.getEnergyCapacity());
    }
    private static long energy(List<PhysicalHatch> hatches) {
        long total = 0;
        for (var item : hatches) {
            guard(MetaMachine.getMachine(level, item.hatch.getPos()) == item.hatch && item.hatch.energyContainer == item.container,
                    "physical_energy_hatch_or_its_stable_container_was_replaced");
            total = Math.addExact(total, item.container.getEnergyStored());
        }
        return total;
    }
    private static List<Map<String, Object>> hatchDescriptions(List<PhysicalHatch> hatches) {
        return hatches.stream().map(item -> Map.<String, Object>of("position", item.hatch.getPos().toShortString(),
                "definition", item.hatch.getDefinition().getId().toString(), "capacity", item.container.getEnergyCapacity(),
                "inputVoltage", item.container.getInputVoltage(), "inputAmperage", item.container.getInputAmperage())).toList();
    }
    private static void action(CloudPatternBufferMachine child, String name, CompoundTag payload) {
        var pos = child.getPos(); player.setPos(pos.getX() + .5, pos.getY() + .5, pos.getZ() - 1);
        player.containerMenu = new ModularUIContainer(child.createUI(player), 79);
        payload.putLong("revision", child.cloudUiSnapshot(player, "", "", 0, "").getLong("revision"));
        child.cloudUiAction(player, name, payload);
    }
    private static CloudPatternStore store() { return CloudPatternStore.get(level); }
    private static CloudPatternStore.Recovery recovery() {
        return store().library(main.libraryId()).recoveries().stream().filter(row -> row.nodeId().equals(frozenNode)).findFirst().orElseThrow();
    }
    private static CraftingService crafting() { return (CraftingService)target.getGrid().getCraftingService(); }
    private static long stock(AEKey key) { return target.getGrid().getStorageService().getInventory().getAvailableStacks().get(key); }
    private static IPatternDetails pattern(CloudPatternBufferMachine child) {
        for (var candidate : child.getAvailablePatterns()) {
            var tag = candidate.getDefinition().toStack().getTag();
            if (tag != null && entryId.equals(tag.getCompound(IvBuffers.ROUTE_KEY).getUUID("entry"))) return candidate;
        }
        return null;
    }
    private static int providerCount() {
        int count = 0;
        if (pattern != null) for (var ignored : crafting().getProviders(pattern)) count++;
        return count;
    }
    private static boolean sourceIsIndexed() {
        for (var provider : crafting().getProviders(pattern)) if (provider == source) return true;
        return false;
    }
    private static void guard(boolean condition, String message) {
        if (!condition) throw new AssertionError("frozen_cpu_recovery:" + message);
    }
    private static void require(boolean condition, String message) { guard(condition, message); checks.add("frozen_cpu_recovery:" + message); }
    private static String diagnostics() {
        return "sourceFormed=" + sourceController.isFormed() + " target=" + target.cloudReadyFailure(true)
                + " targetJobs=" + target.iv$getState().save() + " cpu=" + (cpu == null ? null : cpu.getJobStatus())
                + " input=" + stock(INPUT) + " output=" + stock(OUTPUT);
    }
    private static void writeSnapshot(String status, String error) throws Exception {
        var report = new LinkedHashMap<String, Object>(); report.put("status", status); report.put("error", error);
        report.put("stage", stage); report.put("ticks", ticks); report.put("evidence", evidence);
        report.put("checks", checks.stream().filter(check -> check.startsWith("frozen_cpu_recovery:")).toList());
        report.put("boundary", "One real legacy AE plan and original standalone 64K CPU job/link, real source block removal and production UI recovery on another empty compatible LCR, natural ticks and stable physical hatch EU measurements. Natural completion requires CPU idle/current job-link cleared together with exact output, material, order and energy settlement; standalone link isDone is recorded only. No jobs, leases, WAL, completion or paid-energy state is injected. The independent-JVM long-order restart scenario remains separate.");
        Files.writeString(directory.resolve("cloud-compat-frozen-cpu-recovery.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }
}
