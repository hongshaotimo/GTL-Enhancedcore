package com.gtl.enhancedcore.audit;

import com.gregtechceu.gtceu.api.pattern.MultiblockState;
import com.gregtechceu.gtceu.api.pattern.MultiblockWorldSavedData;
import com.gtl.enhancedcore.common.performance.structure.StructureSearchAccess;
import com.gtl.enhancedcore.common.performance.structure.StructureSearchBackoff;
import com.gtl.enhancedcore.common.performance.structure.StructureSearchWatches;
import com.gtl.enhancedcore.common.performance.structure.StructureWorldAccess;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.List;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

/** Actual transformed GTCEu mapping checks at title screen, without loading a player world. */
public final class StructurePerformanceChecks {
    private StructurePerformanceChecks() {}

    public static void run(List<String> checks) throws Exception {
        check(StructureSearchAccess.class.isAssignableFrom(
                com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine.class),
                "structure_search_controller_mixin_applied", checks);
        var constructor = MultiblockWorldSavedData.class.getDeclaredConstructor(ServerLevel.class);
        constructor.setAccessible(true);
        var saved = constructor.newInstance((ServerLevel) null);
        check(saved instanceof StructureWorldAccess, "structure_world_index_mixin_applied", checks);
        var first = state(new BlockPos(-1, 64, -1));
        for (int x = -32; x < 32; x++) for (int z = -32; z < 32; z++) first.cache.add(BlockPos.asLong(x, 64, z));
        saved.addMapping(first);
        check(saved.chunkPosMapping.size() == 16, "structure_index_has_sixteen_unique_chunks", checks);
        saved.addMapping(first);
        check(saved.chunkPosMapping.size() == 16 && saved.chunkPosMapping.values().stream().allMatch(set -> set.size() == 1),
                "structure_reindex_is_idempotent", checks);
        var second = state(new BlockPos(0, 65, 0));
        second.cache.add(BlockPos.asLong(-1, 64, -1));
        saved.addMapping(second);
        saved.removeMapping(first);
        check(saved.chunkPosMapping.size() == 1 && saved.chunkPosMapping.get(new ChunkPos(-1, -1)).contains(second),
                "structure_remove_preserves_shared_chunk_controller", checks);
        var replacement = state(second.controllerPos);
        replacement.cache.add(BlockPos.asLong(48, 64, 48));
        saved.addMapping(replacement);
        saved.removeMapping(second);
        check(saved.mapping.get(second.controllerPos) == replacement && saved.chunkPosMapping.size() == 1
                        && saved.chunkPosMapping.containsKey(new ChunkPos(3, 3)),
                "structure_stale_remove_cannot_remove_replacement", checks);
        saved.removeMapping(replacement);
        check(saved.mapping.isEmpty() && saved.chunkPosMapping.isEmpty(), "structure_remove_clears_empty_chunks", checks);

        var budget = new StructureSearchBackoff();
        long token = budget.begin(0);
        budget.failed(0, token);
        check(budget.begin(1_999_999_999L) == -1, "structure_failed_retry_waits_two_seconds", checks);
        var watches = ((StructureWorldAccess) saved).enhancedcore$searchWatches();
        watches.watch(budget, new StructureSearchWatches.Bounds(-1, 0, -1, 1, 255, 1));
        watches.blockChanged(0, 64, 0);
        check(budget.begin(0) > token, "structure_related_change_wakes_failed_search", checks);
        watches.clear();
        check(watches.searchCount() == 0 && watches.referenceCount() == 0,
                "structure_level_cleanup_releases_watch_index", checks);
    }

    /** Invoke on the server thread of an isolated new audit world, never a player's save. */
    public static void runWorld(ServerLevel level, List<String> checks) throws Exception {
        if (!level.getServer().isSameThread()) throw new IllegalStateException("server thread required");
        BlockPos pos = new BlockPos(0, 100, 0);
        var definition = GTRegistries.MACHINES.get(new ResourceLocation("gtceu", "coke_oven"));
        if (!level.isEmptyBlock(pos)) throw new IllegalStateException("isolated fixture location must be empty");
        level.setBlockAndUpdate(pos, definition.defaultBlockState());
        var machine = (MultiblockControllerMachine) MetaMachine.getMachine(level, pos);
        var saved = MultiblockWorldSavedData.getOrCreate(level);
        var budget = ((StructureSearchAccess) machine).enhancedcore$searchBackoff();
        try {
            // Keep the real async service from racing this synchronous lifecycle fixture.
            saved.removeAsyncLogic(machine);
            machine.getPatternLock().lock();
            try {
                long token = budget.begin(0);
                budget.failed(0, token);
                machine.onPartUnload();
                check(!budget.current(token) && machine.getMultiblockState().hasError(),
                        "structure_part_unload_wakes_and_keeps_error", checks);
                saved.removeAsyncLogic(machine);
                token = budget.begin(0);
                budget.failed(0, token);
                Direction old = machine.getFrontFacing();
                machine.onRotated(old, old.getClockWise());
                check(!budget.current(token) && !machine.isFormed(),
                        "structure_rotation_immediately_invalidates_and_wakes", checks);
                saved.removeAsyncLogic(machine);

                var watches = ((StructureWorldAccess) saved).enhancedcore$searchWatches();
                token = budget.begin(0);
                budget.failed(0, token);
                watches.watch(budget, new StructureSearchWatches.Bounds(0, 100, 0, 1, 100, 0));
                level.setBlockAndUpdate(pos.east(), Blocks.STONE.defaultBlockState());
                check(!budget.current(token) && budget.failures() == 0,
                        "structure_real_block_event_wakes_unfinished_search", checks);
                level.removeBlock(pos.east(), false);
                saved.addAsyncLogic(machine);
                saved.addAsyncLogic(machine);
                saved.addAsyncLogic(machine);
                var field = MultiblockWorldSavedData.class.getDeclaredField("controllers");
                field.setAccessible(true);
                var controllers = (java.util.Collection<?>) field.get(saved);
                check(controllers.stream().filter(entry -> entry == machine).count() == 1,
                        "structure_async_membership_deduplicates_repeated_add", checks);
                machine.onUnload();
                check(budget.begin(Long.MAX_VALUE) == -1 && watches.searchCount() == 0
                                && controllers.stream().noneMatch(entry -> entry == machine),
                        "structure_controller_unload_cancels_search_and_watch", checks);
                machine.onLoad();
                check(budget.begin(0) >= 0, "structure_controller_reload_resets_backoff", checks);
                budget.lockBusy();
            } finally {
                machine.getPatternLock().unlock();
            }
        } finally {
            machine.onUnload();
            level.removeBlock(pos, false);
            level.removeBlock(pos.east(), false);
        }
    }

    private static MultiblockState state(BlockPos controller) {
        var state = new MultiblockState(null, controller);
        state.cache = new LongOpenHashSet();
        return state;
    }

    private static void check(boolean condition, String label, List<String> checks) {
        if (!condition) throw new AssertionError(label);
        checks.add(label);
    }
}
