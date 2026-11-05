package com.gtl.enhancedcore.common.world.plot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Bounded, strict text interchange. It never accesses a world, registry, path or network. */
public final class PlotPresetShare {
    public static final int MAX_TEXT_LENGTH = 8192;
    public static final int SHARE_VERSION = 4;
    public static final int LEGACY_SHARE_VERSION = 3;
    private static final String FORMAT = "gtl_enhancedcore:plot_preset";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Set<String> NUMBERS = Set.of("plot_chunks", "road_width", "marker_size", "bedrock_y",
            "surface_y", "dash_length", "dash_gap", "border_width", "plot_edge_width");
    private static final Set<String> STRINGS = Set.of("ground_block", "road_block", "border_block", "marker_block",
            "road_style", "marker_mode", "line_block", "line_style", "plot_edge_block");
    private static final Set<String> BOOLEANS = Set.of("show_machine_frames");
    private PlotPresetShare() {}

    public static String encode(PlotSettings settings) {
        validateMaterials(settings);
        JsonObject root = new JsonObject();
        boolean filePreset = settings.roadStyle() == PlotSettings.RoadStyle.CAMPUS_FILE;
        root.addProperty("format", FORMAT); root.addProperty("version", filePreset ? SHARE_VERSION : LEGACY_SHARE_VERSION);
        JsonObject values = new JsonObject();
        values.addProperty("plot_chunks", settings.plotChunks());
        values.addProperty("ground_block", settings.groundBlock());
        values.addProperty("road_block", settings.roadBlock());
        values.addProperty("border_block", settings.borderBlock());
        values.addProperty("marker_block", settings.markerBlock());
        values.addProperty("road_style", settings.roadStyle().name());
        values.addProperty("road_width", settings.roadWidth());
        values.addProperty("marker_mode", settings.markerMode().name());
        values.addProperty("marker_size", settings.markerSize());
        values.addProperty("bedrock_y", settings.bedrockY());
        values.addProperty("surface_y", settings.surfaceY());
        values.addProperty("line_block", settings.lineBlock());
        values.addProperty("line_style", settings.lineStyle().name());
        values.addProperty("dash_length", settings.dashLength());
        values.addProperty("dash_gap", settings.dashGap());
        values.addProperty("border_width", settings.borderWidth());
        values.addProperty("plot_edge_block", settings.plotEdgeBlock());
        values.addProperty("plot_edge_width", settings.plotEdgeWidth());
        values.addProperty("show_machine_frames", settings.showMachineFrames());
        if (filePreset) values.addProperty("campus_preset_id", settings.campusPresetId());
        root.add("settings", values);
        return GSON.toJson(root);
    }

    public static PlotSettings decode(String text) {
        return decode(text, false);
    }

    /** Persisted v1 owner/template recipes predate plot edges; never rewrite their immutable ZIPs. */
    public static PlotSettings decodeStored(String text) {
        return decode(text, true);
    }

