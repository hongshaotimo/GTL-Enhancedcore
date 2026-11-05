package com.gtl.enhancedcore.audit;

import appeng.api.networking.*;
import appeng.api.crafting.IPatternDetails;
import appeng.api.config.Actionable;
import appeng.api.networking.crafting.*;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import appeng.api.util.AEColor;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.blockentity.networking.CableBusBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEParts;
import appeng.me.service.CraftingService;
import com.gtl.enhancedcore.common.cloud.*;
import com.gtl.enhancedcore.common.cloud.store.CloudMachineIdentity;
import com.gtl.enhancedcore.common.recipe.iv.IvBuffers;
import java.util.*;
import java.util.concurrent.Future;
import java.nio.file.Files;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Real cable destruction and AE connection split/merge; no topology fields are edited. */
public final class CloudTopologyRuntimeChecks {
    private static ServerLevel level;
    private static CloudPatternLibraryMachine main;
    private static CloudPatternBufferMachine child;
    private static CloudPatternBufferMachine peer;
    private static ServerPlayer player;
    private static List<String> checks;
    private static BlockPos cablePos, providerPos;
    private static PatternProviderBlockEntity provider;
    private static IGridNode cableNode, endpoint;
    private static final List<IGridNode> severedPeers = new ArrayList<>();
    private static AEKey output;
    private static UUID nodeId, libraryId;
    private static UUID entryId;
    private static IPatternDetails sharedPattern;
    private static Future<ICraftingPlan> calculation;
    private static int stage, ticks, since, branchCycles, endpointCycles;
    private static Map<UUID, UUID> leaseIds;
    private static net.minecraft.world.item.ItemStack exported = net.minecraft.world.item.ItemStack.EMPTY;
    private CloudTopologyRuntimeChecks() {}

    public static void begin(ServerLevel world, CloudPatternLibraryMachine core, CloudPatternBufferMachine buffer,
                             CloudPatternBufferMachine other, UUID original, ServerPlayer actor, List<String> results) {
        level = world; main = core; child = buffer; peer = other; entryId = original; player = actor; checks = results;
        ticks = 0; stage = 0; since = 0; branchCycles = 0; endpointCycles = 0; severedPeers.clear();
        exported = net.minecraft.world.item.ItemStack.EMPTY;
        require(main.isFormed() && child.cloudReady(true) && !child.getAvailablePatterns().isEmpty(),
                "formed_main_actual_ready_endpoint_has_published_patterns");
        require(child.iv$getState().jobs.isEmpty(), "topology_fixture_starts_without_orders");
        nodeId = child.cloudNodeId(); libraryId = child.cloudLibraryId();
        var entry = child.cloudStore().library(libraryId).entry(entryId);
        leaseIds = Map.of(child.cloudNodeId(), entry.lease(child.cloudNodeId()).id(),
                peer.cloudNodeId(), entry.lease(peer.cloudNodeId()).id());
        sharedPattern = publishedPattern(child, entryId).orElseThrow(() ->
                new AssertionError("topology:exact_requested_original_has_no_published_canonical_pattern_" + entryId));
        output = sharedPattern.getOutputs()[0].what();
        checks.add("topology:canonical_shared_pattern_is_selected_by_exact_requested_original_identity");
        require(peer.getAvailablePatterns().contains(sharedPattern), "one_canonical_pattern_is_published_by_both_real_LCR_endpoints");
        endpoint = child.getMainNode().getNode();
        require(indexed(), "actual_CraftingService_contains_cloud_provider_before_topology_change");
        require(providerCount(sharedPattern) == 2, "actual_AE_same_pattern_provider_pool_contains_two_cloud_endpoints");
        cablePos = main.getPos().north(2); providerPos = cablePos.north();
        require(level.isEmptyBlock(cablePos) && level.isEmptyBlock(providerPos), "branch_positions_are_empty_fixture_air");
        placeCable();
        level.setBlockAndUpdate(providerPos, AEBlocks.PATTERN_PROVIDER.block().defaultBlockState());
        provider = (PatternProviderBlockEntity)level.getBlockEntity(providerPos);
        provider.getMainNode().setOwningPlayer(player);
    }

