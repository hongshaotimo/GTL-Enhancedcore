package com.gtl.enhancedcore.common.registration;

import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtl.enhancedcore.common.machine.QftMutableMachine;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;

/**
 * 把第三方机器接入 GTLAdditions 官方「可变多配方」体系（用户 2026-10-06 指定 qft 用 ADD 的跨配方并行 + 512 线程）。
 *
 * <p>实现方式与 ADD 自身的 {@code MutableMultiBlockModify.setOtherMutable()} 一致：调用 GTCEu 公开 API
 * {@code MachineDefinition.setMachineSupplier(Function<IMachineBlockEntity, MetaMachine>)} 替换机器工厂。
 * 不改 GTLCore 源码、不替换注册 ID、不动结构。
 *
 * <p>执行时机：必须在 GTCEu 机器注册事件回调内调用（此时定义已构造完成且可安全写 supplier）；
 * 类字段的延迟读取同样放在回调内，避免 mod 构造期触发第三方静态初始化。
 */
public final class AddMutableMachineRegistration {

    private static boolean applied;

    private AddMutableMachineRegistration() {}

    /** 在 {@code bus.addGenericListener(MachineDefinition.class, ...)} 回调内调用一次。 */
    public static void init() {
        if (applied) return;
        applied = true;
        applyQft();
    }

    private static void applyQft() {
        MultiblockMachineDefinition qft = org.gtlcore.gtlcore.common.data.machines.MultiBlockMachineA.QFT;
        if (qft == null) {
            GTLEnhancedcore.LOGGER.error("qft definition missing; ADD mutable 接入跳过");
            return;
        }
        qft.setMachineSupplier(holder -> (MetaMachine) new QftMutableMachine(holder));
        GTLEnhancedcore.LOGGER.info("qft 已接入 GTLAdditions 可变多配方逻辑（跨配方并行，线程 {}）",
                QftMutableMachine.CROSS_RECIPE_THREADS);
    }
}
