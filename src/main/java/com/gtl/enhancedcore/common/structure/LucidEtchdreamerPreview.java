package com.gtl.enhancedcore.common.structure;

import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.FactoryBlockPattern;
import com.gregtechceu.gtceu.api.pattern.Predicates;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.pattern.predicates.SimplePredicate;
import com.gtl.enhancedcore.mixin.gtceu.BlockPatternAccessor;
import com.lowdragmc.lowdraglib.utils.BlockInfo;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/** Builds the reviewed schematic preview with null cells for its unconstrained volume. */
public final class LucidEtchdreamerPreview {
    private static final String RESOURCE = "/data/gtl_enhancedcore/structures/gtl/lucid_etchdreamer.pattern.gz";

    private LucidEtchdreamerPreview() {}

    /** Preview coordinates after the LEFT/UP/FRONT transform. */
    public static net.minecraft.core.BlockPos controllerPosition() {
        int[] size = GtlMegastructurePatterns.lucidCoordinates("size");
        int[] core = GtlMegastructurePatterns.lucidCoordinates("controllerPosition");
        return new net.minecraft.core.BlockPos(size[0] - 1 - core[0], core[1], size[2] - 1 - core[2]);
    }

    public static BlockInfo[][][] create(BlockPattern pattern) {
        int[] size = GtlMegastructurePatterns.lucidCoordinates("size");
        int[] core = GtlMegastructurePatterns.lucidCoordinates("controllerPosition");
        int[] origin = GtlMegastructurePatterns.lucidPanel();
        TraceabilityPredicate[][][] matches = ((BlockPatternAccessor) pattern).gtlEnhancedcore$getBlockMatches();
        if (matches.length != size[2] || matches[0].length != size[1] || matches[0][0].length != size[0]) {
            throw new IllegalStateException("Unexpected lucid_etchdreamer preview dimensions");
        }

        BlockInfo[][][] result = new BlockInfo[size[0]][size[1]][size[2]];
        // GTLCore's preview widget skips null cells. BlockInfo.EMPTY is non-null and
        // would turn millions of unconstrained cells into preview positions.
        Map<Character, BlockInfo> choices = new HashMap<>();
        int[] aisle = {0};
        StructurePatterns.data(RESOURCE).forEachAisle(rows -> {
            int z = aisle[0]++;
            for (int y = 0; y < rows.length; y++) {
                String row = rows[y];
                for (int x = 0; x < row.length(); x++) {
                    char symbol = row.charAt(x);
                    if (symbol == ' ' || symbol == 'C' || symbol == 'X') continue;
                    BlockInfo info = choices.get(symbol);
                    if (info == null) {
                        info = firstCandidate(matches[z][y][x], symbol);
                        choices.put(symbol, info);
                    }
                    // Loaded patterns use LEFT / UP / FRONT. At NORTH, X and Z run negative.
                    result[size[0] - 1 - x][y][size[2] - 1 - z] = info;
                }
            }
        });

        // Reuse GTCEu's quota-aware preview selection on the actual panel geometry.
        BlockInfo[][][] panel = previewPanel(matches, origin);
        BlockInfo controller = firstCandidate(matches[core[2]][core[1]][core[0]], 'C');
        int controllerX = origin[3] - 1 - (core[0] - origin[0]);
        int controllerY = core[1] - origin[1];
        int controllerLayer = -1;
        for (int layer = 0; layer < panel[controllerX][controllerY].length; layer++) {
            BlockInfo candidate = panel[controllerX][controllerY][layer];
            if (candidate != null && candidate.hasBlockEntity()
                    && candidate.getBlockState().is(controller.getBlockState().getBlock())) {
                controllerLayer = layer;
                break;
            }
        }
        if (controllerLayer < 0) throw new IllegalStateException("Missing lucid_etchdreamer preview controller layer");
        for (int x = 0; x < origin[3]; x++) {
            for (int y = 0; y < origin[4]; y++) {
                BlockInfo info = panel[x][y][controllerLayer];
                if (x == controllerX && y == controllerY) info = controller;
                if (info != null && !info.getBlockState().isAir())
                    result[size[0] - origin[0] - origin[3] + x][origin[1] + y][size[2] - 1 - origin[2]] = info;
            }
        }
        return result;
    }

    private static BlockInfo[][][] previewPanel(TraceabilityPredicate[][][] matches, int[] origin) {
        String resource = "/data/gtl_enhancedcore/structures/gtl/lucid_etchdreamer_preview_panel.pattern.gz";
        FactoryBlockPattern builder = StructurePatterns.start(resource).where(' ', Predicates.any());
        var bound = new HashSet<Character>();
        int[] aisle = {0};
        StructurePatterns.data(resource).forEachAisle(rows -> {
            int z = origin[2] - 1 + aisle[0]++;
            for (int y = 0; y < rows.length; y++) for (int x = 0; x < rows[y].length(); x++) {
                char symbol = rows[y].charAt(x);
                if (symbol != ' ' && bound.add(symbol))
                    builder.where(symbol, matches[z][origin[1] + y][origin[0] + x]);
            }
        });
        return builder.build().getPreview(new int[]{1, 1, 1});
    }

    private static BlockInfo firstCandidate(TraceabilityPredicate predicate, char symbol) {
        if (symbol == 'P') {
            return GtlMegastructurePatterns.lucidPreviewCoil();
        }
        for (SimplePredicate simple : predicate.limited) {
            if (simple.candidates == null) continue;
            BlockInfo[] infos = simple.candidates.get();
            if (infos != null && infos.length > 0 && infos[0] != null) return infos[0];
        }
        for (SimplePredicate simple : predicate.common) {
            if (simple.candidates == null) continue;
            BlockInfo[] infos = simple.candidates.get();
            if (infos != null && infos.length > 0 && infos[0] != null) return infos[0];
        }
        throw new IllegalStateException("No lucid_etchdreamer preview candidate for " + symbol);
    }
}
