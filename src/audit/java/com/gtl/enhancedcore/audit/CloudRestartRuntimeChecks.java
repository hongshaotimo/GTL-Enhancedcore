package com.gtl.enhancedcore.audit;

import appeng.api.stacks.AEItemKey;
import appeng.me.service.CraftingService;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder;
import com.gtl.enhancedcore.common.cloud.*;
import com.gtl.enhancedcore.common.cloud.store.CloudPatternStore;
import com.gtl.enhancedcore.common.cloud.store.CloudMachineIdentity;
import com.gtl.enhancedcore.common.recipe.iv.IvBuffers;
import com.gtl.enhancedcore.common.recipe.iv.IvJob;
import com.lowdragmc.lowdraglib.gui.modular.ModularUIContainer;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import org.gtlcore.gtlcore.integration.ae2.wireless.WirelessAeSavedData;

/** Separate-process recovery of the real first-phase world. Never creates or replaces its machines. */
public final class CloudRestartRuntimeChecks {
    private static ServerLevel level;
    private static List<String> checks;
    private static CompoundTag expected;
    private static CloudPatternLibraryMachine main;
    private static List<CloudPatternBufferMachine> children;
    private static FakePlayer owner;
    private static int ticks, stage, since;
    private CloudRestartRuntimeChecks() {}

    public static void prepare(ServerLevel world, CloudPatternLibraryMachine library,
                               List<CloudPatternBufferMachine> endpoints, FakePlayer actor, UUID original,
                               UUID frequency, List<String> results) throws Exception {
        var store = CloudPatternStore.get(world); store.checkpointLibrary(library.libraryId());
        var manifest = new CompoundTag(); manifest.putLong("firstJvmPid", ProcessHandle.current().pid());
        manifest.putLong("main", library.getPos().asLong()); manifest.putUUID("library", library.libraryId());
        manifest.putUUID("owner", actor.getUUID()); manifest.putUUID("original", original); manifest.putUUID("frequency", frequency);
        var originals = new ListTag();
        for (var entry : store.library(library.libraryId()).entries()) {
            var row = new CompoundTag(); row.putUUID("id", entry.id()); row.putString("status", entry.status().name());
            row.put("original", entry.original().save(new CompoundTag())); row.put("configuration", entry.configuration()); originals.add(row);
        }
        manifest.put("originals", originals); var nodes = new ListTag();
        for (var child : endpoints) {
            if (child.cloudNode().leases().stream().noneMatch(lease -> original.equals(lease.entryId())))
                throw new AssertionError("restart:original must have both real endpoint leases before save");
            var row = new CompoundTag(); row.putLong("position", child.getPos().asLong()); row.putUUID("id", child.cloudNodeId());
            row.putLong("controller", IvBuffers.controller(child).getPos().asLong());
            row.putLong("generation", child.cloudNode().generation()); row.putUUID("machine", child.cloudNode().machineInstance());
            row.putUUID("tasksIdentity", child.iv$getState().identity); row.putString("tasksOwner", child.iv$getState().owner);
            row.putBoolean("accepting", child.iv$getState().accepting);
            row.put("leases", leases(child)); row.put("jobs", jobs(child)); row.put("paidBatches", paidBatches(child));
            row.put("sourceRoute", child.cloudNode().sourceRoute());
            if (child.iv$getState().jobs.isEmpty()) throw new AssertionError("restart:both persisted endpoint ledgers must retain accepted orders");
            nodes.add(row);
        }
        manifest.put("nodes", nodes);
        NbtIo.writeCompressed(manifest, world.getServer().getWorldPath(LevelResource.ROOT).resolve("cloud-compat-restart.dat").toFile());
        results.add("restart:normal_first_JVM_checkpoint_records_physical_originals_two_node_lease_ids_slots_generations_and_256_accepted_operations");
    }

