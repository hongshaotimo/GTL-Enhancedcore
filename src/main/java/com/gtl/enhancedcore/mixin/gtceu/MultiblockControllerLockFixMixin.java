package com.gtl.enhancedcore.mixin.gtceu;

import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import org.gtlcore.gtlcore.api.pattern.WorldPatternTiming;
import org.spongepowered.asm.mixin.Mixin;

/**
 * GTL adds concrete methods that hide IMultiController's default lock guards.
 * Merge before GTL's priority-1000 implementation while retaining its timing hooks.
 */
@Mixin(value = MultiblockControllerMachine.class, remap = false, priority = 1100)
public abstract class MultiblockControllerLockFixMixin implements IMultiController {
    @Override
    public boolean checkPatternWithLock() {
        try (var timing = WorldPatternTiming.beginCheck(this, "lock")) {
            var lock = getPatternLock();
            long start = timing == null ? 0 : System.nanoTime();
            lock.lock();
            boolean matched;
            try {
                if (timing != null) timing.lockResult(start, true);
                matched = checkPattern();
            } finally {
                lock.unlock();
            }
            if (timing != null) timing.result(matched);
            return matched;
        }
    }

    @Override
    public boolean checkPatternWithTryLock() {
        try (var timing = WorldPatternTiming.beginCheck(this, "try_lock")) {
            var lock = getPatternLock();
            long start = timing == null ? 0 : System.nanoTime();
            boolean acquired = lock.tryLock();
            boolean matched = false;
            if (acquired) {
                try {
                    if (timing != null) timing.lockResult(start, true);
                    matched = checkPattern();
                } finally {
                    lock.unlock();
                }
            } else if (timing != null) {
                timing.lockResult(start, false);
            }
            if (timing != null) timing.result(matched);
            return matched;
        }
    }
}
