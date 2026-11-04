package com.gtl.enhancedcore.audit;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.pattern.MultiblockWorldSavedData;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gtl.enhancedcore.common.performance.structure.StructureSearchAccess;
import com.gtl.enhancedcore.common.performance.structure.StructureSearchBackoff;
import com.gtl.enhancedcore.common.performance.structure.StructureWorldAccess;
import com.gtl.enhancedcore.mixin.gtceu.BlockPatternAccessor;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Cross-tick fixture for a freshly created isolated world. No event subscriber or world launcher. */
public final class StructurePerformanceWorldChecks {
    private final ServerLevel level;
    private final List<String> checks;
    private final BlockPos origin = new BlockPos(64, 100, 64);
    private final Map<BlockPos, BlockState> layout = new LinkedHashMap<>();
    private MultiblockControllerMachine machine;
    private StructureSearchBackoff budget;
    private BlockPos shell;
    private BlockState shellState;
    private int stage;
    private long deadline;
    private boolean closed;

    public StructurePerformanceWorldChecks(ServerLevel level, List<String> checks) throws Exception {
        this.level = level;
        this.checks = checks;
        StructurePerformanceChecks.runWorld(level, checks);
        var definition = GTRegistries.MACHINES.get(new ResourceLocation("gtceu", "coke_oven"));
        if (!level.isEmptyBlock(origin)) throw new IllegalStateException("isolated fixture must be empty");
        level.setBlockAndUpdate(origin, definition.defaultBlockState());
        machine = (MultiblockControllerMachine) MetaMachine.getMachine(level, origin);
        machine.setFrontFacing(Direction.NORTH);
        budget = ((StructureSearchAccess) machine).enhancedcore$searchBackoff();
        var pattern = machine.getPattern();
        var accessor = (BlockPatternAccessor) pattern;
        var cells = accessor.gtlEnhancedcore$getBlockMatches();
        int[] center = accessor.enhanced$getCenterOffset();
        for (int z = 0; z < cells.length; z++) for (int y = 0; y < cells[z].length; y++) {
            for (int x = 0; x < cells[z][y].length; x++) {
                var predicate = cells[z][y][x];
                if (predicate.isController) continue;
                BlockPos pos = accessor.enhanced$chamberOffset(x - center[0], y - center[1], z - center[2],
                        machine.getFrontFacing(), machine.getUpwardsFacing(), false).offset(origin);
                BlockState state = candidate(predicate);
                if (!level.isEmptyBlock(pos)) throw new IllegalStateException("fixture intersects existing block " + pos);
                layout.put(pos, state);
                if (!state.isAir() && !state.hasBlockEntity() && shell == null) { shell = pos; shellState = state; }
            }
        }
        if (shell == null) throw new AssertionError("coke oven has no shell fixture");
        for (var entry : layout.entrySet()) if (!entry.getKey().equals(shell)) level.setBlockAndUpdate(entry.getKey(), entry.getValue());
        deadline = level.getGameTime() + 1200;
    }

    /** Called once per server END tick; returns true after all checks and cleanup completed. */
    public boolean tick() throws Exception {
        if (closed) return true;
        if (!level.getServer().isSameThread()) throw new IllegalStateException("server thread required");
        if (level.getGameTime() > deadline) throw new AssertionError("structure lifecycle timed out at stage " + stage);
        switch (stage) {
            case 0 -> {
                if (budget.failures() == 0) return false;
                check(!machine.isFormed(), "structure_incomplete_real_async_search_fails");
                level.setBlockAndUpdate(shell, shellState);
                check(budget.failures() == 0, "structure_shell_repair_wakes_before_backoff_deadline");
                stage++;
            }
            case 1 -> {
                if (!machine.isFormed()) return false;
                check(!machine.getMultiblockState().hasError(), "structure_async_success_confirms_on_server_and_forms");
                level.removeBlock(shell, false);
                check(!machine.isFormed(), "structure_shell_break_immediately_invalidates");
                level.setBlockAndUpdate(shell, shellState);
                stage++;
            }
            case 2 -> {
                if (!machine.isFormed()) return false;
                check(!machine.getMultiblockState().hasError(), "structure_repair_reforms_after_real_event");
                machine.onPartUnload();
                check(machine.getMultiblockState().hasError(), "structure_part_unload_immediately_marks_unavailable");
                stage++;
            }
            case 3 -> {
                if (machine.getMultiblockState().hasError()) return false;
                check(machine.isFormed(), "structure_part_unload_recovers_from_fresh_server_confirmation");
                machine.onRotated(Direction.NORTH, Direction.SOUTH);
                check(!machine.isFormed(), "structure_rotation_immediately_invalidates_real_formed_machine");
                stage++;
            }
            case 4 -> {
                if (!machine.isFormed()) return false;
                check(!machine.getMultiblockState().hasError(), "structure_rotation_reschedules_without_periodic_formed_scans");
                // Reserve a successful attempt then invalidate it before any queued continuation.
                long token = budget.begin(System.nanoTime());
                check(token >= 0 && budget.queue(token), "structure_fixture_reserves_one_pending_completion");
                machine.onPartUnload();
                check(!budget.current(token), "structure_part_unload_rejects_pending_old_generation");
                budget.completed();
                stage++;
            }
            case 5 -> {
                if (machine.getMultiblockState().hasError()) return false;
                machine.onUnload();
                check(budget.begin(Long.MAX_VALUE) == -1, "structure_real_unload_rejects_pending_search");
                var watches = ((StructureWorldAccess) MultiblockWorldSavedData.getOrCreate(level)).enhancedcore$searchWatches();
                check(watches.searchCount() == 0, "structure_real_unload_releases_watches");
                close();
                return true;
            }
            default -> throw new AssertionError(stage);
        }
        return false;
    }

    public void close() {
        if (closed) return;
        closed = true;
        if (machine != null) machine.onUnload();
        level.removeBlock(origin, false);
        for (BlockPos pos : layout.keySet()) level.removeBlock(pos, false);
    }

    private static BlockState candidate(TraceabilityPredicate predicate) {
        if (predicate.isAny() || predicate.isAir()) return Blocks.AIR.defaultBlockState();
        for (var simple : predicate.common) if (simple.candidates != null) {
            var candidates = simple.candidates.get();
            if (candidates != null && candidates.length != 0) return candidates[0].getBlockState();
        }
        for (var simple : predicate.limited) if (simple.candidates != null) {
            var candidates = simple.candidates.get();
            if (candidates != null && candidates.length != 0) return candidates[0].getBlockState();
        }
        throw new AssertionError("coke oven predicate has no fixture candidate");
    }

    private void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        checks.add(label);
    }
}
