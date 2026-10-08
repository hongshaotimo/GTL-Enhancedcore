package com.gtl.enhancedcore.common.structure;

import com.gregtechceu.gtceu.api.GTCEuAPI;
import com.gregtechceu.gtceu.api.block.ICoilType;
import com.gregtechceu.gtceu.api.pattern.Predicates;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.pattern.error.PatternStringError;
import com.gregtechceu.gtceu.common.block.CoilBlock;
import com.lowdragmc.lowdraglib.utils.BlockInfo;
import java.util.Comparator;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;

/** A minimum temperature, not a fixed block; native matching owns coil uniformity and CoilType. */
public final class DistorterCoils {
    private static final String ERROR = "gtl_enhancedcore.structure.distorter_coils";

    private DistorterCoils() {}

    public static TraceabilityPredicate create() {
        int minimum = CoilBlock.CoilType.TRITANIUM.getCoilTemperature();
        var entries = GTCEuAPI.HEATING_COILS.entrySet().stream()
                .filter(entry -> entry.getKey().getCoilTemperature() >= minimum)
                .sorted(Comparator.comparingInt((Map.Entry<ICoilType, Supplier<CoilBlock>> entry) ->
                        entry.getKey().getCoilTemperature()).thenComparingInt(entry -> entry.getKey().getTier()))
                .toList();
        if (entries.isEmpty()) throw new IllegalStateException("No Tritanium-or-hotter heating coils registered");
        Block[] blocks = entries.stream().map(entry -> entry.getValue().get()).toArray(Block[]::new);
        var allowed = Predicates.blocks(blocks);
        var nativeCoils = Predicates.heatingCoils();
        return new TraceabilityPredicate(state -> {
            if (!allowed.test(state)) {
                state.setError(new PatternStringError(ERROR));
                return false;
            }
            return nativeCoils.test(state);
        }, () -> java.util.Arrays.stream(blocks).map(BlockInfo::fromBlock).toArray(BlockInfo[]::new))
                .addTooltips(Component.translatable(ERROR));
    }
}
