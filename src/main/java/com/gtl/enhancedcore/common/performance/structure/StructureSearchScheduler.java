package com.gtl.enhancedcore.common.performance.structure;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.MultiblockState;
import com.gregtechceu.gtceu.api.pattern.MultiblockWorldSavedData;
import com.gtl.enhancedcore.mixin.gtceu.BlockPatternAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

/** Failed-search scheduling. Healthy formed controllers continue to use upstream immediate events. */
public final class StructureSearchScheduler {
    private static final ThreadLocal<Attempt> ATTEMPT = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> CONFIRMING = ThreadLocal.withInitial(() -> false);

    private StructureSearchScheduler() {}

    public static void search(IMultiController controller, long period) {
        if ((controller.self().getHolder().getOffset() + period) % 4 != 0
                || !(controller.self().getLevel() instanceof ServerLevel level)) return;
        StructureSearchBackoff budget = ((StructureSearchAccess) controller).enhancedcore$searchBackoff();
        if (!controller.getMultiblockState().hasError() && controller.isFormed() && !budget.requiresFormation()) return;
        long token = budget.begin(System.nanoTime());
        if (token < 0) return;
        var lock = controller.getPatternLock();
        if (!lock.tryLock()) {
            budget.lockBusy();
            return;
        }
        boolean matched;
        try {
            Attempt attempt = new Attempt(controller, budget, watches(level), token);
            ATTEMPT.set(attempt);
            // The current GTCEu lock is reentrant. Reuse the existing timing and finally guards.
            matched = controller.checkPatternWithTryLock();
        } finally {
            ATTEMPT.remove();
            lock.unlock();
            budget.lockBusy();
        }
        if (!matched) {
            budget.failed(System.nanoTime(), token);
            return;
        }
        if (budget.queue(token)) level.getServer().execute(() -> confirm(controller, level, budget, token));
    }

    private static void confirm(IMultiController controller, ServerLevel level,
            StructureSearchBackoff budget, long token) {
        var lock = controller.getPatternLock();
        lock.lock();
        try {
            if (!budget.current(token) || controller.self().getLevel() != level
                    || controller.self().isInValid() || !level.isLoaded(controller.self().getPos())
                    || MetaMachine.getMachine(level, controller.self().getPos()) != controller.self()) return;
            // Part unload keeps isFormed while dropping energy and recipe handlers. The async
            // matcher already clears its error on success; only the pending token owns recovery.
            // A part can be claimed by another controller without a block-state event. Confirm on
            // the server thread before binding capabilities; never form from an async-only result.
            CONFIRMING.set(true);
            if (controller.checkPatternWithLock()) {
                controller.self().setFlipped(controller.getMultiblockState().isNeededFlip());
                controller.onStructureFormed();
                MultiblockWorldSavedData saved = MultiblockWorldSavedData.getOrCreate(level);
                saved.addMapping(controller.getMultiblockState());
                saved.removeAsyncLogic(controller);
            } else {
                budget.failed(System.nanoTime(), token);
            }
        } finally {
            CONFIRMING.remove();
            budget.completed();
            lock.unlock();
        }
    }

    public static void observe(BlockPattern pattern, MultiblockState state, BlockPos origin,
            Direction facing, Direction upwards, boolean flipped) {
        if (!(state.world instanceof ServerLevel level)) return;
        Attempt attempt = ATTEMPT.get();
        if (attempt == null) {
            if (!CONFIRMING.get() && level.getServer().isSameThread()
                    && state.lastController instanceof StructureSearchAccess access
                    && state.lastController.getMultiblockState() == state) access.enhancedcore$searchBackoff().wake();
            return;
        }
        if (attempt.controller.getMultiblockState() != state) return;
        var accessor = (BlockPatternAccessor) pattern;
        var cells = accessor.gtlEnhancedcore$getBlockMatches();
        if (cells.length == 0 || cells[0].length == 0 || cells[0][0].length == 0) return;
        int[] center = accessor.enhanced$getCenterOffset();
        int depth = 0;
        for (int[] repetition : pattern.aisleRepetitions) depth += repetition[1];
        int[] xs = {-center[0], cells[0][0].length - center[0] - 1};
        int[] ys = {-center[1], cells[0].length - center[1] - 1};
        int[] zs = {-center[4], -center[3] + depth - 1};
        StructureSearchWatches.Bounds bounds = null;
        for (int x : xs) for (int y : ys) for (int z : zs) {
            BlockPos pos = accessor.enhanced$chamberOffset(x, y, z, facing, upwards, flipped).offset(origin);
            var point = new StructureSearchWatches.Bounds(pos.getX(), pos.getY(), pos.getZ(),
                    pos.getX(), pos.getY(), pos.getZ());
            bounds = bounds == null ? point : bounds.union(point);
        }
        attempt.bounds = attempt.bounds == null ? bounds : attempt.bounds.union(bounds);
        attempt.watches.watch(attempt.budget, attempt.bounds, attempt.token);
    }

    public static StructureSearchWatches watches(ServerLevel level) {
        return ((StructureWorldAccess) MultiblockWorldSavedData.getOrCreate(level)).enhancedcore$searchWatches();
    }

    public static void wake(IMultiController controller) {
        ((StructureSearchAccess) controller).enhancedcore$searchBackoff().wake();
    }

    private static final class Attempt {
        private final IMultiController controller;
        private final StructureSearchBackoff budget;
        private final StructureSearchWatches watches;
        private final long token;
        private StructureSearchWatches.Bounds bounds;
        private Attempt(IMultiController controller, StructureSearchBackoff budget, StructureSearchWatches watches, long token) {
            this.controller = controller;
            this.budget = budget;
            this.watches = watches;
            this.token = token;
        }
    }
}
