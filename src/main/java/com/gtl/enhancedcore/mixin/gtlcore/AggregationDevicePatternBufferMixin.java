package com.gtl.enhancedcore.mixin.gtlcore;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.Predicates;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gtl.enhancedcore.mixin.gtceu.BlockPatternAccessor;
import com.gtl.enhancedcore.mixin.gtceu.MultiblockMachineDefinitionAccessor;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.function.Supplier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 让聚合装置（gtceu:aggregation_device，gtlcore 注册）支持样板总成
 * （gtladditions:me_super_pattern_buffer 等，注册能力 IMPORT_ITEMS + IMPORT_FLUIDS）。
 *
 * 依赖核验：GTLCore 1.2.3.3-fix3 MultiBlockMachineA 的聚合装置结构。
 * - 结构 7 个 aisle，字符位：'~' 控制器、'c' 聚变外壳(OR INPUT_ENERGY≤32)、'd' 聚变外壳MK3、
 *   'b' 聚变线圈、'e' kubejs:aggregatione_core、'a' 富集硅岩框架、
 *   'i' = 普通 ULV 物品输入总线 OR 巨型 ULV 物品输入总线（输入位）、
 *   'g' = GTLPredicates.diffAbilities(List.of(EXPORT_ITEMS), List.of(IMPORT_ITEMS, IMPORT_FLUIDS))
 *         （排除式：EXPORT_ITEMS 全部方块减去 IMPORT_ITEMS/IMPORT_FLUIDS 的方块 → 样板总成被排除，输出位）。
 * - 因此 'i' 位（每层左右两端，共 8 处）只认硬编码方块、'g' 位（末段一处）主动排除样板总成。
 *
 * 做法：把普通 IMPORT_ITEMS 能力 OR 进 'i' 位谓词，样板总成因注册 IMPORT_ITEMS 即可装入，
 * 原硬编码 ITEM_IMPORT_BUS[0] 与其余位置的谓词/限制全部保留，'g' 输出位不动。
 * 'i' 位判定：无计数限制、非控制器，所有候选分支均为单个物品输入总线方块。
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
            return patchAggregationDevicePattern(pattern);
        }));
        ci.cancel();
    }

    /**
     * 把普通 IMPORT_ITEMS 能力并入所有 'i'（物品输入）位谓词。
     * 复制图案数组；复用原 SimplePredicate 身份，保留全局计数与原输入/输出规则。
     */
    private static BlockPattern patchAggregationDevicePattern(BlockPattern pattern) {
        var access = (BlockPatternAccessor) (Object) pattern;
        var source = access.gtlEnhancedcore$getBlockMatches();
        var matches = Arrays.copyOf(source, source.length);
        TraceabilityPredicate importItems = Predicates.abilities(PartAbility.IMPORT_ITEMS);
        var replacements = new IdentityHashMap<TraceabilityPredicate, TraceabilityPredicate>();
        for (int z = 0; z < matches.length; z++) {
            matches[z] = Arrays.copyOf(source[z], source[z].length);
            for (int y = 0; y < matches[z].length; y++) {
                var row = matches[z][y] = Arrays.copyOf(source[z][y], source[z][y].length);
                for (int col = 0; col < row.length; col++) {
                    var predicate = row[col];
                    if (isItemImportBusOnly(predicate)) {
                        row[col] = replacements.computeIfAbsent(predicate,
                                original -> new TraceabilityPredicate(original).or(importItems));
                    }
                }
            }
        }
        var center = access.enhanced$getCenterOffset();
        return new BlockPattern(matches, pattern.structureDir, pattern.aisleRepetitions,
                Arrays.copyOf(center, center.length));
    }

    /** 兼容上游普通总线与巨型总线的 OR；混合外壳/输出位与有限额仓位不开放。 */
    private static boolean isItemImportBusOnly(TraceabilityPredicate predicate) {
        if (predicate == null || predicate.isController) {
            return false;
        }
        if (!predicate.limited.isEmpty() || predicate.common.isEmpty()) {
            return false;
        }
        for (var simple : predicate.common) {
            if (simple.candidates == null) return false;
            var candidates = simple.candidates.get();
            if (candidates == null || candidates.length != 1) return false;
            var state = candidates[0].getBlockState();
            if (state == null || !PartAbility.IMPORT_ITEMS.isApplicable(state.getBlock())) return false;
        }
        return true;
    }
}
