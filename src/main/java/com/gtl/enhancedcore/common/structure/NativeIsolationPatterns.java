package com.gtl.enhancedcore.common.structure;

import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.pattern.Predicates;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gtl.enhancedcore.common.recipe.iv.IvMachineScope;
import com.gtl.enhancedcore.common.recipe.iv.IvNativeHatches;
import com.gtl.enhancedcore.mixin.gtceu.BlockPatternAccessor;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceLocation;

/**
 * 给「没有项目定制几何」的原生机器补上 IV 隔离材料仓，不改共享的上游结构定义。
 *
 * <p>判定条件派生自两个既有事实，不维护第二份名单：
 * <ol>
 *   <li>{@link IvMachineScope#nativeTarget} —— 是 IV 隔离目标；</li>
 *   <li>不是 {@link GtlMegastructurePatterns#targets} —— 没有项目定制几何。</li>
 * </ol>
 * 已做结构替换的机器由 {@code GtlMegastructurePatterns.replace()} 顺带换仓室，不走这里。
 * 覆盖 {@code gtceu:huge_incubator}、{@code gtceu:gravitation_shockburst}、
 * {@code gtladditions:inferno_cleft_smelting_vault}与伏羲八卦锻天炉。
 */
public final class NativeIsolationPatterns {
    private NativeIsolationPatterns() {}

    public static boolean targets(ResourceLocation id) {
        return id != null && IvMachineScope.nativeTarget(id) && !GtlMegastructurePatterns.targets(id);
    }

    public static BlockPattern restrict(ResourceLocation id, BlockPattern original) {
        var access = (BlockPatternAccessor) original;
        var matches = access.gtlEnhancedcore$getBlockMatches().clone();
        var hatches = new IvNativeHatches();
        // This path also owns shockburst after its custom geometry was withdrawn.
        // One shared predicate makes the optional maximum global across every port position.
        var maintenance = MegastructureMaintenancePolicy.matches(id.getNamespace(), id.getPath())
                ? Predicates.abilities(PartAbility.MAINTENANCE).setMaxGlobalLimited(1).setPreviewCount(1) : null;
        Map<TraceabilityPredicate, TraceabilityPredicate> rules = new IdentityHashMap<>();
        for (int z = 0; z < matches.length; z++) {
            matches[z] = matches[z].clone();
            for (int y = 0; y < matches[z].length; y++) {
                matches[z][y] = matches[z][y].clone();
                for (int x = 0; x < matches[z][y].length; x++) {
                    matches[z][y][x] = rules.computeIfAbsent(matches[z][y][x], source -> {
                        var restricted = hatches.restrict(source);
                        // Only original material ports gain maintenance; controllers and solid body stay intact.
                        return maintenance != null && restricted != source && !source.isController
                                && !hasMaintenance(source)
                                ? restricted.or(maintenance) : restricted;
                    });
                }
            }
        }
        NativeHiddenStructureCells.apply(id, original, matches);
        return new BlockPattern(matches, original.structureDir, original.aisleRepetitions,
                access.enhanced$getCenterOffset().clone());
    }

    /** Addon factory wrappers may revisit this pattern; do not create a second independent quota. */
    private static boolean hasMaintenance(TraceabilityPredicate source) {
        return Stream.concat(source.common.stream(), source.limited.stream()).anyMatch(part -> {
            if (part.candidates == null) return false;
            var candidates = part.candidates.get();
            if (candidates == null) return false;
            for (var candidate : candidates) {
                if (candidate != null && PartAbility.MAINTENANCE.isApplicable(candidate.getBlockState().getBlock()))
                    return true;
            }
            return false;
        });
    }
}
