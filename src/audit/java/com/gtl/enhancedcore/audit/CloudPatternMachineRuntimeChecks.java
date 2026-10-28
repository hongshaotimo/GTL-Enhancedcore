package com.gtl.enhancedcore.audit;

import com.mojang.authlib.GameProfile;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gtl.enhancedcore.common.cloud.CloudPatternBufferMachine;
import com.gtl.enhancedcore.common.cloud.CloudPatternLibraryMachine;
import com.gtl.enhancedcore.common.cloud.store.CloudPatternStore;
import com.gtl.enhancedcore.common.recipe.iv.IvBuffers;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayerFactory;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

/** Real registered suppliers and transformed lifecycle methods; no formed structure or AE crafting claim. */
public final class CloudPatternMachineRuntimeChecks {
    private CloudPatternMachineRuntimeChecks() {}
    public static void run(ServerLevel level, List<String> checks) throws Exception {
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.fromString("d8e705e4-46cc-4b7c-8f09-990dba70898c"), "CloudAudit"));
        var libraryHolder = holder(level, "cloud_pattern_library", new BlockPos(112, 64, 112));
        var childHolder = holder(level, "cloud_pattern_buffer", new BlockPos(114, 64, 112));
        var immediateHolder = holder(level, "cloud_pattern_buffer", new BlockPos(116, 64, 112));
        try {
            require(libraryHolder.getMetaMachine() instanceof CloudPatternLibraryMachine, "Registered library uses actual cloud supplier", checks);
            require(childHolder.getMetaMachine() instanceof CloudPatternBufferMachine, "Registered child uses actual sparse cloud supplier", checks);
            checkInputAbilities(level, checks);
            var main = (CloudPatternLibraryMachine)libraryHolder.getMetaMachine();
            var child = (CloudPatternBufferMachine)childHolder.getMetaMachine();
            require(child.canShared(), "Cloud endpoint permits actual multiblock part sharing", checks);
            require(org.gtlcore.gtlcore.integration.ae2.wireless.WirelessAeNetworkRuntime.isWirelessMeTargetId(
                    child.getDefinition().getId()), "GTL wireless target whitelist includes registered cloud endpoint", checks);
            checkFurnaceCatalog(level, checks);
            require(((Object[])child.getInternalInventory()).length == 0, "Real cloud child constructor allocates zero upstream pattern stock slots", checks);
            require(child.getPatternInventory().getSlots() == 0, "Unbound cloud view starts sparse, without allocating infinite slots", checks);
            main.onMachinePlaced(player, ItemStack.EMPTY); child.onMachinePlaced(player, ItemStack.EMPTY);
            require(child.cloudLibraryId() == null && child.cloudNodeId() == null,
                    "Offline child never substitutes a player's default library for a real ME binding", checks);
            // This holder-only lifecycle probe has no world structure/grid. Seed the separately
            // supported explicit address path; the full flow checks actual automatic ME binding.
            CompoundTag explicitAddress = new CompoundTag(); child.saveCustomPersistedData(explicitAddress, false);
            explicitAddress.getCompound("cloudAddress").putUUID("library", main.libraryId());
            explicitAddress.getCompound("cloudAddress").putBoolean("manual", true);
            child.loadCustomPersistedData(explicitAddress);
            Method attach = CloudPatternBufferMachine.class.getDeclaredMethod("attach"); attach.setAccessible(true); attach.invoke(child);
            require(child.cloudNode() != null && child.cloudNodeId() != null, "Actual child attaches exactly one world order partition", checks);
            var store = CloudPatternStore.get(level); var node = child.cloudNode();
            var unrelatedLibrary = store.defaultLibrary(UUID.randomUUID());
            var unrelatedNode = store.createNode(store.library(unrelatedLibrary).owner(), unrelatedLibrary,
                    level.dimension().location(), new BlockPos(124, 64, 112).asLong());
            store.checkpoint();
            var foreignCaptures = new java.util.concurrent.atomic.AtomicInteger();
            store.bindNodeSnapshot(unrelatedNode, () -> { foreignCaptures.incrementAndGet(); return new CompoundTag(); },
                    CompoundTag::new, CompoundTag::new);
            child.markDirty(); store.checkpointLibrary(main.libraryId());
            require(foreignCaptures.get() == 0 && unrelatedNode.isDirty(),
                    "Upload-scoped checkpoint does not capture or clear unrelated library nodes", checks);
            store.checkpoint();
            require(foreignCaptures.get() == 1, "Full checkpoint still durably captures excluded dirty node", checks);
            store.unbindNodeSnapshot(unrelatedNode);
            require(store.active(node.id(), child), "Real child is its unique active endpoint", checks);
            child.iv$getState().accepting = false; child.iv$getState().owner = "audit-controller-snapshot"; child.markDirty(); store.checkpoint();
            require(!node.ivState().getBoolean("accepting") && node.ivState().getString("owner").equals("audit-controller-snapshot"), "Actual child markDirty persists live IV state through world supplier snapshot", checks);
            CompoundTag worldTag = new CompoundTag(), dropTag = new CompoundTag();
            child.saveCustomPersistedData(worldTag, false); child.saveCustomPersistedData(dropTag, true);
            require(worldTag.contains("cloudAddress") && !worldTag.contains(IvBuffers.SAVE_KEY), "Child world save holds address but no second authoritative IV ledger", checks);
            require(!dropTag.contains("cloudAddress") && !dropTag.contains(IvBuffers.SAVE_KEY), "Child drops contain neither endpoint identity nor order stock", checks);
            CompoundTag raw = new CompoundTag(); raw.putString("audit", "untrusted-tool-data");
            require(!child.pasteFromTag(raw), "Raw upstream paste cannot overwrite a cloud lease view", checks);
            require(child.cutToTag(raw).equals(raw), "Raw upstream cut cannot serialize a cloud original into tool NBT", checks);
            UUID originalNode = node.id(); child.onUnload();
            require(!store.active(originalNode, child) && !store.node(originalNode).detached(), "Actual unload deactivates without releasing its persistent identity", checks);
            attach.invoke(child); require(originalNode.equals(child.cloudNodeId()), "Actual unload/re-attach preserves original node address", checks);
            child.onMachineRemoved(); store.checkpoint();
            require(store.node(originalNode).detached(), "Actual removal detaches the authoritative node", checks);
            require(store.library(main.libraryId()).recoveries().stream().filter(r -> r.nodeId().equals(originalNode)).allMatch(r -> r.status().equals("EMPTY")), "Empty child removal does not populate frozen task recovery list", checks);
            UUID libraryId = main.libraryId(); main.onMachineRemoved(); store.checkpoint();
            require(store.library(libraryId).owner().equals(player.getUUID()), "Removing access-point block never deletes the world-owned library", checks);
            var immediate = (CloudPatternBufferMachine)immediateHolder.getMetaMachine();
            immediate.onMachinePlaced(player, ItemStack.EMPTY); immediate.onMachineRemoved();
            require(immediate.cloudNodeId() == null, "Removing an unbound child does not create a phantom cloud endpoint", checks);
            checkImportSource(level, checks);
        } finally { libraryHolder.setLevel(null); childHolder.setLevel(null); immediateHolder.setLevel(null); }
    }
    private static void checkFurnaceCatalog(ServerLevel level, List<String> checks) {
        var type = com.gregtechceu.gtceu.common.data.GTRecipeTypes.FURNACE_RECIPES;
        var catalog = com.gtl.enhancedcore.common.cloud.CloudPatternMatching.recipes(level, type);
        var converted = type.getProxyRecipes().values().stream().flatMap(List::stream).toList();
        require(!converted.isEmpty() && catalog.containsAll(converted), "Cloud furnace catalog includes actual GT converted smelting recipes", checks);
        var smelter = GTRegistries.MACHINES.get(new ResourceLocation("gtceu", "multi_smelter"));
        require(smelter != null && java.util.Arrays.asList(smelter.getRecipeTypes()).contains(type),
                "Industrial furnace real ID multi_smelter retains electric furnace capability", checks);
        var source = converted.getFirst();
        var job = new com.gtl.enhancedcore.common.recipe.iv.IvJob(0, source.copy(), new CompoundTag(), java.util.Map.of(), java.util.Map.of(), 1);
        job.cloudMachine = UUID.randomUUID(); job.cloudPattern = UUID.randomUUID();
        job.ivGalaxy = true; job.ivGalaxyEUt = java.math.BigInteger.valueOf(Long.MAX_VALUE).shiftLeft(10);
        var restored = com.gtl.enhancedcore.common.recipe.iv.IvJob.load(job.save());
        require(job.cloudMachine.equals(restored.cloudMachine) && job.cloudPattern.equals(restored.cloudPattern),
                "Shared executor and original lease identity survive actual job codec", checks);
        require(restored.ivGalaxy && restored.ivGalaxyEUt.equals(job.ivGalaxyEUt),
                "Stellar IV source and wide tick ceiling survive actual job codec", checks);
    }
    private static void checkInputAbilities(ServerLevel level, List<String> checks) throws Exception {
        var cloud = GTRegistries.MACHINES.get(new ResourceLocation("gtl_enhancedcore", "cloud_pattern_buffer"));
        var upstream = GTRegistries.MACHINES.get(new ResourceLocation("gtladditions", "me_super_pattern_buffer"));
        var position = new BlockPos(120, 80, 112);
        require(level.isEmptyBlock(position), "Input-ability audit uses an empty task-owned world position", checks);
        level.setBlock(position, cloud.defaultBlockState(), 3);
        try {
            var update = com.gregtechceu.gtceu.api.pattern.MultiblockState.class.getDeclaredMethod("update", BlockPos.class,
                    com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate.class); update.setAccessible(true);
            for (var ability : List.of(com.gregtechceu.gtceu.api.machine.multiblock.PartAbility.IMPORT_ITEMS,
                    com.gregtechceu.gtceu.api.machine.multiblock.PartAbility.IMPORT_FLUIDS)) {
                require(ability.isApplicable(cloud.getBlock()) && ability.isApplicable(upstream.getBlock()),
                        "Cloud and upstream super buffer share actual registered input role " + ability, checks);
                var predicate = com.gregtechceu.gtceu.api.pattern.Predicates.abilities(ability);
                var state = new com.gregtechceu.gtceu.api.pattern.MultiblockState(level, position);
                update.invoke(state, position, predicate);
                require(predicate.test(state), "Unmodified upstream input predicate accepts actual placed cloud block for " + ability, checks);
            }
            require(!com.gregtechceu.gtceu.api.machine.multiblock.PartAbility.EXPORT_ITEMS.isApplicable(cloud.getBlock())
                            && !com.gregtechceu.gtceu.api.machine.multiblock.PartAbility.EXPORT_FLUIDS.isApplicable(cloud.getBlock()),
                    "Cloud stays in input positions; independent AE completion does not advertise ordinary output positions", checks);
        } finally { level.removeBlock(position, false); }
    }
    private static void checkImportSource(ServerLevel level, List<String> checks) {
        var definition = GTRegistries.MACHINES.get(new ResourceLocation("gtladditions", "me_super_pattern_buffer"));
        var sourceHolder = (MetaMachineBlockEntity)definition.getBlockEntityType().create(new BlockPos(118, 64, 112), definition.defaultBlockState());
        sourceHolder.setLevel(level);
        try {
            var source = (org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachine)sourceHolder.getMetaMachine();
            var access = (com.gtl.enhancedcore.common.cloud.transfer.CloudImportSourceAccess)source;
            com.gtl.enhancedcore.common.cloud.transfer.CloudImportJournal.reconcile(source);
            var original = new ItemStack(net.minecraft.world.item.Items.PAPER); original.getOrCreateTag().putString("author", "quarantine-audit");
            source.getPatternInventory().setStackInSlot(0, original);
            CompoundTag before = source.getPatternInventory().serializeNBT();
            access.cloud$quarantineImport("isolated audit intentional uncertainty");
            require(source.getPatternInventory().getStackInSlot(0).isEmpty()
                    && source.getPatternInventory().extractItem(0, 1, false).isEmpty(), "Actual quarantined source hides originals from existing UI reads and extraction", checks);
            require(before.equals(source.getPatternInventory().serializeNBT()), "Quarantine preserves exact serialized original including its author NBT", checks);
            require(source.copyToTag(new CompoundTag()).isEmpty() && source.cutToTag(new CompoundTag()).isEmpty()
                    && !source.pasteFromTag(new CompoundTag()), "Actual quarantined source blocks raw clipboard copy, cut and paste", checks);
            require(source.getAvailablePatterns().isEmpty(), "Quarantined source publishes no recipes", checks);
        } finally { sourceHolder.setLevel(null); }
    }
    private static MetaMachineBlockEntity holder(ServerLevel level, String id, BlockPos position) {
        var definition = GTRegistries.MACHINES.get(new ResourceLocation("gtl_enhancedcore", id));
        if (definition == null) throw new AssertionError("Missing actual registered cloud definition " + id);
        var holder = (MetaMachineBlockEntity)definition.getBlockEntityType().create(position, definition.defaultBlockState());
        if (holder == null) throw new AssertionError("Cloud block entity factory returned null"); holder.setLevel(level); return holder;
    }
    private static void require(boolean value, String message, List<String> checks) { if (!value) throw new AssertionError(message); checks.add(message); }
}
