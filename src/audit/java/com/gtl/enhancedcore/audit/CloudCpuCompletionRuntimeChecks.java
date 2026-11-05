package com.gtl.enhancedcore.audit;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.me.service.CraftingService;
import com.google.gson.GsonBuilder;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder;
import com.gtl.enhancedcore.common.cloud.CloudPatternBufferMachine;
import com.gtl.enhancedcore.common.cloud.CloudPatternLibraryMachine;
import com.gtl.enhancedcore.common.cloud.store.CloudLedger;
import com.gtl.enhancedcore.common.cloud.store.CloudMachineIdentity;
import com.gtl.enhancedcore.common.cloud.store.CloudPatternStore;
import com.gtl.enhancedcore.common.recipe.iv.IvBuffers;
import com.gtl.enhancedcore.common.recipe.iv.IvJob;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Future;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;
import org.gtlcore.gtlcore.integration.ae2.pattern.PatternQuickUploadMetadata;

/** Real AE calculation, physical CPU execution and natural machine ticks in the shared formed world. */
public final class CloudCpuCompletionRuntimeChecks {
    private static final long REPETITIONS = 33;
    private static ServerLevel level;
    private static CloudPatternLibraryMachine main;
    private static List<CloudPatternBufferMachine> children;
    private static List<WorkableElectricMultiblockMachine> machines;
    private static ServerPlayer player;
    private static List<String> checks;
    private static Path directory;
    private static final List<Map<String, Object>> rounds = new ArrayList<>();
    private static final Map<UUID, ObservedJob> jobs = new LinkedHashMap<>();
    private static final Map<String, Map<String, Object>> batches = new LinkedHashMap<>();
    private static final List<Long> initialEnergy = new ArrayList<>();
    private static UUID entryId;
    private static ItemStack original;
    private static AEKey input, output;
    private static IPatternDetails sharedPattern;
    private static Future<ICraftingPlan> calculation;
    private static ICraftingCPU cpu;
    private static ICraftingLink link;
    private static long scale, quantity;
    private static int round, stage, ticks, since;
    private static boolean sawBusy;
    private record ObservedJob(int endpoint, IvJob job) {}
    private CloudCpuCompletionRuntimeChecks() {}

    /** Called during fixture construction, before the server creates any recipe lookup cache. */
    public static void prepareRecipes(ServerLevel world, WorkableElectricMultiblockMachine controller) {
        var recipes = new ArrayList<net.minecraft.world.item.crafting.Recipe<?>>(world.getRecipeManager().getRecipes());
        for (int index = 0; index < 2; index++) {
            GTRecipe recipe = GTRecipeBuilder.of(new ResourceLocation("gtl_enhancedcore", "audit_cloud_cpu_complete_" + index), controller.getRecipeType())
                    .inputItems(index == 0 ? Items.BEETROOT : Items.CARROT)
                    .outputItems(index == 0 ? Items.EMERALD : Items.IRON_INGOT).duration(80).EUt(8).buildRawRecipe();
            recipes.add(recipe); controller.getRecipeType().getLookup().addRecipe(recipe);
        }
        world.getRecipeManager().replaceRecipes(recipes);
    }

