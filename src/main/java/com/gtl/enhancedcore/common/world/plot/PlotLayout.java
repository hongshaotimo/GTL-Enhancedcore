package com.gtl.enhancedcore.common.world.plot;

/** Shared deterministic sampler used by both terrain generation and the creation preview. */
public final class PlotLayout {
    public enum Cell { GROUND, PLOT_EDGE, ROAD, BORDER, MARKER, LINE }
    private PlotLayout() {}

    public static Cell surfaceAt(PlotSettings settings, int worldX, int worldZ) {
        int period = settings.plotBlocks();
        int markerPeriod = settings.markerMode() == PlotSettings.MarkerMode.CHUNK ? 16 : period;
        if (settings.markerMode() != PlotSettings.MarkerMode.NONE
                && centered(Math.floorMod(worldX, markerPeriod), markerPeriod, settings.effectiveMarkerSize())
                && centered(Math.floorMod(worldZ, markerPeriod), markerPeriod, settings.effectiveMarkerSize())) return Cell.MARKER;
        int plotX = Math.floorMod(worldX, period), plotZ = Math.floorMod(worldZ, period);
        int plotEdge = Math.min(Math.min(plotX, period - 1 - plotX), Math.min(plotZ, period - 1 - plotZ));
        if (settings.plotEdgeWidth() > 0 && plotEdge < settings.plotEdgeWidth()) return Cell.PLOT_EDGE;
        // Avenues use the same full-width streets at the boundary and halfway through each plot.
        // Every road axis lies BETWEEN blocks, so both sides must use the same folded coordinate.
        int roadPeriod = settings.roadStyle() == PlotSettings.RoadStyle.AVENUE ? period / 2 : period;
        int x = Math.floorMod(worldX, roadPeriod), z = Math.floorMod(worldZ, roadPeriod);
        int xEdge = Math.min(x, roadPeriod - 1 - x), zEdge = Math.min(z, roadPeriod - 1 - z);
        int edge = Math.min(xEdge, zEdge);
        // Each side owns half the shared street; clamp only to preserve a usable plot interior.
        int width = effectiveRoadWidth(settings), border = effectiveBorderWidth(settings);
        Cell base = switch (settings.roadStyle()) {
            case NONE -> Cell.GROUND;
            case OUTLINE -> edge < border ? Cell.BORDER : Cell.GROUND;
            case RING, HIGHWAY, BOULEVARD -> edge < width ? Cell.ROAD
                    : edge < width + border ? Cell.BORDER : Cell.GROUND;
            case GARDEN -> edge < border || (edge >= border + width && edge < border * 2 + width)
                    ? Cell.BORDER : edge < border + width ? Cell.ROAD : Cell.GROUND;
            case AVENUE -> edge < width ? Cell.ROAD : edge < width + border ? Cell.BORDER : Cell.GROUND;
        };
        if (base != Cell.ROAD || settings.lineStyle() == PlotSettings.LineStyle.NONE) return base;
        int inset = settings.roadStyle() == PlotSettings.RoadStyle.GARDEN ? border : 0;
        boolean vertical = xEdge >= inset && xEdge < inset + width;
        boolean horizontal = zEdge >= inset && zEdge < inset + width;
        // Junctions stay clear; crossing bands own the entire band, including the lane center.
        if (vertical && horizontal) return base;
        if (!vertical && !horizontal) return base;
        int across = vertical ? xEdge : zEdge;
        int along = vertical ? z : x;
        int start = inset + width, end = roadPeriod - start;
        if (settings.lineStyle() == PlotSettings.LineStyle.CROSSWALK) {
            // One clear block separates each zebra crossing from the open junction. The same
            // center-out phase is mirrored on the other half of the street, never worldX % 2.
            int depth = Math.min(3, Math.max(0, (end - start) / 2 - 1));
            int fromJunction = Math.min(along - start, end - 1 - along);
            if (fromJunction <= depth)
                return fromJunction >= 1 && (width == 1 || ((across - inset) & 1) == 1) ? Cell.LINE : base;
            // No center dash is allowed to cut through a zebra stripe or its clearance.
            start += depth + 2;
            end -= depth + 2;
        }
        boolean stripe = across == inset + (settings.lineStyle() == PlotSettings.LineStyle.DOUBLE
                ? Math.min(1, width - 1) : 0);
        if (stripe && (settings.lineStyle() == PlotSettings.LineStyle.SOLID
                || settings.lineStyle() == PlotSettings.LineStyle.DOUBLE
                || symmetricDash(along, start, end, settings.dashLength(), settings.dashGap()))) return Cell.LINE;
        return base;
    }

