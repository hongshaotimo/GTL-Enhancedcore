package com.gtl.enhancedcore.mixin.gtlcore;

import org.gtlcore.gtlcore.common.machine.multiblock.part.maintenance.AutoConfigurationMaintenanceHatchPartMachine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 暴露 gtlcore 可配置维护仓的私有倍率字段 durationMultiplier。
 *
 * 用途：AutoConfigMaintenanceHatchMixin 需要绕过 GravityCleaningConfigurationMaintenancePartMachine
 * 对 setDurationMultiplier 的 isConfig 门控（isConfig == false 时整个丢弃写入），直接写基类字段。
 * 该字段声明在基类 AutoConfigurationMaintenanceHatchPartMachine 上，@Shadow 只能解析目标类自身字段
 * （规则 19），跨类访问必须走 @Accessor。
 */
@Mixin(value = AutoConfigurationMaintenanceHatchPartMachine.class, remap = false)
public interface AutoConfigMaintenanceHatchAccessor {

    @Accessor(value = "durationMultiplier", remap = false)
    void gtlEnhancedcore$setDurationMultiplierRaw(float value);

    @Accessor(value = "durationMultiplier", remap = false)
    float gtlEnhancedcore$getDurationMultiplierRaw();
}
