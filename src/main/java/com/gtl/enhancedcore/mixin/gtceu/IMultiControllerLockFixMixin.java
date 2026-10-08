package com.gtl.enhancedcore.mixin.gtceu;

import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.concurrent.locks.Lock;

/**
 * GTCEu 1.4.4 多方块结构检查锁泄漏修复。
 *
 * @author GTL-Enhancedcore
 * @reason 原版 IMultiController.checkPatternWithTryLock / checkPatternWithLock 未用 try/finally，
 *         结构检查抛异常（如 gtlcore 的 EmptySet 强转错误）时锁永久泄漏，异步线程持有锁回到线程池，
 *         服务端线程随后等待同一把锁导致整个游戏死锁（ModernFix watchdog 线程转储实证）。
 *         此处改为 finally 中无条件解锁。目标是接口，故本 mixin 声明为接口类型。
 */
@Mixin(value = IMultiController.class, remap = false)
public interface IMultiControllerLockFixMixin {

    @Shadow(remap = false)
    Lock getPatternLock();

    @Shadow(remap = false)
    boolean checkPattern();

    /**
     * @author GTL-Enhancedcore
     * @reason 修复结构检查异常导致锁泄漏的问题
     */
    @Overwrite(remap = false)
    default boolean checkPatternWithTryLock() {
        Lock lock = this.getPatternLock();
        if (lock.tryLock()) {
            try {
                return this.checkPattern();
            } finally {
                lock.unlock();
            }
        }
        return false;
    }

    /**
     * @author GTL-Enhancedcore
     * @reason 修复结构检查异常导致锁泄漏的问题
     */
    @Overwrite(remap = false)
    default boolean checkPatternWithLock() {
        Lock lock = this.getPatternLock();
        lock.lock();
        try {
            return this.checkPattern();
        } finally {
            lock.unlock();
        }
    }
}
