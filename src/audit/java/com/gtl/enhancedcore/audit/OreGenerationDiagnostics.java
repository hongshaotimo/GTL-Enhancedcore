package com.gtl.enhancedcore.audit;

import com.gregtechceu.gtceu.api.data.worldgen.WorldGeneratorUtils;
import com.gregtechceu.gtceu.api.data.worldgen.generator.veins.VeinedVeinGenerator;
import com.gregtechceu.gtceu.api.data.worldgen.ores.OreGenerator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;

public final class OreGenerationDiagnostics {
    public static final boolean ENABLED = Boolean.getBoolean("gtl.enhancedcore.oreAudit");
    private static final ConcurrentHashMap<String, LongAdder> COUNTS = new ConcurrentHashMap<>();

    public static void generating(OreGenerator.VeinConfiguration config) {
        if (!ENABLED) return;
        var gen = config.data().definition().veinGenerator();
        String name = gen.getClass().getSimpleName();
        if (gen instanceof VeinedVeinGenerator veined) {
            int y = config.data().center().getY();
            name += y < veined.minYLevel || y > veined.maxYLevel ? ":outside_y" : ":inside_y";
        }
        COUNTS.computeIfAbsent(name, key -> new LongAdder()).increment();
    }

    public static String counts() { return COUNTS.toString(); }

    public static String layers(ServerLevel level) {
        var result = new StringBuilder();
        WorldGeneratorUtils.WORLD_GEN_LAYERS.forEach((name, layer) -> {
            if (!layer.isApplicableForLevel(level.dimension().location())) return;
            var target = layer.getTarget();
            result.append(name).append('=').append(target.getClass().getName()).append('[');
            for (var block : new net.minecraft.world.level.block.Block[]{
                    Blocks.AIR, Blocks.BEDROCK, Blocks.DIRT, Blocks.GRASS_BLOCK, Blocks.STONE}) {
                result.append(block).append(':').append(target.test(block.defaultBlockState(), RandomSource.create(0))).append(',');
            }
            result.append("];");
        });
        return result.toString();
    }
}
