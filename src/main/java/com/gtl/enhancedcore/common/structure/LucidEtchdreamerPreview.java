package com.gtl.enhancedcore.common.structure;

import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.FactoryBlockPattern;
import com.gregtechceu.gtceu.api.pattern.Predicates;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.pattern.predicates.SimplePredicate;
import com.gtl.enhancedcore.mixin.gtceu.BlockPatternAccessor;
import com.lowdragmc.lowdraglib.utils.BlockInfo;
import java.util.HashMap;
import java.util.Map;

/** Builds the 233-cube preview with null cells for its unconstrained volume. */
public final class LucidEtchdreamerPreview {
    private static final int SIZE = 233;
    private static final int PANEL_AISLE = 156;
    private static final int PANEL_X = 82;
    private static final int PANEL_Y = 102;
    private static final String RESOURCE = "/data/gtl_enhancedcore/structures/gtl/lucid_etchdreamer.pattern.gz";

    private LucidEtchdreamerPreview() {}

    public static BlockInfo[][][] create(BlockPattern pattern) {
        TraceabilityPredicate[][][] matches = ((BlockPatternAccessor) pattern).gtlEnhancedcore$getBlockMatches();
        if (matches.length != SIZE || matches[0].length != SIZE || matches[0][0].length != SIZE) {
            throw new IllegalStateException("Unexpected lucid_etchdreamer preview dimensions");
        }

        BlockInfo[][][] result = new BlockInfo[SIZE][SIZE][SIZE];
        // GTLCore's preview widget skips null cells. BlockInfo.EMPTY is non-null and
        // would turn the 12.3 million unconstrained cells into preview positions.
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
                    // FactoryBlockPattern.start() uses LEFT / UP / FRONT. At NORTH, X and Z run negative.
                    result[SIZE - 1 - x][y][SIZE - 1 - z] = info;
                }
            }
        });

        // Reuse GTCEu's exact preview selection for the only ability panel. This 5x5x3
        // probe preserves its required maintenance/energy hatches and chosen facing.
        BlockInfo[][][] panel = previewPanel(matches);
        BlockInfo controller = firstCandidate(matches[PANEL_AISLE][PANEL_Y + 2][PANEL_X + 2], 'C');
        BlockInfo casing = firstCandidate(matches[PANEL_AISLE - 1][PANEL_Y][PANEL_X], 'J');
        int controllerLayer = -1;
        for (int layer = 0; layer < panel[2][2].length; layer++) {
            BlockInfo candidate = panel[2][2][layer];
            if (candidate != null && candidate.hasBlockEntity()
                    && candidate.getBlockState().is(controller.getBlockState().getBlock())) {
                controllerLayer = layer;
                break;
            }
        }
        if (controllerLayer < 0) throw new IllegalStateException("Missing lucid_etchdreamer preview controller layer");
        for (int x = 0; x < 5; x++) {
            for (int y = 0; y < 5; y++) {
                BlockInfo info = panel[x][y][controllerLayer];
                if (x == 2 && y == 2) info = controller;
                if ((x != 2 || y != 2) && info != null
                        && info.getBlockState().is(controller.getBlockState().getBlock())) {
                    // An unused X slot accepts either the old controller fallback or
                    // the original iridium casing. Show the casing by default.
                    info = casing;
                }
                result[SIZE - PANEL_X - 5 + x][PANEL_Y + y][SIZE - 1 - PANEL_AISLE] = info;
            }
        }
        return result;
    }

    private static BlockInfo[][][] previewPanel(TraceabilityPredicate[][][] matches) {
        FactoryBlockPattern builder = FactoryBlockPattern.start()
                .aisle("JJJJJ", "JJJJJ", "JJJJJ", "JJJJJ", "JJJJJ")
                .aisle("XXXXX", "XXXXX", "XXCXX", "XXXXX", "XXXXX")
                .aisle("     ", "     ", "     ", "     ", "     ")
                .where('J', matches[PANEL_AISLE - 1][PANEL_Y][PANEL_X])
                .where('X', matches[PANEL_AISLE][PANEL_Y][PANEL_X])
                .where('C', matches[PANEL_AISLE][PANEL_Y + 2][PANEL_X + 2])
                .where(' ', Predicates.any());
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