    public static void begin(ServerLevel world, CloudPatternLibraryMachine core, List<CloudPatternBufferMachine> buffers,
                             ServerPlayer actor, List<String> results) throws Exception {
        level = world; main = core; children = List.copyOf(buffers); player = actor; checks = results;
        directory = world.getServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getParent().getParent();
        machines = children.stream().map(IvBuffers::controller).map(WorkableElectricMultiblockMachine.class::cast).toList();
        round = 0; stage = 0; ticks = 0; rounds.clear();
        require(children.size() == 2 && machines.get(0) != machines.get(1)
                        && main.isFormed() && machines.stream().allMatch(WorkableElectricMultiblockMachine::isFormed)
                        && children.stream().allMatch(child -> child.cloudReady(true)),
                "two_distinct_physically_formed_LCRs_and_real_cloud_endpoints_are_ready");
        require(children.stream().allMatch(child -> child.iv$getState().jobs.isEmpty()), "fixture_starts_with_empty_order_ledgers");
        require(!org.gtlcore.gtlcore.integration.ae2.graph.CraftingEngineRouter.useGraph(),
                "actual_legacy_AE_planner_and_CPU_engine_are_used");
        require(crafting().getCpus().stream().anyMatch(candidate -> candidate instanceof appeng.me.cluster.implementations.CraftingCPUCluster
                        && !candidate.isBusy() && candidate.getAvailableStorage() >= 65536 && candidate.getCoProcessors() > 0),
                "real_physically_formed_64K_CPU_and_coprocessor_are_available");
        beginRound();
    }

    /** The fixture must not refill physical GT energy while this helper measures its debit. */
    public static boolean wantOuterPower() { return false; }

    private static void beginRound() {
        scale = round == 0 ? 1 : 8; quantity = Math.multiplyExact(REPETITIONS, scale);
        Item ingredient = round == 0 ? Items.BEETROOT : Items.CARROT;
        Item result = round == 0 ? Items.EMERALD : Items.IRON_INGOT;
        input = AEItemKey.of(ingredient); output = AEItemKey.of(result);
        require(stock(input) == 0 && stock(output) == 0, label("physical_ME_starts_without_this_rounds_input_or_output"));
        require(children.stream().allMatch(child -> child.iv$getState().jobs.isEmpty()), label("previous_orders_are_fully_retired"));
        initialEnergy.clear(); jobs.clear(); batches.clear(); cpu = null; link = null; sharedPattern = null; sawBusy = false;
        for (var machine : machines) {
            var energy = machine.getEnergyContainer();
            require(energy != null && energy.getEnergyCapacity() > 0, label("real_LCR_has_physical_energy_container_" + machine.getPos()));
            energy.addEnergy(energy.getEnergyCapacity()); initialEnergy.add(energy.getEnergyStored());
        }
        original = PatternDetailsHelper.encodeProcessingPattern(new GenericStack[]{new GenericStack(input, scale)},
                new GenericStack[]{new GenericStack(output, scale)});
        PatternQuickUploadMetadata.writeRecipeTypeId(original, children.getFirst().cloudMode());
        original.getOrCreateTag().putString("cloudCpuCompletionAudit", "scale_" + scale);
        var physical = original.copy(); UUID operation = UUID.randomUUID(); var store = CloudPatternStore.get(level);
        entryId = store.upload(main.libraryId(), player.getUUID(), physical, new CompoundTag(), operation).id();
        require(physical.isEmpty(), label("central_upload_consumes_exactly_one_physical_original"));
        store.acknowledge(main.libraryId(), player.getUUID(), operation); store.checkpointLibrary(main.libraryId());
        since = ticks; stage = 0;
    }

    public static boolean step() throws Exception {
        try { return stepInternal(); }
        catch (Exception | Error failure) { writeSnapshot("FAIL", failure.toString()); throw failure; }
    }