    private static PlotSettings decode(String text, boolean allowLegacy) {
        if (text == null || text.isBlank()) throw failure("empty");
        if (text.length() > MAX_TEXT_LENGTH) throw failure("too_long");
        try (JsonReader reader = new JsonReader(new StringReader(text))) {
            reader.setLenient(false);
            reader.beginObject();
            Map<String, Boolean> seen = new HashMap<>();
            Map<String, String> values = null;
            String format = null;
            int version = -1;
            while (reader.hasNext()) {
                String key = reader.nextName();
                if (seen.put(key, true) != null) throw failure("schema");
                switch (key) {
                    case "format" -> format = string(reader);
                    case "version" -> version = integer(reader);
                    case "settings" -> values = readSettings(reader);
                    default -> throw failure("schema");
                }
            }
            reader.endObject();
            if (reader.peek() != JsonToken.END_DOCUMENT) throw failure("malformed");
            if (seen.size() != 3 || !FORMAT.equals(format) || values == null) throw failure("schema");
            if (allowLegacy && version == 1) {
                if (values.containsKey("plot_edge_block") || values.containsKey("plot_edge_width")
                        || values.containsKey("show_machine_frames")) throw failure("schema");
                values.put("plot_edge_block", "minecraft:white_concrete");
                values.put("plot_edge_width", "0");
                values.put("show_machine_frames", "false");
            } else if (allowLegacy && version == 2) {
                if (values.containsKey("show_machine_frames")) throw failure("schema");
                values.put("show_machine_frames", "false");
            } else if (version != LEGACY_SHARE_VERSION && version != SHARE_VERSION) throw failure("version");
            boolean filePreset = version == SHARE_VERSION;
            int expectedFields = NUMBERS.size() + STRINGS.size() + BOOLEANS.size() + (filePreset ? 1 : 0);
            if (values.size() != expectedFields || values.containsKey("campus_preset_id") != filePreset) throw failure("schema");
            if (filePreset) {
                String id = values.get("campus_preset_id");
                if (!"CAMPUS_FILE".equals(values.get("road_style")) || id.isBlank() || !CampusPresetRegistry.contains(id))
                    throw failure("settings");
            } else if ("CAMPUS_FILE".equals(values.get("road_style"))) throw failure("settings");
            validateMaterials(values.get("ground_block"), values.get("plot_edge_block"), values.get("road_block"), values.get("border_block"),
                    values.get("line_block"), values.get("marker_block"));
            PlotSettings result = new PlotSettings(number(values, "plot_chunks"), values.get("ground_block"),
                    values.get("road_block"), values.get("border_block"), values.get("marker_block"),
                    enumValue(PlotSettings.RoadStyle.class, values.get("road_style")), number(values, "road_width"),
                    enumValue(PlotSettings.MarkerMode.class, values.get("marker_mode")), number(values, "marker_size"),
                    number(values, "bedrock_y"), number(values, "surface_y"), values.get("line_block"),
                    enumValue(PlotSettings.LineStyle.class, values.get("line_style")), number(values, "dash_length"),
                    number(values, "dash_gap"), number(values, "border_width"),
                    values.get("plot_edge_block"), number(values, "plot_edge_width"),
                    Boolean.parseBoolean(values.get("show_machine_frames")), filePreset ? values.get("campus_preset_id") : "");
            validateMaterials(result);
            return result;
        } catch (ShareException invalid) {
            throw invalid;
        } catch (IOException | IllegalStateException invalid) {
            throw failure("malformed");
        } catch (IllegalArgumentException invalid) {
            throw failure("settings");
        }
    }

    private static Map<String, String> readSettings(JsonReader reader) throws IOException {
        Map<String, String> values = new HashMap<>();
        reader.beginObject();
        while (reader.hasNext()) {
            String key = reader.nextName();
            if (values.containsKey(key)) throw failure("schema");
            if (NUMBERS.contains(key)) values.put(key, Integer.toString(integer(reader)));
            else if (STRINGS.contains(key) || key.equals("campus_preset_id")) values.put(key, string(reader));
            else if (BOOLEANS.contains(key)) values.put(key, Boolean.toString(bool(reader)));
            else throw failure("schema");
        }
        reader.endObject();
        return values;
    }
    private static String string(JsonReader reader) throws IOException {
        if (reader.peek() != JsonToken.STRING) throw failure("schema");
        return reader.nextString();
    }
    private static int integer(JsonReader reader) throws IOException {
        if (reader.peek() != JsonToken.NUMBER) throw failure("schema");
        String value = reader.nextString();
        if (!value.matches("-?(0|[1-9][0-9]*)")) throw failure("schema");
        try { return Integer.parseInt(value); }
        catch (NumberFormatException invalid) { throw failure("settings"); }
    }
    private static boolean bool(JsonReader reader) throws IOException {
        if (reader.peek() != JsonToken.BOOLEAN) throw failure("schema");
        return reader.nextBoolean();
    }
    private static int number(Map<String, String> values, String key) { return Integer.parseInt(values.get(key)); }
    private static <T extends Enum<T>> T enumValue(Class<T> type, String value) {
        try { return Enum.valueOf(type, value); }
        catch (IllegalArgumentException invalid) { throw failure("enum"); }
    }
    private static void validateMaterials(PlotSettings settings) {
        validateMaterials(settings.groundBlock(), settings.plotEdgeBlock(), settings.roadBlock(), settings.borderBlock(), settings.lineBlock(), settings.markerBlock());
    }
    private static void validateMaterials(String ground, String plotEdge, String road, String border, String line, String marker) {
        for (String id : new String[] {ground, plotEdge, road, border, line})
            if (!PlotSettings.MATERIALS.contains(id)) throw failure("material");
        if (!PlotSettings.MATERIALS.contains(marker) && !PlotSettings.MARKER_LIGHTS.contains(marker))
            throw failure("material");
    }
    private static ShareException failure(String key) { return new ShareException(key); }
    public static final class ShareException extends IllegalArgumentException {
        private final String errorKey;
        private ShareException(String errorKey) { super("Invalid plot preset: " + errorKey); this.errorKey = errorKey; }
        /** Translation suffix under gtl_enhancedcore.plot_preset_share.error. */
        public String errorKey() { return errorKey; }
    }
}
