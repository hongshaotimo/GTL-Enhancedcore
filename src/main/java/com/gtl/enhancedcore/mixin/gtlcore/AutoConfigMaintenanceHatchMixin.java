package com.gtl.enhancedcore.mixin.gtlcore;

import org.gtlcore.gtlcore.common.machine.multiblock.part.maintenance.AutoConfigurationMaintenanceHatchPartMachine;
import org.gtlcore.gtlcore.common.machine.multiblock.part.maintenance.CleaningConfigurationMaintenanceHatchPartMachine;
import org.gtlcore.gtlcore.common.machine.multiblock.part.maintenance.GravityCleaningConfigurationMaintenancePartMachine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 可配置维护仓自动 5 倍速（durationMultiplier = 0.2f）。
 * 覆盖 gtlcore 8 种可配置维护仓：
 * auto / cleaning / sterile_cleaning / law_cleaning /
 * gravity / cleaning_gravity / sterile_cleaning_gravity / law_cleaning_gravity。
 *
 * 注入点固定在<b>子类构造 TAIL</b>，保证所有字段初始化已完成后再写入倍率。
 * 写入走 AutoConfigMaintenanceHatchAccessor 直接改基类私有字段 durationMultiplier，
 * <b>不调用</b>虚方法 setDurationMultiplier —— 见下方 2026-09-11 根因说明。
 *
 * 2026-09-08 记录：不再注入 addedToController。gtlcore 的 durationMultiplier 是 @Persisted 字段
 * （javap 实证 RuntimeVisibleAnnotations），重进存档时序为「构造 → NBT 恢复玩家设置 → 结构成型
 * addedToController」；原 addedToController TAIL 无条件 setDurationMultiplier(0.2f) 会把玩家保存的
 * 0.15/1.0/1.2 覆盖回 0.2。删除后玩家设置跨存档保留，新放置默认 0.2 由构造注入保证。
 *
 * 2026-09-11 修复「大量放置场景下数值不再默认 0.2」（javap 字节码实证根因，规则 21）：
 * 原实现调用虚方法 self.setDurationMultiplier(0.2f)，存在两处叠加失效：
 * 1) GravityCleaningConfigurationMaintenancePartMachine 覆写了该方法并在 isConfig == false 时丢弃写入，
 *    getDurationMultiplier() 亦直接返回 1.0f：
 *      setDurationMultiplier(F)：getfield isConfig; ifeq 12; aload_0; fload_1; invokespecial 基类.setDurationMultiplier; return
 *      getDurationMultiplier()：getfield isConfig; ifeq 14; invokespecial 基类.getDurationMultiplier; fconst_1; freturn
 * 2) 构造期调用时序问题（更致命）：基类构造 TAIL 触发注入时，子类字段初始化尚未执行，
 *    GravityCleaningConfigurationMaintenancePartMachine(I, boolean) 字节码顺序为
 *      invokespecial AutoConfigurationMaintenanceHatchPartMachine."<init>"(I)   ← 注入在此返回处触发
 *      putfield gravity:I
 *      putfield isConfig:Z (true，字段默认值)
 *      putfield isConfig:Z (false，参数值)
 *    即注入瞬间 isConfig 仍是 JVM 默认 false，虚分发到子类 setter 后被 isConfig 判定直接丢弃 → 0.2 静默失效。
 *
 * 修复：注入点改到三个子类各自的构造 TAIL（isConfig 已就绪），写入改走 @Accessor 直接改基类字段，
 * 彻底绕开 isConfig 门控。gravity_hatch（注册时 isConfig=false）同样获得默认 0.2。
 */
@Mixin(value = {
        AutoConfigurationMaintenanceHatchPartMachine.class,
        CleaningConfigurationMaintenanceHatchPartMachine.class,
        GravityCleaningConfigurationMaintenancePartMachine.class
}, remap = false)
public abstract class AutoConfigMaintenanceHatchMixin {

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void gtlEnhancedcore$autoSetDurationOnPlace(CallbackInfo ci) {
        // Mth.clamp(0.2f, 0.2f, 1.2f) == 0.2f：等价于基类 setDurationMultiplier(0.2f) 的结果，
        // 但不经过子类 isConfig 门控，8 种维护仓一律默认 0.2。
        ((AutoConfigMaintenanceHatchAccessor) (Object) this).gtlEnhancedcore$setDurationMultiplierRaw(0.2f);
    }
}