    private static boolean stepInternal() throws Exception {
        if (++ticks > 2200) throw new AssertionError("CPU completion timeout round=" + round + " stage=" + stage + " " + diagnostics());
        observe();
        if (ticks % 20 == 0) writeSnapshot("RUNNING", "");
        switch (stage) {
            case 0 -> {
                var entry = CloudPatternStore.get(level).library(main.libraryId()).entry(entryId);
                if (entry.leases().size() != 2) return false;
                sharedPattern = pattern(children.getFirst());
                if (sharedPattern == null || children.stream().anyMatch(child -> !child.getAvailablePatterns().contains(sharedPattern))) return false;
                if (providerCount() != 2 || !crafting().isCraftable(output)) return false;
                require(entry.leases().stream().allMatch(lease -> children.stream().anyMatch(child -> child.cloudNodeId().equals(lease.nodeId()))),
                        label("single_original_has_exactly_two_live_endpoint_leases"));
                require(providerCount() == 2, label("actual_CraftingService_indexes_two_providers_for_one_canonical_pattern"));
                require(sharedPattern.getOutputs().length == 1 && sharedPattern.getOutputs()[0].amount() == scale
                                && sharedPattern.getInputs().length == 1 && sharedPattern.getInputs()[0].getMultiplier() == scale,
                        label("published_AE_pattern_retains_exact_physical_input_and_output_scale"));
                long inserted = children.getFirst().getGrid().getStorageService().getInventory().insert(input, quantity,
                        Actionable.MODULATE, IActionSource.ofPlayer(player));
                require(inserted == quantity && stock(input) == quantity, label("exact_physical_input_is_inserted_into_real_AE_drive"));
                calculation = crafting().beginCraftingCalculation(level, () -> IActionSource.ofPlayer(player), output, quantity,
                        CalculationStrategy.REPORT_MISSING_ITEMS);
                since = ticks; stage = 1;
            }
            case 1 -> {
                if (!calculation.isDone()) return false;
                var plan = calculation.get();
                require(!plan.simulation() && plan.missingItems().isEmpty() && plan.patternTimes().size() == 1
                                && plan.patternTimes().getOrDefault(sharedPattern, 0L) == REPETITIONS
                                && plan.finalOutput().what().equals(output) && plan.finalOutput().amount() == quantity,
                        label("real_AE_calculation_plans_33_whole_pattern_repetitions_and_exact_final_output"));
                cpu = crafting().getCpus().stream().filter(candidate -> candidate instanceof appeng.me.cluster.implementations.CraftingCPUCluster
                        && !candidate.isBusy() && candidate.getAvailableStorage() >= plan.bytes()).findFirst().orElseThrow();
                var submitted = crafting().submitJob(plan, null, cpu, false, IActionSource.ofPlayer(player));
                require(submitted.successful(), label("real_AE_CPU_accepts_calculated_plan_" + submitted.errorCode()));
                link = submitted.link(); sawBusy = cpu.isBusy(); since = ticks; stage = 2;
            }
            case 2 -> {
                sawBusy |= cpu.isBusy();
                if (link != null && link.isCanceled()) throw new AssertionError(label("real_CPU_link_was_cancelled"));
                if (cpu.isBusy() || stock(output) != quantity || children.stream().anyMatch(child -> !child.iv$getState().jobs.isEmpty())) return false;
                finishRound();
                since = ticks; stage = 3;
            }
            case 3 -> {
                if (children.stream().anyMatch(child -> pattern(child) != null) || providerCount() != 0) return false;
                require(children.stream().allMatch(child -> child.iv$getState().jobs.isEmpty()) && stock(input) == 0 && stock(output) == 0,
                        label("temporary_original_routes_outputs_and_order_ledgers_are_clean_before_next_scenario"));
                if (++round < 2) { beginRound(); return false; }
                writeSnapshot("PASS", ""); return true;
            }
            default -> throw new AssertionError("Unknown CPU completion stage " + stage);
        }
        return false;
    }

