package com.gtl.enhancedcore.common.world.plot;

import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

/** Immutable, world-local terrain recipe; deliberately independent of Minecraft bootstrap. */
public record PlotSettings(int plotChunks, String groundBlock, String roadBlock, String borderBlock,
                           String markerBlock, RoadStyle roadStyle, int roadWidth, MarkerMode markerMode,
                           int markerSize, int bedrockY, int surfaceY, String lineBlock,
                           LineStyle lineStyle, int dashLength, int dashGap, int borderWidth,
                           String plotEdgeBlock, int plotEdgeWidth) {
    public static final int FORMAT_VERSION = 2;
    public static final List<String> CONCRETE_COLORS = List.of("white", "orange", "magenta", "light_blue",
            "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black");
    public static final List<String> MATERIALS = CONCRETE_COLORS.stream()
            .map(color -> "minecraft:" + color + "_concrete").toList();
    public static final List<String> MARKER_LIGHTS = List.of("minecraft:sea_lantern", "minecraft:glowstone");

    public enum RoadStyle { RING, OUTLINE, GARDEN, AVENUE, NONE, HIGHWAY, BOULEVARD }
    public enum LineStyle { NONE, DASHED, SOLID, DOUBLE, CROSSWALK }
    public enum RoadPreset { HIGHWAY, CITY, STONE, GARDEN, NEON, MINIMAL }
    public enum MarkerMode { PLOT, CHUNK, NONE }

    public PlotSettings {
        if (plotChunks < 1 || plotChunks > 11) throw new IllegalArgumentException("plotChunks must be 1..11");
        validateConcrete(groundBlock); validateConcrete(roadBlock); validateConcrete(borderBlock); validateConcrete(lineBlock);
        if (!MATERIALS.contains(markerBlock) && !MARKER_LIGHTS.contains(markerBlock))
            throw new IllegalArgumentException("Center marker must be concrete or a preset light");
        Objects.requireNonNull(roadStyle, "roadStyle"); Objects.requireNonNull(lineStyle, "lineStyle"); Objects.requireNonNull(markerMode, "markerMode");
        if (roadWidth < 1 || roadWidth > 32) throw new IllegalArgumentException("roadWidth must be 1..32");
        validateConcrete(plotEdgeBlock);
        if (dashLength < 1 || dashLength > 32 || dashGap < 1 || dashGap > 32
                || borderWidth < 0 || borderWidth > 8 || plotEdgeWidth < 0 || plotEdgeWidth > 8)
            throw new IllegalArgumentException("Invalid road decoration dimensions");
        if (markerSize < 0 || markerSize > 5) throw new IllegalArgumentException("markerSize must be 0..5 (0 = parity-based automatic)");
        if (bedrockY < -64 || bedrockY > 318 || surfaceY <= bedrockY || surfaceY > 319)
            throw new IllegalArgumentException("layers must fit overworld -64..319 and leave a foundation");
    }

    private static void validateConcrete(String id) {
        if (!MATERIALS.contains(id)) throw new IllegalArgumentException("Plot materials must be one of the sixteen concrete colors");
    }
    public PlotSettings(int plotChunks, String groundBlock, String roadBlock, String borderBlock,
                        String markerBlock, RoadStyle roadStyle, int roadWidth, MarkerMode markerMode,
                        int markerSize, int bedrockY, int surfaceY, String lineBlock,
                        LineStyle lineStyle, int dashLength, int dashGap, int borderWidth) {
        this(plotChunks, groundBlock, roadBlock, borderBlock, markerBlock, roadStyle, roadWidth, markerMode,
                markerSize, bedrockY, surfaceY, lineBlock, lineStyle, dashLength, dashGap, borderWidth,
                "minecraft:white_concrete", 0);
    }

    public int plotBlocks() { return plotChunks * 16; }
    public int effectiveMarkerSize() {
        return markerSize > 0 ? markerSize : markerMode == MarkerMode.CHUNK || plotChunks % 2 == 1 ? 2 : 1;
    }
    public int concreteLayers() { return surfaceY - bedrockY; }
    public static PlotSettings defaults() { return preset(3); }
    public static PlotSettings preset(int size) {
        if (size < 1 || size > 10) throw new IllegalArgumentException("preset size must be 1..10");
        return new PlotSettings(size, "minecraft:light_gray_concrete", "minecraft:black_concrete",
                "minecraft:white_concrete", "minecraft:sea_lantern", RoadStyle.HIGHWAY, 3,
                MarkerMode.PLOT, 0, -64, -53, "minecraft:white_concrete", LineStyle.DASHED, 3, 3, 1,
                "minecraft:gray_concrete", 1);
    }
    /** Convenience constructor for callers that do not request lane markings. */
    public PlotSettings(int plotChunks, String groundBlock, String roadBlock, String borderBlock,
                        String markerBlock, RoadStyle roadStyle, int roadWidth, MarkerMode markerMode,
                        int markerSize, int bedrockY, int surfaceY) {
        this(plotChunks, groundBlock, roadBlock, borderBlock, markerBlock, roadStyle, roadWidth,
                markerMode, markerSize, bedrockY, surfaceY, "minecraft:white_concrete", LineStyle.NONE, 3, 3, 1);
    }
    public PlotSettings withRoadPreset(RoadPreset preset) {
        return switch (preset) {
            case HIGHWAY -> roadTheme("black_concrete", "white_concrete", "white_concrete", RoadStyle.HIGHWAY, 3, LineStyle.DASHED, 3, 3, 1);
            case CITY -> roadTheme("black_concrete", "light_gray_concrete", "white_concrete", RoadStyle.BOULEVARD, 4, LineStyle.CROSSWALK, 3, 3, 2);
            case STONE -> roadTheme("light_gray_concrete", "gray_concrete", "white_concrete", RoadStyle.RING, 2, LineStyle.NONE, 3, 3, 1);
            case GARDEN -> roadTheme("white_concrete", "green_concrete", "white_concrete", RoadStyle.GARDEN, 2, LineStyle.NONE, 3, 3, 1);
            case NEON -> roadTheme("black_concrete", "cyan_concrete", "white_concrete", RoadStyle.HIGHWAY, 3, LineStyle.DOUBLE, 3, 3, 1);
            case MINIMAL -> roadTheme("gray_concrete", "white_concrete", "white_concrete", RoadStyle.OUTLINE, 1, LineStyle.NONE, 3, 3, 1);
        };
    }
    private PlotSettings roadTheme(String road, String border, String line, RoadStyle style, int width,
                                    LineStyle marking, int length, int gap, int curb) {
        return new PlotSettings(plotChunks, groundBlock, "minecraft:" + road, "minecraft:" + border,
                markerBlock, style, width, markerMode, markerSize, bedrockY, surfaceY,
                "minecraft:" + line, marking, length, gap, curb, plotEdgeBlock, plotEdgeWidth);
    }
    private PlotSettings copy(int chunks, String ground, String road, String curb, String marker, RoadStyle layout,
                              int width, MarkerMode markers, int markerSide, int bottom, int top, String line,
                              LineStyle marking, int dash, int gap, int curbWidth, String edge, int edgeWidth) {
        return new PlotSettings(chunks,ground,road,curb,marker,layout,width,markers,markerSide,bottom,top,
                line,marking,dash,gap,curbWidth,edge,edgeWidth);
    }
    public PlotSettings withLevels(int bottom, int top) { return copy(plotChunks,groundBlock,roadBlock,borderBlock,markerBlock,roadStyle,roadWidth,markerMode,markerSize,bottom,top,lineBlock,lineStyle,dashLength,dashGap,borderWidth,plotEdgeBlock,plotEdgeWidth); }
    public static List<PlotSettings> presets() { return IntStream.rangeClosed(1, 10).mapToObj(PlotSettings::preset).toList(); }
    public PlotSettings withPlotChunks(int v) { return copy(v,groundBlock,roadBlock,borderBlock,markerBlock,roadStyle,roadWidth,markerMode,markerSize,bedrockY,surfaceY,lineBlock,lineStyle,dashLength,dashGap,borderWidth,plotEdgeBlock,plotEdgeWidth); }
    public PlotSettings withGroundBlock(String v) { return copy(plotChunks,v,roadBlock,borderBlock,markerBlock,roadStyle,roadWidth,markerMode,markerSize,bedrockY,surfaceY,lineBlock,lineStyle,dashLength,dashGap,borderWidth,plotEdgeBlock,plotEdgeWidth); }
    public PlotSettings withRoadBlock(String v) { return copy(plotChunks,groundBlock,v,borderBlock,markerBlock,roadStyle,roadWidth,markerMode,markerSize,bedrockY,surfaceY,lineBlock,lineStyle,dashLength,dashGap,borderWidth,plotEdgeBlock,plotEdgeWidth); }
    public PlotSettings withBorderBlock(String v) { return copy(plotChunks,groundBlock,roadBlock,v,markerBlock,roadStyle,roadWidth,markerMode,markerSize,bedrockY,surfaceY,lineBlock,lineStyle,dashLength,dashGap,borderWidth,plotEdgeBlock,plotEdgeWidth); }
    public PlotSettings withMarkerBlock(String v) { return copy(plotChunks,groundBlock,roadBlock,borderBlock,v,roadStyle,roadWidth,markerMode,markerSize,bedrockY,surfaceY,lineBlock,lineStyle,dashLength,dashGap,borderWidth,plotEdgeBlock,plotEdgeWidth); }
    public PlotSettings withRoadStyle(RoadStyle v) { return copy(plotChunks,groundBlock,roadBlock,borderBlock,markerBlock,v,roadWidth,markerMode,markerSize,bedrockY,surfaceY,lineBlock,lineStyle,dashLength,dashGap,borderWidth,plotEdgeBlock,plotEdgeWidth); }
    public PlotSettings withRoadWidth(int v) { return copy(plotChunks,groundBlock,roadBlock,borderBlock,markerBlock,roadStyle,v,markerMode,markerSize,bedrockY,surfaceY,lineBlock,lineStyle,dashLength,dashGap,borderWidth,plotEdgeBlock,plotEdgeWidth); }
    public PlotSettings withMarkerMode(MarkerMode v) { return copy(plotChunks,groundBlock,roadBlock,borderBlock,markerBlock,roadStyle,roadWidth,v,markerSize,bedrockY,surfaceY,lineBlock,lineStyle,dashLength,dashGap,borderWidth,plotEdgeBlock,plotEdgeWidth); }
    public PlotSettings withMarkerSize(int v) { return copy(plotChunks,groundBlock,roadBlock,borderBlock,markerBlock,roadStyle,roadWidth,markerMode,v,bedrockY,surfaceY,lineBlock,lineStyle,dashLength,dashGap,borderWidth,plotEdgeBlock,plotEdgeWidth); }
    public PlotSettings withBedrockY(int v) { return copy(plotChunks,groundBlock,roadBlock,borderBlock,markerBlock,roadStyle,roadWidth,markerMode,markerSize,v,surfaceY,lineBlock,lineStyle,dashLength,dashGap,borderWidth,plotEdgeBlock,plotEdgeWidth); }
    public PlotSettings withSurfaceY(int v) { return copy(plotChunks,groundBlock,roadBlock,borderBlock,markerBlock,roadStyle,roadWidth,markerMode,markerSize,bedrockY,v,lineBlock,lineStyle,dashLength,dashGap,borderWidth,plotEdgeBlock,plotEdgeWidth); }
    public PlotSettings withLineBlock(String v) { return copy(plotChunks,groundBlock,roadBlock,borderBlock,markerBlock,roadStyle,roadWidth,markerMode,markerSize,bedrockY,surfaceY,v,lineStyle,dashLength,dashGap,borderWidth,plotEdgeBlock,plotEdgeWidth); }
    public PlotSettings withLineStyle(LineStyle v) { return copy(plotChunks,groundBlock,roadBlock,borderBlock,markerBlock,roadStyle,roadWidth,markerMode,markerSize,bedrockY,surfaceY,lineBlock,v,dashLength,dashGap,borderWidth,plotEdgeBlock,plotEdgeWidth); }
    public PlotSettings withDashLength(int v) { return copy(plotChunks,groundBlock,roadBlock,borderBlock,markerBlock,roadStyle,roadWidth,markerMode,markerSize,bedrockY,surfaceY,lineBlock,lineStyle,v,dashGap,borderWidth,plotEdgeBlock,plotEdgeWidth); }
    public PlotSettings withDashGap(int v) { return copy(plotChunks,groundBlock,roadBlock,borderBlock,markerBlock,roadStyle,roadWidth,markerMode,markerSize,bedrockY,surfaceY,lineBlock,lineStyle,dashLength,v,borderWidth,plotEdgeBlock,plotEdgeWidth); }
    public PlotSettings withBorderWidth(int v) { return copy(plotChunks,groundBlock,roadBlock,borderBlock,markerBlock,roadStyle,roadWidth,markerMode,markerSize,bedrockY,surfaceY,lineBlock,lineStyle,dashLength,dashGap,v,plotEdgeBlock,plotEdgeWidth); }
    public PlotSettings withPlotEdgeBlock(String v) { return copy(plotChunks,groundBlock,roadBlock,borderBlock,markerBlock,roadStyle,roadWidth,markerMode,markerSize,bedrockY,surfaceY,lineBlock,lineStyle,dashLength,dashGap,borderWidth,v,plotEdgeWidth); }
    public PlotSettings withPlotEdgeWidth(int v) { return copy(plotChunks,groundBlock,roadBlock,borderBlock,markerBlock,roadStyle,roadWidth,markerMode,markerSize,bedrockY,surfaceY,lineBlock,lineStyle,dashLength,dashGap,borderWidth,plotEdgeBlock,v); }
}