    private static void placeCable() {
        level.setBlockAndUpdate(cablePos, AEBlocks.CABLE_BUS.block().defaultBlockState());
        var host = (CableBusBlockEntity)level.getBlockEntity(cablePos);
        var cable = host.addPart(AEParts.SMART_CABLE.item(AEColor.TRANSPARENT), null, player);
        if (cable == null) throw new AssertionError("Could not add actual smart cable part");
    }
    static Optional<IPatternDetails> publishedPattern(CloudPatternBufferMachine buffer, UUID original) {
        return buffer.getAvailablePatterns().stream().filter(candidate -> {
            var tag = candidate.getDefinition().toStack().getTag();
            return tag != null && original.equals(tag.getCompound(IvBuffers.ROUTE_KEY).getUUID("entry"));
        }).findFirst();
    }
    private static boolean indexed() {
        var grid = child.getMainNode().getGrid();
        if (grid == null || !grid.getCraftingService().isCraftable(output)) return false;
        var crafting = (CraftingService)grid.getCraftingService();
        for (var pattern : crafting.getCraftingFor(output))
            for (var candidate : crafting.getProviders(pattern)) if (candidate == child) return true;
        return false;
    }
    private static int providerCount(IPatternDetails pattern) {
        int count = 0;
        for (var candidate : ((CraftingService)peer.getGrid().getCraftingService()).getProviders(pattern))
            if (candidate == child || candidate == peer) count++;
        return count;
    }
    private static void require(boolean value, String text) {
        if (!value) throw new AssertionError("topology:" + text);
        checks.add("topology:" + text);
    }
    private static void unchangedEndpoint() {
        require(nodeId.equals(child.cloudNodeId()) && libraryId.equals(child.cloudLibraryId())
                        && endpoint == child.getMainNode().getNode(),
                "same_cloud_authority_and_real_node_survive_without_controller_or_buffer_replacement");
        var entry = child.cloudStore().library(libraryId).entry(entryId);
        require(leaseIds.entrySet().stream().allMatch(expected -> entry.lease(expected.getKey()) != null
                        && expected.getValue().equals(entry.lease(expected.getKey()).id())),
                "same_two_durable_original_leases_survive_topology_refresh_without_reallocation");
    }
    public static boolean step() throws Exception {
        if (++ticks > 1600) throw new AssertionError("Topology timeout stage=" + stage + " ready="
                + child.cloudReadyFailure(true) + " main=" + main.serviceGrid() + " child=" + orders(child) + " peer=" + orders(peer));
        switch (stage) {
            case 0 -> {
                var host = (CableBusBlockEntity)level.getBlockEntity(cablePos);
                cableNode = host.getGridNode(null);
                if (cableNode == null || !cableNode.isActive() || !provider.getMainNode().isActive()) return false;
                require(cableNode.getGrid() == endpoint.getGrid() && provider.getMainNode().getGrid() == endpoint.getGrid(),
                        "actual_smart_cable_and_ordinary_pattern_provider_join_cloud_grid");
                level.removeBlock(cablePos, false); since = ticks; stage = 1;
            }
            case 1 -> {
                if (ticks - since < 25) return false;
                require(level.getBlockEntity(cablePos) == null && provider.getMainNode().getGrid() != endpoint.getGrid(),
                        "actual_smart_cable_block_removal_splits_ordinary_provider_branch");
                require(main.serviceGrid() == endpoint.getGrid() && child.cloudReady(true) && indexed(),
                        "cloud_CraftingService_provider_recovers_after_unrelated_branch_cable_is_mined");
                branchCycles++;
                unchangedEndpoint(); placeCable(); since = ticks; stage = 2;
            }
            case 2 -> {
                if (ticks - since < 25 || !provider.getMainNode().isActive()) return false;
                require(provider.getMainNode().getGrid() == endpoint.getGrid() && indexed(),
                        "actual_replaced_smart_cable_merges_provider_branch_and_keeps_cloud_craftability");
                if (branchCycles < 10) { level.removeBlock(cablePos, false); since = ticks; stage = 1; return false; }
                require(branchCycles == 10, "ten_actual_ordinary_provider_branch_cable_mining_replacement_cycles_keep_terminal_craftability");
                severedPeers.clear();
                for (var connection : List.copyOf(endpoint.getConnections())) {
                    severedPeers.add(connection.getOtherSide(endpoint)); connection.destroy();
                }
                require(!severedPeers.isEmpty(), "real_endpoint_connections_destroyed_to_split_cloud_from_storage");
                since = ticks; stage = 3;
            }
            case 3 -> {
                if (ticks - since < 25) return false;
                require(!child.getMainNode().isActive() && endpoint.getGrid() != main.serviceGrid()
                                && child.getAvailablePatterns().isEmpty(),
                        "actual_endpoint_partition_is_unpowered_and_cannot_publish_or_dispatch");
                require(peer.cloudReady(true) && providerCount(sharedPattern) == 1,
                        "splitting_one_real_cloud_node_withdraws_only_its_own_provider_route");
                unchangedEndpoint();
                for (var peer : severedPeers) GridHelper.createConnection(endpoint, peer);
                since = ticks; stage = 4;
            }
            case 4 -> {
                if (ticks - since < 25 || !child.cloudReady(true) || !indexed()) return false;
                require(main.serviceGrid() == endpoint.getGrid(), "real_grid_merge_restores_actual_library_binding");
                require(indexed(), "same_cached_cloud_patterns_republish_into_actual_CraftingService_after_split_merge");
                require(providerCount(sharedPattern) == 2, "actual_AE_provider_pool_restores_both_cloud_routes_after_merge");
                unchangedEndpoint();
                if (++endpointCycles < 10) {
                    severedPeers.clear();
                    for (var connection : List.copyOf(endpoint.getConnections())) {
                        severedPeers.add(connection.getOtherSide(endpoint)); connection.destroy();
                    }
                    since = ticks; stage = 3; return false;
                }
                require(endpointCycles == 10, "ten_actual_endpoint_split_merge_cycles_restore_isCraftable_craftingFor_and_two_provider_index_entries");
                CloudOriginContinuityRuntimeChecks.run(level, checks);
                var crafting = child.getGrid().getCraftingService();
                require(crafting.getCpus().stream().anyMatch(cpu -> cpu instanceof appeng.me.cluster.implementations.CraftingCPUCluster
                                && cpu.getAvailableStorage() >= 65536 && cpu.getCoProcessors() > 0),
                        "physically_formed_actual_64K_AE_CPU_with_coprocessor_is_registered_on_real_grid");
                require(!org.gtlcore.gtlcore.integration.ae2.graph.CraftingEngineRouter.useGraph(),
                        "fixture_uses_the_real_LEGACY_AE_planner_and_CPU_execution_engine");
                var source = IActionSource.ofPlayer(player);
                var input = sharedPattern.getInputs()[0];
                long amount = Math.multiplyExact(256, input.getMultiplier());
                require(child.getGrid().getStorageService().getInventory().insert(input.getPossibleInputs()[0].what(), amount,
                                Actionable.MODULATE, source) == amount,
                        "real_AE_drive_contains_256_whole_patterns_of_physical_input_before_calculation");
                calculation = crafting.beginCraftingCalculation(level, () -> source, output, 256, CalculationStrategy.REPORT_MISSING_ITEMS);
                since = ticks; stage = 5;
            }
            case 5 -> {
                if (!calculation.isDone()) return false;
                var plan = calculation.get();
                writePlanSnapshot(plan);
                require(!plan.simulation() && plan.missingItems().isEmpty()
                                && plan.patternTimes().getOrDefault(sharedPattern, 0L) == 256,
                        "actual_AE_calculation_plans_256_shared_pattern_operations_from_real_ME_storage_simulation_"
                                + plan.simulation() + "_missing_" + plan.missingItems() + "_selectedTimes_"
                                + plan.patternTimes().getOrDefault(sharedPattern, 0L) + "_patternCount_" + plan.patternTimes().size());
                var submit = child.getGrid().getCraftingService().submitJob(plan, null, null, false, IActionSource.ofPlayer(player));
                require(submit.successful(), "real_AE_CraftingService_submitJob_accepts_the_plan_" + submit.errorCode());
                since = ticks; stage = 6;
            }
            case 6 -> {
                if ((ticks - since) % 20 == 0) writeCpuSnapshot();
                if (child.iv$getState().jobs.isEmpty() || peer.iv$getState().jobs.isEmpty()
                        || child.iv$getState().jobs.stream().noneMatch(job -> job.active())
                        || peer.iv$getState().jobs.stream().noneMatch(job -> job.active())) return false;
                for (var buffer : List.of(child, peer)) require(buffer.iv$getState().jobs.stream().allMatch(job ->
                                entryId.equals(job.cloudPattern) && ((CloudMachineIdentity)IvBuffers.controller(buffer))
                                        .cloud$machineInstance(false).equals(job.cloudMachine)),
                        "actual_AE_CPU_dispatch_creates_distinct_shared_original_orders_owned_by_" + buffer.getPos());
                long accepted = child.iv$getState().jobs.stream().mapToLong(job -> job.totalOperations).sum()
                        + peer.iv$getState().jobs.stream().mapToLong(job -> job.totalOperations).sum();
                require(accepted == 256 && child.getGrid().getStorageService().getInventory().getAvailableStacks()
                                .get(sharedPattern.getInputs()[0].getPossibleInputs()[0].what()) == 0,
                        "real_AE_CPU_distributes_all_256_operations_to_two_LCRs_and_debits_each_input_once");
                require(child.iv$getState().jobs.stream().anyMatch(job -> job.active()) && peer.iv$getState().jobs.stream().anyMatch(job -> job.active()),
                        "both_real_native_LCR_executors_run_the_actual_AE_CPU_orders_from_one_central_original");
                var returned = child.cloudStore().extractFromCore(libraryId, player.getUUID(), entryId, UUID.randomUUID());
                require(!returned.isEmpty(), "central_export_returns_the_single_physical_original_once");
                exported = returned;
                require(child.getAvailablePatterns().stream().noneMatch(sharedPattern::equals)
                                && peer.getAvailablePatterns().stream().noneMatch(sharedPattern::equals)
                                && providerCount(sharedPattern) == 0,
                        "central_export_revokes_all_real_AE_cloud_provider_routes_for_one_original");
                require(!child.iv$getState().jobs.isEmpty() && !peer.iv$getState().jobs.isEmpty(),
                        "central_export_preserves_both_previously_accepted_order_ledgers");
                writeCpuSnapshot();
                return true;
            }
            default -> throw new AssertionError("Unknown topology stage");
        }
        return false;
    }
    public static net.minecraft.world.item.ItemStack takeExportedOriginal() {
        var result = exported; exported = net.minecraft.world.item.ItemStack.EMPTY; return result;
    }
    private static String orders(CloudPatternBufferMachine buffer) {
        return buffer.cloudReadyFailure(true) + ":" + buffer.iv$getState().jobs.stream().map(job ->
                "total=" + job.totalOperations + ",remaining=" + job.remaining + ",parallel=" + job.parallel
                        + ",completed=" + job.completedOperations + ",halted=" + job.halted + ",message=" + job.error).toList();
    }
    private static void writePlanSnapshot(ICraftingPlan plan) throws Exception {
        var directory = level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                .toAbsolutePath().normalize().getParent().getParent();
        var snapshot = new LinkedHashMap<String, Object>();
        snapshot.put("entry", entryId.toString()); snapshot.put("simulation", plan.simulation());
        snapshot.put("missingItems", plan.missingItems().toString());
        snapshot.put("selectedPattern", sharedPattern.getDefinition().toStack().save(new net.minecraft.nbt.CompoundTag()).toString());
        snapshot.put("selectedPatternTimes", plan.patternTimes().getOrDefault(sharedPattern, 0L));
        snapshot.put("plannedPatterns", plan.patternTimes().entrySet().stream().map(row -> Map.of(
                "pattern", row.getKey().getDefinition().toStack().save(new net.minecraft.nbt.CompoundTag()).toString(),
                "times", row.getValue())).toList());
        snapshot.put("childPublished", child.getAvailablePatterns().stream().map(pattern ->
                pattern.getDefinition().toStack().save(new net.minecraft.nbt.CompoundTag()).toString()).toList());
        snapshot.put("peerPublished", peer.getAvailablePatterns().stream().map(pattern ->
                pattern.getDefinition().toStack().save(new net.minecraft.nbt.CompoundTag()).toString()).toList());
        var stock = child.getGrid().getStorageService().getInventory().getAvailableStacks();
        snapshot.put("inputInMe", stock.get(sharedPattern.getInputs()[0].getPossibleInputs()[0].what()));
        snapshot.put("outputInMe", stock.get(output));
        Files.writeString(directory.resolve("cloud-topology-plan.json"), new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(snapshot));
    }
    private static void writeCpuSnapshot() throws Exception {
        var crafting = child.getGrid().getCraftingService();
        var directory = level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                .toAbsolutePath().normalize().getParent().getParent();
        var snapshot = new LinkedHashMap<String, Object>();
        snapshot.put("ticks", ticks); snapshot.put("branchCycles", branchCycles); snapshot.put("endpointCycles", endpointCycles);
        snapshot.put("child", orders(child)); snapshot.put("peer", orders(peer));
        snapshot.put("childTasks", child.iv$getState().save().toString()); snapshot.put("peerTasks", peer.iv$getState().save().toString());
        snapshot.put("controllers", List.of(child, peer).stream().map(buffer -> {
            var machine = IvBuffers.controller(buffer); var logic = machine.getRecipeLogic();
            var access = (com.gtl.enhancedcore.common.recipe.iv.IvNativeAccess)logic;
            return machine.getPos() + ":enabled=" + logic.isWorkingEnabled() + ":active=" + logic.isActive()
                    + ":status=" + logic.getStatus() + ":managed=" + access.iv$managed()
                    + ":summary=" + access.iv$summary() + ":runtime=" + runtime(logic);
        }).toList());
        snapshot.put("childJobs", child.iv$getState().jobs.stream().map(job -> job.save().toString()).toList());
        snapshot.put("peerJobs", peer.iv$getState().jobs.stream().map(job -> job.save().toString()).toList());
        snapshot.put("providerCount", providerCount(sharedPattern)); snapshot.put("craftable", crafting.isCraftable(output));
        snapshot.put("cpus", crafting.getCpus().stream().map(cpu -> cpu.getName() + ":" + cpu.getJobStatus()).toList());
        snapshot.put("inputInMe", child.getGrid().getStorageService().getInventory().getAvailableStacks()
                .get(sharedPattern.getInputs()[0].getPossibleInputs()[0].what()));
        Files.writeString(directory.resolve("cloud-compat-cpu-live.json"), new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(snapshot));
    }
    private static String runtime(Object logic) {
        var result = new LinkedHashMap<String, String>();
        for (var type = logic.getClass(); type != null; type = type.getSuperclass()) for (var field : type.getDeclaredFields()) {
            if (!java.util.Set.of("subscription", "lastRecipe", "lastOriginRecipe", "lastFailedMatches", "enhanced$nativeInputsRunning").contains(field.getName())) continue;
            try { field.setAccessible(true); result.put(field.getName(), String.valueOf(field.get(logic))); }
            catch (ReflectiveOperationException failure) { result.put(field.getName(), failure.toString()); }
        }
        return result.toString();
    }
}