    private static void observe() {
        if (entryId == null) return;
        for (int index = 0; index < children.size(); index++) for (var job : children.get(index).iv$getState().jobs) {
            if (!entryId.equals(job.cloudPattern)) throw new AssertionError(label("unrelated_job_entered_isolated_completion_fixture"));
            job.validateAccounting();
            if (job.halted || job.cancelled) throw new AssertionError(label("job_halted_or_cancelled_" + job.error));
            UUID executor = ((CloudMachineIdentity)machines.get(index)).cloud$machineInstance(false);
            if (!executor.equals(job.cloudMachine) || job.cloudPatternOperations != scale || job.totalOperations % scale != 0)
                throw new AssertionError(label("job_ownership_or_frozen_whole_pattern_scale_changed"));
            jobs.putIfAbsent(job.id, new ObservedJob(index, job));
            if (!job.cycle.isEmpty() && job.energyTotal.signum() > 0) {
                String batchId = job.id + ":" + job.cycle;
                if (!batches.containsKey(batchId)) {
                    if (!job.energyTotal.equals(BigInteger.valueOf(job.eut).multiply(BigInteger.valueOf(job.duration))))
                        throw new AssertionError(label("native_batch_energy_total_disagrees_with_committed_tick_payments"));
                    var batch = new LinkedHashMap<String, Object>();
                    batch.put("job", job.id.toString()); batch.put("endpoint", index); batch.put("cycle", job.cycle);
                    batch.put("eut", job.eut); batch.put("duration", job.duration); batch.put("energyTotal", job.energyTotal.toString());
                    batch.put("firstObservedElapsed", job.elapsed); batch.put("firstObservedEnergyLeft", job.energyLeft.toString());
                    batches.put(batchId, batch);
                }
            }
        }
    }

    private static void finishRound() throws Exception {
        require(sawBusy && !cpu.isBusy() && (link == null || link.isDone()), label("real_CPU_was_busy_and_naturally_completed_its_job"));
        long accepted = 0, delivered = 0; var byEndpoint = new long[children.size()];
        for (var observed : jobs.values()) {
            var job = observed.job; job.validateAccounting();
            require(job.done() && !job.cancelled && !job.halted && job.remaining == 0 && job.parallel == 0
                            && job.totalOperations == job.completedOperations && job.totalOperations == job.deliveredOperations
                            && job.pendingOperations == 0 && job.cancelledOperations == 0,
                    label("accepted_order_retires_only_after_all_operations_are_completed_and_delivered_" + job.id));
            accepted = Math.addExact(accepted, job.totalOperations); delivered = Math.addExact(delivered, job.deliveredOperations);
            byEndpoint[observed.endpoint] = Math.addExact(byEndpoint[observed.endpoint], job.totalOperations);
        }
        require(accepted == quantity && delivered == quantity && byEndpoint[0] > 0 && byEndpoint[1] > 0,
                label("both_LCRs_accept_nonzero_whole_pattern_shares_and_total_GT_operations_equal_33_times_scale"));
        require(byEndpoint[0] % scale == 0 && byEndpoint[1] % scale == 0,
                label("both_endpoint_shares_preserve_whole_AE_pattern_repetitions"));
        require(stock(input) == 0 && stock(output) == quantity,
                label("physical_input_is_consumed_once_and_exact_full_output_is_present_in_real_ME_storage"));
        BigInteger paid = BigInteger.ZERO; var physicalDebits = new ArrayList<Long>();
        for (int index = 0; index < machines.size(); index++) {
            long debit = Math.subtractExact(initialEnergy.get(index), machines.get(index).getEnergyContainer().getEnergyStored());
            final int endpoint = index;
            BigInteger ledger = batches.values().stream().filter(batch -> ((Integer)batch.get("endpoint")) == endpoint)
                    .map(batch -> new BigInteger((String)batch.get("energyTotal"))).reduce(BigInteger.ZERO, BigInteger::add);
            require(debit > 0 && ledger.equals(BigInteger.valueOf(debit)),
                    label("endpoint_" + index + "_physical_EU_debit_exactly_equals_every_committed_native_batch_once"));
            paid = paid.add(ledger); physicalDebits.add(debit);
        }
        var report = new LinkedHashMap<String, Object>();
        report.put("scale", scale); report.put("patternRepetitions", REPETITIONS); report.put("physicalInput", quantity);
        report.put("GToperationsAccepted", accepted); report.put("GToperationsDelivered", delivered);
        report.put("endpointOperations", List.of(byEndpoint[0], byEndpoint[1])); report.put("outputInRealME", stock(output));
        report.put("physicalEUdebits", physicalDebits); report.put("totalPaidEU", paid.toString());
        report.put("nativeBatches", new ArrayList<>(batches.values())); report.put("CPUclass", cpu.getClass().getName());
        report.put("CPUidle", !cpu.isBusy()); report.put("linkDone", link == null ? null : link.isDone());
        rounds.add(report);
        UUID operation = UUID.randomUUID(); var store = CloudPatternStore.get(level);
        var returned = store.extractFromCore(main.libraryId(), player.getUUID(), entryId, operation);
        require(ItemStack.matches(original, returned) && returned.getCount() == 1,
                label("cleanup_exports_exactly_the_single_original_after_completed_work"));
        store.acknowledge(main.libraryId(), player.getUUID(), operation); store.checkpointLibrary(main.libraryId());
        require(store.library(main.libraryId()).entry(entryId).status() == CloudLedger.Status.EXPORTED
                        && store.library(main.libraryId()).entry(entryId).leases().isEmpty(),
                label("cleanup_revokes_both_endpoint_leases_at_authority"));
        require(store.extractFromCore(main.libraryId(), player.getUUID(), entryId, operation).isEmpty(),
                label("cleanup_export_replay_cannot_duplicate_the_physical_original"));
        long extracted = children.getFirst().getGrid().getStorageService().getInventory().extract(output, quantity,
                Actionable.MODULATE, IActionSource.ofPlayer(player));
        require(extracted == quantity && stock(output) == 0, label("cleanup_removes_exact_completed_output_from_real_ME_storage"));
        writeSnapshot("RUNNING", "");
    }