    public static void begin(ServerLevel world, List<String> results) throws Exception {
        level = world; checks = results; ticks = 0; stage = 0; since = 0;
        expected = NbtIo.readCompressed(world.getServer().getWorldPath(LevelResource.ROOT).resolve("cloud-compat-restart.dat").toFile());
        require(expected.getLong("firstJvmPid") != ProcessHandle.current().pid(), "second_independent_JVM_has_a_distinct_OS_process_id");
        var mainPos = BlockPos.of(expected.getLong("main")); world.getChunk(mainPos);
        main = (CloudPatternLibraryMachine)MetaMachine.getMachine(world, mainPos);
        children = new ArrayList<>();
        for (var value : expected.getList("nodes", Tag.TAG_COMPOUND)) {
            var row = (CompoundTag)value; var pos = BlockPos.of(row.getLong("position")); world.getChunk(pos);
            children.add((CloudPatternBufferMachine)MetaMachine.getMachine(world, pos));
        }
        require(main != null && children.size() == 2 && children.stream().allMatch(Objects::nonNull),
                "reopened_saved_world_contains_the_same_real_library_and_two_cloud_blocks");
        owner = FakePlayerFactory.get(world, new GameProfile(expected.getUUID("owner"), "CloudCompatAudit"));
        // The audit's deterministic recipe is fixture data, registered in each separate process like a datapack recipe.
        var controllerPos = BlockPos.of(expected.getList("nodes", Tag.TAG_COMPOUND).getCompound(0).getLong("controller"));
        world.getChunk(controllerPos);
        var controller = (WorkableElectricMultiblockMachine)MetaMachine.getMachine(world, controllerPos);
        var recipe = GTRecipeBuilder.of(new ResourceLocation("gtl_enhancedcore", "audit_cloud_compat_shared"), controller.getRecipeType())
                .inputItems(Items.APPLE).outputItems(Items.GOLD_INGOT).duration(2000000000).EUt(8).buildRawRecipe();
        var recipes = new ArrayList<net.minecraft.world.item.crafting.Recipe<?>>(world.getRecipeManager().getRecipes());
        recipes.add(recipe); controller.getRecipeType().getLookup().addRecipe(recipe); world.getRecipeManager().replaceRecipes(recipes);
    }

