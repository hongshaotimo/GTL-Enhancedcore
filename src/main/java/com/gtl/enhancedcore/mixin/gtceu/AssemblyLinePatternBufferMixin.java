package com.gtl.enhancedcore.mixin.gtceu;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.Predicates;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import java.util.function.Supplier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 让 GTCEu 装配线（gtceu:assembly_line）与电路装配线（gtceu:circuit_assembly_line）
 * 支持样板总成（gtladditions:me_super_pattern_buffer 等），
 * 参考 gtlcore 进阶装配线（advanced_assembly_line）的实现方式，不改动任何原有仓室位置与功能。
 *
 * 反编译实证（2026-08-15）：
 * - 进阶装配线与普通装配线 aisles/布局完全一致；差异只在 F 位谓词：进阶 F =
 *   钢外壳 OR IMPORT_FLUIDS（普通能力）OR PARALLEL_HATCH，普通 F =
 *   钢外壳 OR IMPORT_FLUIDS_1X/4X/9X。
 * - 超级样板总成注册能力 = IMPORT_ITEMS + IMPORT_FLUIDS，因此进阶装配线能在 F 位（底部流体仓行）
 *   装样板总成，普通装配线装不上。
 * - 电路装配线（gtlcore 注册，gtceu 命名空间）：中段 aisle "bbb/cec/bdb" 的 row2 col0/col2 为
 *   物品输入位 d，末段 aisle "bbb/bab/bgb" 的 row2 col0/col2 为流体输入位 g；
 *   BlockPattern.blockMatches 不展开重复 aisle（aisleRepetitions 记录次数），
 *   因此 matches[1]=中段（OR IMPORT_ITEMS）、matches[2]=末段（OR IMPORT_FLUIDS）即可让样板总成装上。
 * - Predicates.abilities 在模式构建时固化成方块数组，模式由 patternFactory 惰性构建，
 *   因此在 setPatternFactory 时用包装器，构建后把普通 IMPORT_FLUIDS 能力 OR 进 F 位谓词
 *   （所有 aisle 的 row0 col0/col2），与原 F 位能力全部共存。
 */
@Mixin(value = MultiblockMachineDefinition.class, remap = false)
public abstract class AssemblyLinePatternBufferMixin {

    @Inject(method = "setPatternFactory", at = @At("HEAD"), cancellable = true, remap = false)
    private void gtlEnhancedcore$allowPatternBufferOnAssemblyLine(
            Supplier<BlockPattern> supplier, CallbackInfo ci) {
        MultiblockMachineDefinition self = (MultiblockMachineDefinition) (Object) this;
        if (!"gtceu".equals(self.getId().getNamespace())) return;
        String path = self.getId().getPath();
        boolean isAssemblyLine = "assembly_line".equals(path);
        boolean isCircuitAssemblyLine = "circuit_assembly_line".equals(path);
        if (!isAssemblyLine && !isCircuitAssemblyLine) {
            return;
        }
        Supplier<BlockPattern> original = supplier;
        ((MultiblockMachineDefinitionAccessor) (Object) self).gtlEnhancedcore$setPatternFactory(com.google.common.base.Suppliers.memoize(() -> {
            BlockPattern pattern = original.get();
            if (isAssemblyLine) {
                patchAssemblyLinePattern(pattern);
            } else {
                patchCircuitAssemblyLinePattern(pattern);
            }
            return pattern;
        }));
        ci.cancel();
    }

    /** 把普通 IMPORT_FLUIDS 能力并入装配线 F 位（所有 aisle 的 row0 col0/col2）谓词，原 F 位功能保留。 */
    private static void patchAssemblyLinePattern(BlockPattern pattern) {
        TraceabilityPredicate[][][] matches =
                ((BlockPatternAccessor) (Object) pattern).gtlEnhancedcore$getBlockMatches();
        if (matches == null) {
            return;
        }
        TraceabilityPredicate importFluids = Predicates.abilities(PartAbility.IMPORT_FLUIDS);
        for (int aisle = 0; aisle < matches.length; aisle++) {
            TraceabilityPredicate[][] rows = matches[aisle];
            if (rows == null || rows.length <= 0) {
                continue;
            }
            TraceabilityPredicate[] row0 = rows[0];
            if (row0 == null || row0.length < 3) {
                continue;
            }
            row0[0] = row0[0].or(importFluids);
            row0[2] = row0[2].or(importFluids);
        }
    }

    /** 电路装配线：中段物品输入位并入普通 IMPORT_ITEMS，末段流体输入位并入普通 IMPORT_FLUIDS。 */
    private static void patchCircuitAssemblyLinePattern(BlockPattern pattern) {
        TraceabilityPredicate[][][] matches =
                ((BlockPatternAccessor) (Object) pattern).gtlEnhancedcore$getBlockMatches();
        if (matches == null || matches.length < 3) {
            return;
        }
        orAbilityIntoBottomCorners(matches[1], Predicates.abilities(PartAbility.IMPORT_ITEMS));
        orAbilityIntoBottomCorners(matches[2], Predicates.abilities(PartAbility.IMPORT_FLUIDS));
    }

    /** 把能力谓词 OR 进该 aisle 最底行两端（row2 col0/col2），原谓词与限制保留。 */
    private static void orAbilityIntoBottomCorners(TraceabilityPredicate[][] rows, TraceabilityPredicate ability) {
        if (rows == null || rows.length < 3) {
            return;
        }
        TraceabilityPredicate[] bottom = rows[2];
        if (bottom == null || bottom.length < 3) {
            return;
        }
        bottom[0] = bottom[0].or(ability);
        bottom[2] = bottom[2].or(ability);
    }
}