    /** Complete dashes centered in a finite road segment, with equal clearances at both ends. */
    private static boolean symmetricDash(int coordinate, int start, int end, int length, int gap) {
        int span = end - start;
        if (span <= 0 || coordinate < start || coordinate >= end) return false;
        int count = ((span + gap) / (length + gap)) & ~1;
        if (count == 0) {
            // A short segment gets one centered, even-width dash instead of clipped fragments.
            int fitted = Math.min(span, length + (length & 1));
            return Math.abs(2 * (coordinate - start) + 1 - span) < fitted;
        }
        int occupied = count * length + (count - 1) * gap;
        // An even road cannot mirror an odd footprint. Put its one spare block in the middle
        // gap; all painted dashes retain the exact requested length, as do the other gaps.
        int centerExtra = (span - occupied) & 1;
        int margin = (span - occupied - centerExtra) / 2;
        int fromEnd = Math.min(coordinate - start, end - 1 - coordinate) - margin;
        int halfFootprint = (count / 2) * length + (count / 2 - 1) * gap;
        return fromEnd >= 0 && fromEnd < halfFootprint && fromEnd % (length + gap) < length;
    }

    public static boolean supportsLaneMarkings(PlotSettings settings) {
        return settings.roadStyle() != PlotSettings.RoadStyle.NONE && settings.roadStyle() != PlotSettings.RoadStyle.OUTLINE;
    }

    public static int effectiveRoadWidth(PlotSettings settings) {
        int reserve = settings.roadStyle() == PlotSettings.RoadStyle.GARDEN
                ? effectiveBorderWidth(settings) * 2 : effectiveBorderWidth(settings);
        int spacing = settings.roadStyle() == PlotSettings.RoadStyle.AVENUE
                ? settings.plotBlocks() / 2 : settings.plotBlocks();
        return Math.min(settings.roadWidth(), Math.max(1, spacing / 2 - reserve - 2));
    }

    public static int effectiveBorderWidth(PlotSettings settings) {
        int reserve = Math.max(0, settings.plotBlocks() / 2 - 3);
        if (settings.roadStyle() == PlotSettings.RoadStyle.GARDEN) reserve /= 2;
        return Math.min(settings.borderWidth(), reserve);
    }

    private static boolean centered(int coordinate, int period, int width) {
        int first = period / 2 - width / 2;
        return coordinate >= first && coordinate < first + width;
    }

    public static String surfaceBlock(PlotSettings settings, int worldX, int worldZ) {
        return switch (surfaceAt(settings, worldX, worldZ)) {
            case GROUND -> settings.groundBlock();
            case PLOT_EDGE -> settings.plotEdgeBlock();
            case ROAD -> settings.roadBlock();
            case BORDER -> settings.borderBlock();
            case MARKER -> settings.markerBlock();
            case LINE -> settings.lineBlock();
        };
    }

    public static String blockIdAt(PlotSettings settings, int worldX, int y, int worldZ) {
        if (y < settings.bedrockY() || y > settings.surfaceY()) return "minecraft:air";
        if (y == settings.bedrockY()) return "minecraft:bedrock";
        if (y == settings.surfaceY()) return surfaceBlock(settings, worldX, worldZ);
        return settings.groundBlock();
    }

    public static int plotCoordinate(PlotSettings settings, int worldCoordinate) {
        return Math.floorDiv(worldCoordinate, settings.plotBlocks());
    }
}