    private static IPatternDetails pattern(CloudPatternBufferMachine child) {
        for (var candidate : child.getAvailablePatterns()) {
            var stack = candidate.getDefinition().toStack();
            var tag = stack.getTag();
            if (tag != null && tag.getCompound(IvBuffers.ROUTE_KEY).hasUUID("entry")
                    && entryId.equals(tag.getCompound(IvBuffers.ROUTE_KEY).getUUID("entry"))) return candidate;
        }
        return null;
    }
    private static CraftingService crafting() { return (CraftingService)children.getFirst().getGrid().getCraftingService(); }
    private static int providerCount() {
        if (sharedPattern == null) return 0;
        int count = 0;
        for (var provider : crafting().getProviders(sharedPattern)) if (children.contains(provider)) count++;
        return count;
    }
    private static long stock(AEKey key) { return children.getFirst().getGrid().getStorageService().getInventory().getAvailableStacks().get(key); }
    private static String label(String text) { return "scale_" + scale + ":" + text; }
    private static void require(boolean condition, String text) {
        if (!condition) throw new AssertionError("cpu_completion:" + text);
        checks.add("cpu_completion:" + text);
    }
    private static String diagnostics() {
        return children.stream().map(child -> child.cloudReadyFailure(true) + ":" + child.iv$getState().save()).toList()
                + " cpu=" + (cpu == null ? null : cpu.getJobStatus()) + " input=" + stock(input) + " output=" + stock(output);
    }
    private static void writeSnapshot(String status, String error) throws Exception {
        var report = new LinkedHashMap<String, Object>();
        report.put("status", status); report.put("error", error); report.put("ticks", ticks); report.put("round", round);
        report.put("stage", stage); report.put("scale", scale); report.put("rounds", rounds);
        report.put("live", diagnostics()); report.put("observedJobs", jobs.values().stream().map(observed -> observed.job.save().toString()).toList());
        report.put("observedNativeBatches", new ArrayList<>(batches.values()));
        report.put("boundary", "Uses the shared real formed two-LCR/64K-CPU fixture, actual RecipeManager recipes and physical ME cells; no manual machine or recipe-logic ticks, no order counter writes and no external GT energy refill during measurement.");
        Files.writeString(directory.resolve("cloud-compat-cpu-completion.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }
}
