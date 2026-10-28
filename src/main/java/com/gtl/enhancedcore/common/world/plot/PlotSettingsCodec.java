package com.gtl.enhancedcore.common.world.plot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;

/** Registry-independent codec for the serialized plot terrain recipe. */
public final class PlotSettingsCodec {
    private static final Codec<PlotSettings.RoadStyle> ROAD_STYLE = enumCodec(PlotSettings.RoadStyle.class);
    private static final Codec<PlotSettings.MarkerMode> MARKER_MODE = enumCodec(PlotSettings.MarkerMode.class);

    private record GeometryExtras(int borderWidth, String plotEdgeBlock, int plotEdgeWidth) {}

    private static final MapCodec<GeometryExtras> GEOMETRY_EXTRAS = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.intRange(0, 8).fieldOf("border_width").forGetter(GeometryExtras::borderWidth),
            Codec.STRING.fieldOf("plot_edge_block").forGetter(GeometryExtras::plotEdgeBlock),
            Codec.intRange(0, 8).fieldOf("plot_edge_width").forGetter(GeometryExtras::plotEdgeWidth)
    ).apply(instance, GeometryExtras::new));

    private static final MapCodec<GeometryExtras> COMPATIBLE_GEOMETRY = RecordCodecBuilder.mapCodec(instance -> instance.group(
            GEOMETRY_EXTRAS.codec().optionalFieldOf("geometry_extras").forGetter(settings ->
                    Optional.of(new GeometryExtras(settings.borderWidth(), settings.plotEdgeBlock(), settings.plotEdgeWidth()))),
            Codec.intRange(0, 8).optionalFieldOf("border_width").forGetter(settings -> Optional.empty())
    ).apply(instance, (extras, legacyBorderWidth) -> extras.orElseGet(() -> new GeometryExtras(
            legacyBorderWidth.orElse(1), "minecraft:white_concrete", 0))));

    public static final Codec<PlotSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.intRange(1, 11).fieldOf("plot_chunks").forGetter(PlotSettings::plotChunks),
            Codec.STRING.fieldOf("ground_block").forGetter(PlotSettings::groundBlock),
            Codec.STRING.fieldOf("road_block").forGetter(PlotSettings::roadBlock),
            Codec.STRING.fieldOf("border_block").forGetter(PlotSettings::borderBlock),
            Codec.STRING.fieldOf("marker_block").forGetter(PlotSettings::markerBlock),
            ROAD_STYLE.fieldOf("road_style").forGetter(PlotSettings::roadStyle),
            Codec.intRange(1, 32).fieldOf("road_width").forGetter(PlotSettings::roadWidth),
            MARKER_MODE.fieldOf("marker_mode").forGetter(PlotSettings::markerMode),
            Codec.intRange(0, 5).fieldOf("marker_size").forGetter(PlotSettings::markerSize),
            Codec.intRange(-64, 318).fieldOf("bedrock_y").forGetter(PlotSettings::bedrockY),
            Codec.intRange(-63, 319).fieldOf("surface_y").forGetter(PlotSettings::surfaceY),
            Codec.STRING.fieldOf("line_block").forGetter(PlotSettings::lineBlock),
            enumCodec(PlotSettings.LineStyle.class).fieldOf("line_style").forGetter(PlotSettings::lineStyle),
            Codec.intRange(1, 32).fieldOf("dash_length").forGetter(PlotSettings::dashLength),
            Codec.intRange(1, 32).fieldOf("dash_gap").forGetter(PlotSettings::dashGap),
            COMPATIBLE_GEOMETRY.forGetter(settings -> new GeometryExtras(
                    settings.borderWidth(), settings.plotEdgeBlock(), settings.plotEdgeWidth()))
    ).apply(instance, (chunks, ground, road, border, marker, style, roadWidth, markerMode, markerSize,
                       bedrockY, surfaceY, line, lineStyle, dashLength, dashGap, geometry) ->
            new PlotSettings(chunks, ground, road, border, marker, style, roadWidth, markerMode, markerSize,
                    bedrockY, surfaceY, line, lineStyle, dashLength, dashGap, geometry.borderWidth(),
                    geometry.plotEdgeBlock(), geometry.plotEdgeWidth())));

    private PlotSettingsCodec() {}

    private static <E extends Enum<E>> Codec<E> enumCodec(Class<E> type) {
        return Codec.STRING.comapFlatMap(value -> {
            try { return DataResult.success(Enum.valueOf(type, value)); }
            catch (IllegalArgumentException exception) { return DataResult.error(() -> "Unknown " + type.getSimpleName() + ": " + value); }
        }, Enum::name);
    }
}