    public static boolean step() throws Exception {
        if (++ticks > 1000) throw new AssertionError("restart:timeout stage=" + stage + " main=" + main.isFormed()
                + " endpoints=" + children.stream().map(child -> child.cloudReadyFailure(true)).toList());
        for (var child : children) if (IvBuffers.controller(child) instanceof WorkableElectricMultiblockMachine controller
                && controller.getEnergyContainer() != null) controller.getEnergyContainer().addEnergy(controller.getEnergyContainer().getEnergyCapacity());
        switch (stage) {
            case 0 -> {
                if (!main.isFormed() || main.serviceGrid() == null || children.stream().anyMatch(child -> child.cloudNode() == null
                        || !child.getMainNode().isActive() || IvBuffers.controller(child) == null || child.getGrid() != main.serviceGrid())) return false;
                require(expected.getUUID("library").equals(main.libraryId()), "saved_library_identity_automatically_restores");
                var wireless = WirelessAeSavedData.get(level.getServer());
                require(expected.getUUID("frequency").equals(wireless.getExactMemberNetwork(member(main.getPos())))
                                && children.stream().allMatch(child -> expected.getUUID("frequency").equals(wireless.getExactMemberNetwork(member(child.getPos())))),
                        "three_exact_saved_wireless_memberships_automatically_restore_the_shared_real_grid");
                verifyAuthority(); verifyJobs();
                verifyPaidBatches();
                require(children.stream().allMatch(child -> !child.cloudMayUseNetwork() && !child.cloudReady(true)
                                && child.getAvailablePatterns().isEmpty()),
                        "reconstructed_AE_grid_does_not_silently_authorize_old_order_returns_or_new_dispatch");
                require(!main.serviceGrid().getCraftingService().isCraftable(AEItemKey.of(Items.GOLD_INGOT)),
                        "actual_AE_index_withholds_both_old_origin_routes_until_explicit_authorization");
                require(main.serviceGrid().getStorageService().getInventory().getAvailableStacks().get(AEItemKey.of(Items.APPLE)) == 0,
                        "restart_does_not_reinsert_or_duplicate_the_256_previously_paid_inputs");
                since = ticks; stage = 1;
            }
            case 1 -> {
                if (ticks - since < 40) return false;
                verifyAuthority(); verifyJobs();
                verifyPaidBatches();
                require(children.stream().allMatch(child -> !child.cloudMayUseNetwork()),
                        "old_origin_remains_suspended_across_real_server_ticks_without_explicit_evidence");
                for (var child : children) {
                    owner.setPos(Vec3.atCenterOf(child.getPos()));
                    var ui = child.createUI(owner); ui.initWidgets();
                    var previous = owner.containerMenu; owner.containerMenu = new ModularUIContainer(ui, 227);
                    try {
                        var before = child.cloudUiInitialSnapshot(owner);
                        require(before.getBoolean("hasOrigin") && !before.getBoolean("returnNetworkReady"),
                                "production_UI_reports_saved_origin_and_blocks_return_before_reconnect");
                        var payload = new CompoundTag(); payload.putLong("revision", before.getLong("revision"));
                        child.cloudUiAction(owner, "reconnect", payload);
                        require(child.cloudMayUseNetwork(), "production_reconnect_action_authenticates_original_physical_endpoint_on_reconstructed_grid");
                    } finally { owner.containerMenu = previous; }
                }
                since = ticks; stage = 2;
            }
            case 2 -> {
                if (ticks - since < 25 || children.stream().anyMatch(child -> !child.cloudReady(true)
                        || child.getAvailablePatterns().isEmpty())) return false;
                var first = children.get(0).getAvailablePatterns().getFirst();
                var crafting = (CraftingService)main.serviceGrid().getCraftingService();
                int providers = 0;
                for (var provider : crafting.getProviders(first)) if (children.contains(provider)) providers++;
                require(providers == 2 && crafting.isCraftable(AEItemKey.of(Items.GOLD_INGOT))
                                && children.get(1).getAvailablePatterns().contains(first),
                        "real_AE_craftingFor_provider_index_recovers_two_routes_for_one_saved_original_after_explicit_reconnect");
                verifyAuthority(); verifyJobs();
                verifyPaidBatches();
                CloudPatternStore.get(level).checkpointLibrary(main.libraryId());
                return true;
            }
            default -> throw new AssertionError("restart:unknown stage");
        }
        return false;
    }
    private static void verifyAuthority() {
        var library = CloudPatternStore.get(level).library(expected.getUUID("library"));
        require(library.entries().size() == expected.getList("originals", Tag.TAG_COMPOUND).size(),
                "all_central_original_records_restore_exactly_once");
        for (var value : expected.getList("originals", Tag.TAG_COMPOUND)) {
            var row = (CompoundTag)value; var entry = library.entry(row.getUUID("id"));
            require(entry.original().save(new CompoundTag()).equals(row.getCompound("original"))
                            && entry.configuration().equals(row.getCompound("configuration")) && entry.status().name().equals(row.getString("status")),
                    "saved_original_payload_configuration_and_authoritative_status_are_unchanged_" + entry.id());
        }
        var nodes = expected.getList("nodes", Tag.TAG_COMPOUND);
        for (int i = 0; i < children.size(); i++) {
            var child = children.get(i); var row = nodes.getCompound(i);
            require(row.getUUID("id").equals(child.cloudNodeId()) && row.getLong("generation") == child.cloudNode().generation()
                            && row.getUUID("machine").equals(child.cloudNode().machineInstance())
                            && row.getUUID("machine").equals(((CloudMachineIdentity)IvBuffers.controller(child)).cloud$machineInstance(false))
                            && leases(child).equals(row.getList("leases", Tag.TAG_COMPOUND))
                            && row.getCompound("sourceRoute").equals(child.cloudNode().sourceRoute()),
                    "node_identity_machine_ownership_and_all_lease_ids_stable_slots_generations_restore_without_reallocation");
            require(row.getUUID("tasksIdentity").equals(child.iv$getState().identity)
                            && row.getString("tasksOwner").equals(child.iv$getState().owner)
                            && row.getBoolean("accepting") == child.iv$getState().accepting && child.iv$getState().healthy(),
                    "durable_task_partition_identity_owner_accepting_latch_and_health_restore_unchanged");
        }
    }
    private static void verifyJobs() {
        long total = 0;
        var rows = expected.getList("nodes", Tag.TAG_COMPOUND);
        for (int i = 0; i < children.size(); i++) {
            var child = children.get(i);
            require(jobs(child).equals(rows.getCompound(i).getList("jobs", Tag.TAG_COMPOUND)),
                    "accepted_order_ids_owners_recipe_scales_and_all_material_buckets_survive_independent_JVM_restart");
            total += child.iv$getState().jobs.stream().mapToLong(job -> job.totalOperations).sum();
        }
        require(total == 256, "exactly_256_operations_remain_owned_once_across_both_restarted_ledgers");
    }
    private static ListTag leases(CloudPatternBufferMachine child) {
        var result = new ListTag();
        for (var lease : child.cloudNode().leases()) {
            var row = new CompoundTag(); row.putUUID("id", lease.id()); row.putUUID("entry", lease.entryId());
            row.putInt("slot", lease.stableSlot()); row.putLong("generation", lease.generation()); result.add(row);
        }
        return result;
    }
    private static ListTag jobs(CloudPatternBufferMachine child) {
        var result = new ListTag();
        for (IvJob job : child.iv$getState().jobs) {
            var saved = job.save(); var row = new CompoundTag();
            for (String key : List.of("id", "slot", "cloudPattern", "cloudMachine", "cloudPatternOperations", "totalOperations", "remaining",
                    "completedOperations", "deliveredOperations", "cancelledOperations", "pendingOperations", "input", "virtual", "pending", "refunds", "completion"))
                if (saved.contains(key)) row.put(key, saved.get(key).copy());
            result.add(row);
        }
        return result;
    }
    private static ListTag paidBatches(CloudPatternBufferMachine child) {
        var result = new ListTag();
        for (IvJob job : child.iv$getState().jobs) if (job.active()) {
            var saved = job.save(); var row = new CompoundTag();
            for (String key : List.of("id", "parallel", "nativeRecipe", "nativeCapacity", "nativeWireless", "nativeWirelessEUt",
                    "eut", "duration", "elapsed", "energyTotal", "energyLeft", "supplyPower", "cycle", "input"))
                if (saved.contains(key)) row.put(key, saved.get(key).copy());
            result.add(row);
        }
        return result;
    }
    private static void verifyPaidBatches() throws Exception {
        var observations = new ArrayList<Map<String, Object>>();
        var rows = expected.getList("nodes", Tag.TAG_COMPOUND);
        for (int index = 0; index < children.size(); index++) {
            var child = children.get(index); var previous = rows.getCompound(index).getList("paidBatches", Tag.TAG_COMPOUND);
            require(!previous.isEmpty() && previous.size() == child.iv$getState().jobs.stream().filter(IvJob::active).count(),
                    "all_committed_native_batches_remain_active_without_a_second_input_admission");
            for (var value : previous) {
                var before = (CompoundTag)value;
                var job = child.iv$getState().jobs.stream().filter(candidate -> candidate.id.equals(before.getUUID("id"))).findFirst().orElseThrow();
                var now = job.save();
                require(job.active() && job.nativeRecipe != null && now.get("nativeRecipe").equals(before.get("nativeRecipe"))
                                && job.parallel == before.getLong("parallel") && job.nativeCapacity == before.getLong("nativeCapacity")
                                && job.nativeWireless == before.getBoolean("nativeWireless")
                                && job.nativeWirelessEUt.toString().equals(before.getString("nativeWirelessEUt"))
                                && job.duration == before.getInt("duration") && job.eut == before.getLong("eut")
                                && job.energyTotal.toString().equals(before.getString("energyTotal"))
                                && job.supplyPower == before.getLong("supplyPower")
                                && job.cycle.equals(before.getString("cycle"))
                                && now.getList("input", Tag.TAG_COMPOUND).equals(before.getList("input", Tag.TAG_COMPOUND)),
                        "paid_native_recipe_parallel_duration_energy_total_cycle_and_exact_unprocessed_input_tail_restore_without_recommit");
                var beforeLeft = new java.math.BigInteger(before.getString("energyLeft"));
                int advanced = job.elapsed - before.getInt("elapsed");
                var paid = beforeLeft.subtract(job.energyLeft);
                var perTick = job.nativeWireless ? job.nativeWirelessEUt : java.math.BigInteger.valueOf(job.eut);
                require(advanced >= 0 && job.elapsed < job.duration && job.energyLeft.signum() >= 0
                                && job.energyLeft.compareTo(job.energyTotal) <= 0 && paid.signum() >= 0
                                && paid.equals(perTick.multiply(java.math.BigInteger.valueOf(advanced))),
                        "natural_shutdown_and_reloaded_ticks_advance_paid_energy_exactly_once_without_reset_or_extra_charge");
                var observation = new LinkedHashMap<String, Object>();
                observation.put("node", child.cloudNodeId().toString()); observation.put("job", job.id.toString());
                observation.put("firstElapsed", before.getInt("elapsed")); observation.put("observedElapsed", job.elapsed);
                observation.put("advancedTicksAcrossShutdownAndRestart", advanced); observation.put("paidEUAcrossBoundary", paid.toString());
                observation.put("energyTotal", job.energyTotal.toString()); observation.put("energyLeft", job.energyLeft.toString());
                observation.put("parallel", job.parallel); observation.put("nativeCapacity", job.nativeCapacity);
                observation.put("eut", job.eut); observation.put("duration", job.duration); observation.put("cycle", job.cycle);
                observation.put("firstInputTail", before.getList("input", Tag.TAG_COMPOUND).toString());
                observation.put("observedInputTail", now.getList("input", Tag.TAG_COMPOUND).toString());
                observation.put("committedNativeRecipe", now.get("nativeRecipe").toString());
                observations.add(observation);
            }
        }
        var directory = level.getServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getParent().getParent();
        java.nio.file.Files.writeString(directory.resolve("cloud-compat-restart-paid.json"),
                new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(Map.of("status", "PASS", "observations", observations,
                        "boundary", "First manifest precedes normal server shutdown; exact elapsed delta includes natural ticks after that snapshot and during the second JVM. This compares the durable paid batch ledger; direct restart physical container debit is not independently instrumented.")));
    }
    private static WirelessAeSavedData.MemberKey member(BlockPos pos) {
        return new WirelessAeSavedData.MemberKey(GlobalPos.of(level.dimension(), pos), null);
    }
    private static void require(boolean value, String label) {
        if (!value) throw new AssertionError("restart:" + label);
        checks.add("restart:" + label);
    }
}
