package com.gtl.enhancedcore;

import com.gtl.enhancedcore.common.world.plot.PlotLayout;
import com.gtl.enhancedcore.common.world.plot.PlotPreviewRaster;
import com.gtl.enhancedcore.common.world.plot.PlotSettings;

/** Geometric invariants for roads, independent of which concrete colors the player chooses. */
final class PlotRoadAlignmentRegression {
    static int run() {
        int checks = 0;
        for (int chunks = 1; chunks <= 11; chunks++) {
            for (var preset : PlotSettings.RoadPreset.values()) {
                var settings = PlotSettings.defaults().withPlotChunks(chunks).withRoadPreset(preset)
                        .withMarkerMode(PlotSettings.MarkerMode.NONE).withPlotEdgeWidth(0);
                int p = settings.plotBlocks();
                int[] palette = {0x807e70, 0x606060, 0x121518, 0xa0a0a0, 0x88ffff, 0xffffff};
                int[] preview = PlotPreviewRaster.render(settings, p * 2, 2, palette);
                for (int z = 0; z < p; z++) for (int x = 0; x < p; x++) {
                    checks += aligned(settings, x, z);
                    for (int dz = 0; dz < 2; dz++) for (int dx = 0; dx < 2; dx++) {
                        require(preview[(z + dz * p) * p * 2 + x + dx * p]
                                == (0xff000000 | palette[PlotLayout.surfaceAt(settings, x, z).ordinal()]),
                                settings, "Preview must repeat actual blocks without shifted tile seams");
                        checks++;
                    }
                }
            }
        }
        // All custom widths, all layouts/markings, both parities of dash and gap, small/large plots.
        for (int chunks : new int[]{1, 2, 3, 10, 11}) for (var style : PlotSettings.RoadStyle.values())
            for (var line : PlotSettings.LineStyle.values()) for (int width = 1; width <= 32; width++) {
                var settings = PlotSettings.defaults().withPlotChunks(chunks).withRoadStyle(style)
                        .withLineStyle(line).withRoadWidth(width).withBorderWidth(width % 9)
                        .withDashLength(width).withDashGap(33 - width).withMarkerMode(PlotSettings.MarkerMode.NONE)
                        .withPlotEdgeWidth(0);
                int p = settings.plotBlocks();
                for (int x : new int[]{0, 1, 2, width - 1, width, p / 4 - 1, p / 4, p / 2 - 1, p / 2, p - 1})
                    for (int z = 0; z < p; z++) checks += aligned(settings, x, z);
            }
        var city = PlotSettings.defaults().withRoadPreset(PlotSettings.RoadPreset.CITY)
                .withMarkerMode(PlotSettings.MarkerMode.NONE).withPlotEdgeWidth(0);
        for (int z = 5; z <= 7; z++) for (int x = -4; x < 4; x++) {
            require(PlotLayout.surfaceAt(city, x, z) == PlotLayout.surfaceAt(city, x, 5), city,
                    "Every zebra stripe has equal length; center dashes must not protrude into the crossing"); checks++;
        }
        var narrow = city.withRoadWidth(1);
        require(PlotLayout.surfaceAt(narrow, 0, 2) == PlotLayout.Cell.LINE
                && PlotLayout.surfaceAt(narrow, -1, 2) == PlotLayout.Cell.LINE, narrow,
                "Two-block streets retain a centered narrow pedestrian crossing"); checks++;
        // A reflected image alone could still hide equally clipped dashes. Count actual painted
        // runs independently and verify the finite-segment fitter preserves requested lengths.
        for (int chunks = 1; chunks <= 11; chunks++) for (int length = 1; length <= 32; length++)
            for (int gap : new int[]{1, 2, 3, 8, 31, 32}) {
                var s = city.withPlotChunks(chunks).withRoadWidth(3).withLineStyle(PlotSettings.LineStyle.DASHED)
                        .withDashLength(length).withDashGap(gap);
                int runs = 0, run = 0, firstRun = 0;
                for (int z = 0; z <= s.plotBlocks(); z++) {
                    if (z < s.plotBlocks() && PlotLayout.surfaceAt(s, 0, z) == PlotLayout.Cell.LINE) run++;
                    else if (run > 0) {
                        if (runs == 0) firstRun = run;
                        require(run == firstRun, s, "All dashes in a segment must have equal full lengths"); checks++;
                        runs++; run = 0;
                    }
                }
                require(runs > 0 && (runs == 1 || firstRun == length), s,
                        "Multiple dashes retain the selected length without truncated end pieces"); checks++;
            }
        return checks;
    }

    private static int aligned(PlotSettings s, int x, int z) {
        int p = s.plotBlocks();
        var cell = PlotLayout.surfaceAt(s, x, z);
        require(cell == PlotLayout.surfaceAt(s, p - 1 - x, z), s, "East/west mirror alignment");
        require(cell == PlotLayout.surfaceAt(s, x, p - 1 - z), s, "North/south mirror alignment");
        require(cell == PlotLayout.surfaceAt(s, z, x), s, "Horizontal/vertical roads must match");
        require(cell == PlotLayout.surfaceAt(s, x - p, z + p), s, "Negative/positive tile seams must match");
        require(cell != PlotLayout.Cell.LINE || PlotLayout.surfaceAt(s.withLineStyle(PlotSettings.LineStyle.NONE), x, z) == PlotLayout.Cell.ROAD,
                s, "No marking may spill onto curbs or plot interiors");
        if (s.roadStyle() == PlotSettings.RoadStyle.AVENUE) {
            require(cell == PlotLayout.surfaceAt(s, x + p / 2, z + p / 2), s,
                    "Internal avenue and boundary roads must share width, junctions and marking phase");
        }
        return s.roadStyle() == PlotSettings.RoadStyle.AVENUE ? 6 : 5;
    }

    private static void require(boolean condition, PlotSettings settings, String message) {
        if (!condition) throw new AssertionError(message + ": " + settings);
    }
}
