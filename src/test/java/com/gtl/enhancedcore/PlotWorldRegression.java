package com.gtl.enhancedcore;

import com.gtl.enhancedcore.common.world.plot.PlotLayout;
import com.gtl.enhancedcore.common.world.plot.PlotSettings;

/** Deterministic layout and serialization checks, runnable without bootstrapping Minecraft. */
final class PlotWorldRegression {
    static int run() {
        int checks = 0;
        check(PlotSettings.presets().size() == 10, "All ten requested size presets exist"); checks++;
        for (int size = 1; size <= 10; size++) {
            PlotSettings settings = PlotSettings.preset(size);
            int period = settings.plotBlocks();
            check(period == 16 * size, "Preset is an exact group of complete chunks"); checks++;
            check(settings.concreteLayers() == 11, "Default has eleven concrete layers"); checks++;
            check(PlotLayout.blockIdAt(settings, 8, -64, 8).equals("minecraft:bedrock"), "Bedrock foundation"); checks++;
            check(PlotLayout.blockIdAt(settings, 8, -54, 8).equals(settings.groundBlock()), "Interior ground layers"); checks++;
            check(PlotLayout.blockIdAt(settings, 8, -52, 8).equals("minecraft:air"), "No raised road layer"); checks++;
            check(PlotLayout.blockIdAt(settings, 8, -65, 8).equals("minecraft:air"), "No terrain below foundation"); checks++;
            for (PlotSettings.RoadStyle road : PlotSettings.RoadStyle.values()) {
                PlotSettings selected = settings.withRoadStyle(road);
                for (int x = -period - 1; x <= period + 1; x++) {
                    for (int z : new int[] {-period - 1, -period, -1, 0, 1, period / 2, period - 1, period}) {
                        check(PlotLayout.surfaceAt(selected, x, z) == PlotLayout.surfaceAt(selected, x + period, z - period),
                                "Four-plot preview and generation agree across negative chunk boundaries"); checks++;
                    }
                }
                check(PlotLayout.surfaceAt(selected, period / 2, period / 2) == PlotLayout.Cell.MARKER,
                        "Every plot center includes its deterministic positive-side anchor"); checks++;
                for (PlotSettings.LineStyle line : PlotSettings.LineStyle.values()) {
                    PlotSettings marked = selected.withMarkerMode(PlotSettings.MarkerMode.NONE).withLineStyle(line);
                    for (int x : new int[] {-period, -1, 0, 1, 2, 4, period / 2, period - 1}) {
                        for (int z = -period; z <= period; z++) {
                            PlotLayout.Cell cell = PlotLayout.surfaceAt(marked, x, z);
                            check(cell == PlotLayout.surfaceAt(marked, x + period, z - period),
                                    "Lane markings repeat across negative world coordinates"); checks++;
                            check(cell != PlotLayout.Cell.LINE || marked.lineStyle() != PlotSettings.LineStyle.NONE,
                                    "Disabled lane markings never emit marking material"); checks++;
                        }
                    }
                }
            }
            check(PlotLayout.plotCoordinate(settings, -1) == -1
                    && PlotLayout.plotCoordinate(settings, -period) == -1
                    && PlotLayout.plotCoordinate(settings, -period - 1) == -2, "Negative plot coordinates floor correctly"); checks++;
        }
        for (int chunks = 1; chunks <= 11; chunks++) {
            PlotSettings automatic = PlotSettings.defaults().withPlotChunks(chunks).withRoadStyle(PlotSettings.RoadStyle.NONE);
            int expected = chunks % 2 == 1 ? 4 : 1, center = automatic.plotBlocks() / 2, lamps = 0;
            for (int x = 0; x < automatic.plotBlocks(); x++) for (int z = 0; z < automatic.plotBlocks(); z++) {
                if (PlotLayout.surfaceAt(automatic, x, z) == PlotLayout.Cell.MARKER) lamps++;
            }
            check(lamps == expected, "Odd plot groups have four center lamps; even groups have one"); checks++;
            check(PlotLayout.surfaceAt(automatic, center, center) == PlotLayout.Cell.MARKER,
                    "Automatic marker stays at the plot center for both parities"); checks++;
            PlotSettings manual = automatic.withMarkerSize(3);
            check(manual.effectiveMarkerSize() == 3, "Explicit custom marker size overrides automatic parity"); checks++;
        }
        PlotSettings custom = PlotSettings.defaults().withPlotChunks(11).withGroundBlock("minecraft:red_concrete")
                .withRoadBlock("minecraft:gray_concrete").withBorderBlock("minecraft:white_concrete")
                .withMarkerBlock("minecraft:glowstone").withRoadStyle(PlotSettings.RoadStyle.AVENUE)
                .withRoadWidth(7).withMarkerMode(PlotSettings.MarkerMode.CHUNK).withMarkerSize(5)
                .withSurfaceY(100).withBedrockY(50).withLineStyle(PlotSettings.LineStyle.DOUBLE)
                .withDashLength(5).withDashGap(4).withBorderWidth(2);
        PlotSettings copied = new PlotSettings(custom.plotChunks(), custom.groundBlock(), custom.roadBlock(),
                custom.borderBlock(), custom.markerBlock(), custom.roadStyle(), custom.roadWidth(), custom.markerMode(),
                custom.markerSize(), custom.bedrockY(), custom.surfaceY(), custom.lineBlock(), custom.lineStyle(),
                custom.dashLength(), custom.dashGap(), custom.borderWidth(), custom.plotEdgeBlock(), custom.plotEdgeWidth());
        check(custom.equals(copied), "All custom settings survive DTO round-trip"); checks++;
        check(PlotLayout.surfaceAt(custom, -8, -8) == PlotLayout.Cell.MARKER
                && PlotLayout.surfaceAt(custom, 8, 8) == PlotLayout.Cell.MARKER, "Chunk marker repeats in both coordinate signs"); checks++;
        PlotSettings plain = custom.withRoadStyle(PlotSettings.RoadStyle.NONE).withMarkerMode(PlotSettings.MarkerMode.NONE)
                .withPlotEdgeWidth(0);
        check(PlotLayout.surfaceAt(plain, 0, 0) == PlotLayout.Cell.GROUND, "No-road/no-marker mode is entirely ground"); checks++;
        for (Runnable invalid : new Runnable[] {
                () -> PlotSettings.preset(0), () -> PlotSettings.preset(11),
                () -> custom.withPlotChunks(12), () -> custom.withRoadWidth(33), () -> custom.withMarkerSize(-1),
                () -> custom.withGroundBlock("minecraft:stone_bricks"),
                () -> custom.withRoadBlock("minecraft:sea_lantern"), () -> custom.withLineBlock("minecraft:glowstone"),
                () -> custom.withMarkerBlock("minecraft:stone"),
                () -> custom.withDashLength(0), () -> custom.withBorderWidth(9),
                () -> custom.withBedrockY(-65), () -> custom.withSurfaceY(320),
                () -> custom.withBedrockY(101), () -> custom.withGroundBlock("invalid identifier") }) {
            try { invalid.run(); throw new AssertionError("Invalid plot recipe must be rejected"); }
            catch (IllegalArgumentException expected) { checks++; }
        }
        PlotSettings highest = PlotSettings.defaults().withSurfaceY(319).withBedrockY(318);
        check(PlotLayout.blockIdAt(highest, 0, 318, 0).equals("minecraft:bedrock")
                && PlotLayout.blockIdAt(highest, 0, 320, 0).equals("minecraft:air"), "Maximum legal layers are bounded"); checks++;
        PlotSettings highway = PlotSettings.defaults().withRoadPreset(PlotSettings.RoadPreset.HIGHWAY)
                .withMarkerMode(PlotSettings.MarkerMode.NONE).withPlotEdgeWidth(0);
        for (int size : new int[] {1, 3, 5, 7, 9, 11}) {
            PlotSettings symmetric = PlotSettings.defaults().withPlotChunks(size).withMarkerMode(PlotSettings.MarkerMode.NONE)
                    .withPlotEdgeWidth(3).withPlotEdgeBlock("minecraft:red_concrete");
            int period = symmetric.plotBlocks();
            for (int z = -period; z < period; z++) for (int x = 0; x < period; x++) {
                check(PlotLayout.surfaceAt(symmetric, x, z) == PlotLayout.surfaceAt(symmetric, period - 1 - x, z),
                        "Odd-sized plot surfaces mirror exactly across X"); checks++;
                check(PlotLayout.surfaceAt(symmetric, x, z) == PlotLayout.surfaceAt(symmetric, x, period - 1 - z),
                        "Odd-sized plot surfaces mirror exactly across Z"); checks++;
            }
            check(PlotLayout.surfaceAt(symmetric, 0, 10) == PlotLayout.Cell.PLOT_EDGE
                    && PlotLayout.surfaceAt(symmetric, 2, 10) == PlotLayout.Cell.PLOT_EDGE
                    && PlotLayout.surfaceAt(symmetric, 3, 10) != PlotLayout.Cell.PLOT_EDGE,
                    "Custom plot edge width has an exact symmetric footprint"); checks++;
        }
        check(highway.roadBlock().equals("minecraft:black_concrete")
                && highway.lineBlock().equals("minecraft:white_concrete"), "Highway preset uses black road and white marking materials"); checks++;
        check(PlotLayout.surfaceBlock(highway, 0, 7).equals("minecraft:white_concrete"), "Highway preview and generated road expose centered dashed lane markings"); checks++;
        PlotSettings tall = highway.withLevels(-32, 128);
        check(tall.concreteLayers() == 160 && PlotLayout.blockIdAt(tall, 8, 100, 8).equals(tall.groundBlock()),
                "Custom world height remains applied below the surface"); checks++;
        check(PlotLayout.surfaceAt(highway, 0, 4) == PlotLayout.Cell.ROAD,
                "The gap between white dashes keeps the black road material"); checks++;
        check(PlotLayout.surfaceAt(highway.withLineStyle(PlotSettings.LineStyle.SOLID), 0, 4) == PlotLayout.Cell.LINE,
                "Solid marking ignores dash gaps"); checks++;
        PlotSettings doubleLine = highway.withLineStyle(PlotSettings.LineStyle.DOUBLE);
        check(PlotLayout.surfaceAt(doubleLine, 1, 8) == PlotLayout.Cell.LINE
                && PlotLayout.surfaceAt(doubleLine, -2, 8) == PlotLayout.Cell.LINE
                && PlotLayout.surfaceAt(doubleLine, 0, 8) == PlotLayout.Cell.ROAD,
                "Double lines straddle the shared road center without filling the center gap"); checks++;
        PlotSettings city = highway.withRoadPreset(PlotSettings.RoadPreset.CITY);
        check(PlotLayout.surfaceAt(city, 1, 5) == PlotLayout.Cell.LINE
                && PlotLayout.surfaceAt(city, 2, 5) == PlotLayout.Cell.ROAD,
                "Zebra crossing alternates road and marking cubes outside the junction"); checks++;
        for (PlotSettings.RoadPreset theme : PlotSettings.RoadPreset.values()) {
            PlotSettings themed = tall.withGroundBlock("minecraft:blue_concrete").withRoadPreset(theme);
            check(themed.bedrockY() == -32 && themed.surfaceY() == 128
                    && themed.groundBlock().equals("minecraft:blue_concrete"),
                    "Road presets preserve custom height and plot material"); checks++;
        }
        check(PlotLayout.effectiveRoadWidth(PlotSettings.preset(1).withRoadWidth(32)) == 5,
                "Small plots bound oversized road width to retain interior space"); checks++;
        for (PlotSettings.RoadPreset theme : PlotSettings.RoadPreset.values()) {
            PlotSettings themed = PlotSettings.defaults().withRoadPreset(theme);
            check(PlotSettings.MATERIALS.containsAll(java.util.List.of(themed.groundBlock(), themed.roadBlock(),
                    themed.borderBlock(), themed.lineBlock())), "All templates stay within the concrete-only palette"); checks++;
        }
        check(PlotSettings.MATERIALS.size() == 16, "The palette exposes all sixteen concrete colors"); checks++;
        PlotSettings garden = highway.withRoadPreset(PlotSettings.RoadPreset.GARDEN).withLineStyle(PlotSettings.LineStyle.SOLID);
        check(PlotLayout.surfaceAt(garden, garden.borderWidth(), 12) == PlotLayout.Cell.LINE,
                "Garden curb inset does not swallow enabled lane markings"); checks++;
        check(PlotLayout.surfaceAt(highway.withRoadWidth(1).withLineStyle(PlotSettings.LineStyle.DOUBLE), 0, 12)
                == PlotLayout.Cell.LINE, "Narrow shared roads still render double lines on both plot sides"); checks++;
        check(!PlotLayout.supportsLaneMarkings(highway.withRoadStyle(PlotSettings.RoadStyle.OUTLINE))
                && !PlotLayout.supportsLaneMarkings(highway.withRoadStyle(PlotSettings.RoadStyle.NONE)),
                "UI can distinguish layouts without any road surface"); checks++;
        PlotSettings avenue = highway.withRoadStyle(PlotSettings.RoadStyle.AVENUE).withRoadWidth(5)
                .withLineStyle(PlotSettings.LineStyle.DOUBLE);
        int center = avenue.plotBlocks() / 2;
        check(PlotLayout.surfaceAt(avenue, center - 2, 12) == PlotLayout.Cell.LINE
                && PlotLayout.surfaceAt(avenue, center + 1, 12) == PlotLayout.Cell.LINE
                && PlotLayout.surfaceAt(avenue, center, 12) == PlotLayout.Cell.ROAD,
                "Central avenue double markings use two separate lanes rather than a single line"); checks++;
        return checks;
    }

    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
