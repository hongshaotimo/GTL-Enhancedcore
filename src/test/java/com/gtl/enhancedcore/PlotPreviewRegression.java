package com.gtl.enhancedcore;

import com.gtl.enhancedcore.common.world.plot.PlotLayout;
import com.gtl.enhancedcore.common.world.plot.PlotPreviewRaster;
import com.gtl.enhancedcore.common.world.plot.PlotSettings;
import java.util.Arrays;

/** Checks actual preview pixels, including fractional marker coverage and narrow lane markings. */
final class PlotPreviewRegression {
    static int run() {
        int checks = 0;
        int[] markerPalette = {0, 0, 0, 0, 0xffffff, 0};
        for (int chunks = 1; chunks <= 11; chunks++) for (int plots : new int[] {1, 2}) {
            PlotSettings settings = PlotSettings.defaults().withPlotChunks(chunks)
                    .withRoadStyle(PlotSettings.RoadStyle.NONE);
            int cells = settings.plotBlocks() * plots;
            int[] exact = PlotPreviewRaster.render(settings, cells, plots, markerPalette);
            int markerCount = 0;
            for (int z = 0; z < cells; z++) for (int x = 0; x < cells; x++) {
                boolean marker = PlotLayout.surfaceAt(settings, x, z) == PlotLayout.Cell.MARKER;
                check(exact[z * cells + x] == (marker ? 0xffffffff : 0xff000000),
                        "At native scale preview markers match actual world blocks without extra pixels"); checks++;
                if (marker) markerCount++;
            }
            check(markerCount == (chunks % 2 == 1 ? 4 : 1) * plots * plots,
                    "Odd and even marker footprints repeat correctly in the four-plot view"); checks++;
            for (int pixels : new int[] {31, 64, 137, 257}) {
                int[] raster = PlotPreviewRaster.render(settings, pixels, plots, markerPalette);
                for (int z = 0; z < pixels; z++) for (int x = 0; x < pixels; x++) {
                    int expected;
                    if (pixels >= cells) {
                        int wx = (2 * x + 1) * cells / (2 * pixels);
                        int wz = (2 * z + 1) * cells / (2 * pixels);
                        expected = PlotLayout.surfaceAt(settings, wx, wz) == PlotLayout.Cell.MARKER ? 255 : 0;
                    } else {
                        // Independent analytic rectangle integration for the actual center lamp footprint.
                        long coverage = 0;
                        int period = settings.plotBlocks(), width = settings.effectiveMarkerSize();
                        int first = period / 2 - width / 2;
                        for (int pz = 0; pz < plots; pz++) for (int px = 0; px < plots; px++) {
                            long overlapX = overlap((long) x * cells, (long) (x + 1) * cells,
                                    (long) (px * period + first) * pixels,
                                    (long) (px * period + first + width) * pixels);
                            long overlapZ = overlap((long) z * cells, (long) (z + 1) * cells,
                                    (long) (pz * period + first) * pixels,
                                    (long) (pz * period + first + width) * pixels);
                            coverage += overlapX * overlapZ;
                        }
                        expected = (int) ((coverage * 255 + (long) cells * cells / 2) / ((long) cells * cells));
                    }
                    check(raster[z * pixels + x] == (0xff000000 | expected * 0x010101),
                            "Resized preview has exactly the real lamp coverage and no detached corner dot"); checks++;
                }
            }
        }
        PlotSettings highway = PlotSettings.defaults().withPlotChunks(11)
                .withRoadPreset(PlotSettings.RoadPreset.HIGHWAY).withMarkerMode(PlotSettings.MarkerMode.NONE)
                .withPlotEdgeWidth(0);
        int[] linePalette = {0, 0, 0, 0, 0, 0xffffff};
        int[] dashed = PlotPreviewRaster.render(highway, 64, 2, linePalette);
        int[] unmarked = PlotPreviewRaster.render(highway.withLineStyle(PlotSettings.LineStyle.NONE), 64, 2, linePalette);
        check(!Arrays.equals(dashed, unmarked), "One-block road markings survive downsampling a large four-plot view"); checks++;
        check(Arrays.stream(unmarked).allMatch(rgb -> rgb == 0xff000000),
                "Disabled line and center markers never leave synthetic marks"); checks++;
        int[] redPalette = {0x123456, 0x123456, 0x123456, 0x123456, 0x123456, 0x123456};
        check(Arrays.stream(PlotPreviewRaster.render(highway, 93, 2, redPalette)).allMatch(rgb -> rgb == 0xff123456),
                "Coverage averaging preserves opaque RGB colors without changing channels"); checks++;
        PlotSettings symmetric = PlotSettings.defaults().withPlotChunks(3).withRoadPreset(PlotSettings.RoadPreset.CITY)
                .withPlotEdgeWidth(3).withMarkerMode(PlotSettings.MarkerMode.NONE);
        for (int pixels : new int[] {31, 64, 137, 257}) {
            int[] raster = PlotPreviewRaster.render(symmetric, pixels, 2, new int[] {1, 2, 3, 4, 5, 6});
            for (int z = 0; z < pixels; z++) for (int x = 0; x < pixels; x++) {
                check(raster[z * pixels + x] == raster[z * pixels + pixels - 1 - x],
                        "Odd and even preview widths preserve exact horizontal mirror symmetry"); checks++;
                check(raster[z * pixels + x] == raster[(pixels - 1 - z) * pixels + x],
                        "Odd and even preview heights preserve exact vertical mirror symmetry"); checks++;
            }
        }
        for (int pixels : new int[] {0, PlotPreviewRaster.MAX_PIXELS + 1}) {
            try {
                PlotPreviewRaster.render(highway, pixels, 2, redPalette);
                throw new AssertionError("Raster allocation must be bounded");
            } catch (IllegalArgumentException expected) { checks++; }
        }
        return checks;
    }

    private static long overlap(long a, long b, long c, long d) { return Math.max(0, Math.min(b, d) - Math.max(a, c)); }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
