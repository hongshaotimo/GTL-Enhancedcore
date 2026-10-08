package com.gtl.enhancedcore.mixin.gtlcore;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.Predicates;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gtl.enhancedcore.mixin.gtceu.BlockPatternAccessor;
import com.gtl.enhancedcore.mixin.gtceu.MultiblockMachineDefinitionAccessor;
import java.util.function.Supplier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 让聚合装置（gtceu:aggregation_device，gtlcore 注册）支持样板总成
 * （gtladditions:me_super_pattern_buffer 等，注册能力 IMPORT_ITEMS + IMPORT_FLUIDS）。
 *
 * 反编译实证（2026-08-29 javap gtlcore-1.2.3.2 MultiBlockMachineA.lambda$static$45）：
 * - 结构 7 个 aisle，字符位：'~' 控制器、'c' 聚变外壳(OR INPUT_ENERGY≤32)、'd' 聚变外壳MK3、
 *   'b' 聚变线圈、'e' kubejs:aggregatione_core、'a' 富集硅岩框架、
 *   'i' = Predicates.blocks(ITEM_IMPORT_BUS[0].get())（硬编码 ULV 物品输入总线方块，输入位）、
 *   'g' = GTLPredicates.diffAbilities(List.of(EXPORT_ITEMS), List.of(IMPORT_ITEMS, IMPORT_FLUIDS))
 *         （排除式：EXPORT_ITEMS 全部方块减去 IMPORT_ITEMS/IMPORT_FLUIDS 的方块 → 样板总成被排除，输出位）。
 * - 因此 'i' 位（每层左右两端，共 8 处）只认硬编码方块、'g' 位（末段一处）主动排除样板总成。
 *
 * 做法：把普通 IMPORT_ITEMS 能力 OR 进 'i' 位谓词，样板总成因注册 IMPORT_ITEMS 即可装入，
 * 原硬编码 ITEM_IMPORT_BUS[0] 与其余位置的谓词/限制全部保留，'g' 输出位不动。
 * 'i' 位判定：谓词候选中包含 ITEM_IMPORT_BUS[0] 方块且不是控制器（避免误伤其它字符位）。
 */
@Mixin(value = MultiblockMachineDefinition.class, remap = false)
public abstract class AggregationDevicePatternBufferMixin {

    @Inject(method = "setPatternFactory", at = @At("HEAD"), cancellable = true, remap = false)
    private void gtlEnhancedcore$allowPatternBufferOnAggregationDevice(
            Supplier<BlockPattern> supplier, CallbackInfo ci) {
        MultiblockMachineDefinition self = (MultiblockMachineDefinition) (Object) this;
        if (!"gtceu".equals(self.getId().getNamespace()) || !"aggregation_device".equals(self.getId().getPath())) {
            return;
        }
        Supplier<BlockPattern> original = supplier;
        ((MultiblockMachineDefinitionAccessor) (Object) self).gtlEnhancedcore$setPatternFactory(com.google.common.base.Suppliers.memoize(() -> {
            BlockPattern pattern = original.get();
            patchAggregationDevicePattern(pattern);
            return pattern;
        }));
        ci.cancel();
    }

    /**
     * 把普通 IMPORT_ITEMS 能力并入所有 'i'（物品输入）位谓词。
     * 'i' 位特征：候选方块集合恰好只含 ULV 物品输入总线方块（硬编码 Predicates.blocks(ITEM_IMPORT_BUS[0])）。
     */
    private static void patchAggregationDevicePattern(BlockPattern pattern) {
        TraceabilityPredicate[][][] matches =
                ((BlockPatternAccessor) (Object) pattern).gtlEnhancedcore$getBlockMatches();
        if (matches == null) {
            return;
        }
        TraceabilityPredicate importItems = Predicates.abilities(PartAbility.IMPORT_ITEMS);
        for (TraceabilityPredicate[][] rows : matches) {
            if (rows == null) {
                continue;
            }
            for (TraceabilityPredicate[] row : rows) {
                if (row == null) {
                    continue;
                }
                for (int col = 0; col < row.length; col++) {
                    TraceabilityPredicate predicate = row[col];
                    if (isItemImportBusOnly(predicate)) {
                        row[col] = predicate.or(importItems);
                    }
                }
            }
        }
    }

    /** 判定谓词是否为硬编码的 ULV 物品输入总线位（'i'）：非控制器且候选方块只含物品输入总线方块。 */
    private static boolean isItemImportBusOnly(TraceabilityPredicate predicate) {
        if (predicate == null || predicate.isController) {
            return false;
        }
        // 'i' 位是 Predicates.blocks(IMachineBlock...) 单一候选，不含 limited 限制
        if (!predicate.limited.isEmpty() || predicate.common.size() != 1) {
            return false;
        }
        var simple = predicate.common.get(0);
        if (simple.candidates == null) {
            return false;
        }
        var candidates = simple.candidates.get();
        if (candidates == null || candidates.length != 1) {
            return false;
        }
        var state = candidates[0].getBlockState();
        if (state == null) {
            return false;
        }
        return PartAbility.IMPORT_ITEMS.isApplicable(state.getBlock());
    }
}
