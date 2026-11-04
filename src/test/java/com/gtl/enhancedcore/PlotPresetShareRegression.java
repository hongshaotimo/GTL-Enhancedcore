package com.gtl.enhancedcore;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.gtl.enhancedcore.common.world.plot.PlotPresetShare;
import com.gtl.enhancedcore.common.world.plot.PlotSettings;
import java.util.function.Consumer;

/** Untrusted shared text is bounded and validated before replacing a local creation draft. */
final class PlotPresetShareRegression {
    private static int checks;
    static int run() {
        checks = 0;
        for (int chunks = 1; chunks <= 11; chunks++) {
            PlotSettings source = PlotSettings.defaults().withPlotChunks(chunks);
            check(PlotPresetShare.decode(PlotPresetShare.encode(source)).equals(source), "Every size survives text sharing");
        }
        PlotSettings custom = new PlotSettings(11, "minecraft:pink_concrete", "minecraft:blue_concrete",
                "minecraft:orange_concrete", "minecraft:glowstone", PlotSettings.RoadStyle.AVENUE, 32,
                PlotSettings.MarkerMode.CHUNK, 5, 250, 319, "minecraft:yellow_concrete",
                PlotSettings.LineStyle.DOUBLE, 31, 32, 8);
        String encoded = PlotPresetShare.encode(custom);
        check(PlotPresetShare.decode(encoded).equals(custom), "All sixteen custom fields survive interchange");
        check(PlotPresetShare.decodeStored(encoded).equals(custom), "Current stored presets use the same complete schema as current interchange");
        check(PlotPresetShare.encode(PlotPresetShare.decode(encoded)).equals(encoded), "Export is deterministic");
        check(encoded.length() < PlotPresetShare.MAX_TEXT_LENGTH, "Complete presets fit the bounded editor");
        check(PlotPresetShare.decode("  \n" + encoded + "\r\n").equals(custom), "Pasted whitespace is harmless");
        PlotSettings edgeCustom = custom.withPlotEdgeBlock("minecraft:cyan_concrete").withPlotEdgeWidth(7);
        check(PlotPresetShare.decode(PlotPresetShare.encode(edgeCustom)).equals(edgeCustom),
                "Plot edge material and width survive share roundtrip");
        for (boolean frames : new boolean[] {false, true}) {
            PlotSettings campus = PlotSettings.plannedCampus().withMachineFrames(frames);
            String share = PlotPresetShare.encode(campus);
            JsonObject root = JsonParser.parseString(share).getAsJsonObject();
            check(root.get("version").getAsInt() == 3, "Campus frame choice uses v3 interchange");
            check(root.getAsJsonObject("settings").get("show_machine_frames").getAsBoolean() == frames,
                    "The frame choice is an explicit boolean in both share states");
            check(PlotPresetShare.decode(share).equals(campus) && PlotPresetShare.decodeStored(share).equals(campus),
                    "Share and stored decoders preserve the frame choice");
            check(PlotPresetShare.encode(PlotPresetShare.decode(share)).equals(share),
                    "Campus frame share is canonical in both states");
        }
        check(!PlotSettings.plannedCampus().showMachineFrames() && !PlotSettings.defaults().showMachineFrames(),
                "New presets default support frames off");
        PlotSettings framed = PlotSettings.plannedCampus().withMachineFrames(true);
        check(framed.withSurfaceY(framed.surfaceY()).withGroundBlock("minecraft:cyan_concrete")
                        .withPlotEdgeWidth(2).showMachineFrames(),
                "Editing terrain fields preserves the selected frame choice");
        check(framed.withRoadPreset(PlotSettings.RoadPreset.CITY).showMachineFrames(),
                "Changing a road theme preserves the selected frame choice");
        for (var road : PlotSettings.RoadStyle.values()) for (var line : PlotSettings.LineStyle.values()) {
            var campus = PlotSettings.plannedCampus().withRoadStyle(road);
            // Every fixed campus needs foundation and height headroom, including woodland.
            var settings = (campus.isCampus() ? campus : custom.withRoadStyle(road)).withLineStyle(line);
            check(PlotPresetShare.decode(PlotPresetShare.encode(settings)).equals(settings), "All supported road and lane enums roundtrip");
        }
        for (String color : PlotSettings.CONCRETE_COLORS) {
            String id = "minecraft:" + color + "_concrete";
            var settings = custom.withGroundBlock(id).withRoadBlock(id).withBorderBlock(id).withLineBlock(id).withMarkerBlock(id);
            check(PlotPresetShare.decode(PlotPresetShare.encode(settings)).equals(settings), "Every concrete selection can be shared");
        }
        reject(null, "empty"); reject("  \n ", "empty"); reject("a".repeat(PlotPresetShare.MAX_TEXT_LENGTH + 1), "too_long");
        reject("{", "malformed"); reject(encoded + " true", "malformed"); reject("[]", "malformed");
        mutate(encoded, root -> root.addProperty("version", 100), "version");
        mutate(encoded, root -> root.addProperty("format", "foreign:preset"), "schema");
        mutate(encoded, root -> root.addProperty("unknown", 1), "schema");
        mutate(encoded, root -> root.remove("version"), "schema");
        mutateSettings(encoded, values -> values.addProperty("extra", 1), "schema");
        mutateSettings(encoded, values -> values.remove("dash_gap"), "schema");
        mutateSettings(encoded, values -> values.addProperty("plot_chunks", "11"), "schema");
        mutateSettings(encoded, values -> values.addProperty("road_style", "INVALID"), "enum");
        mutateSettings(encoded, values -> values.addProperty("marker_mode", "plot"), "enum");
        mutateSettings(encoded, values -> values.addProperty("line_style", "INVALID"), "enum");
        reject(encoded.replace("\"version\": 3", "\"version\": 3, \"version\": 3"), "schema");
        reject(encoded.replace("\"plot_chunks\": 11", "\"plot_chunks\": 11, \"plot_chunks\": 11"), "schema");
        reject(encoded.replace("\"show_machine_frames\": false", "\"show_machine_frames\": false, \"show_machine_frames\": true"), "schema");
        mutateSettings(encoded, values -> values.remove("show_machine_frames"), "schema");
        mutateSettings(encoded, values -> values.addProperty("show_machine_frames", "true"), "schema");
        mutateSettings(encoded, values -> values.addProperty("show_machine_frames", 1), "schema");
        mutateSettings(encoded, values -> values.add("show_machine_frames", com.google.gson.JsonNull.INSTANCE), "schema");
        for (String number : new String[] {"11.0", "1e1", "2147483648"})
            reject(encoded.replace("\"plot_chunks\": 11", "\"plot_chunks\": " + number), number.equals("2147483648") ? "settings" : "schema");
        for (String key : new String[] {"plot_chunks", "road_width", "marker_size", "dash_length", "dash_gap", "border_width", "bedrock_y", "surface_y"}) {
            int bad = switch (key) { case "plot_chunks" -> 12; case "road_width", "dash_length", "dash_gap" -> 33;
                case "marker_size" -> 6; case "border_width" -> 9; case "bedrock_y" -> -65; default -> 320; };
            mutateSettings(encoded, values -> values.addProperty(key, bad), "settings");
        }
        for (String key : new String[] {"ground_block", "road_block", "border_block", "line_block"})
            mutateSettings(encoded, values -> values.addProperty(key, "minecraft:stone_bricks"), "material");
        mutateSettings(encoded, values -> values.addProperty("marker_block", "minecraft:command_block"), "material");
        mutateSettings(encoded, values -> values.addProperty("ground_block", "minecraft:sea_lantern"), "material");
        mutateSettings(encoded, values -> values.addProperty("surface_y", 250), "settings");
        mutateSettings(encoded, values -> values.add("road_width", new JsonObject()), "schema");
        mutateSettings(encoded, values -> values.addProperty("line_block", false), "schema");
        check(PlotPresetShare.decode(PlotPresetShare.encode(PlotSettings.defaults())).markerSize() == 0,
                "Automatic parity marker mode remains automatic after sharing");
        return checks;
    }
    private static void mutate(String text, Consumer<JsonObject> mutation, String expected) {
        JsonObject root = JsonParser.parseString(text).getAsJsonObject(); mutation.accept(root); reject(root.toString(), expected);
    }
    private static void mutateSettings(String text, Consumer<JsonObject> mutation, String expected) {
        mutate(text, root -> mutation.accept(root.getAsJsonObject("settings")), expected);
    }
    private static void reject(String text, String expected) {
        for (boolean stored : new boolean[] {false, true}) {
            try {
                if (stored) PlotPresetShare.decodeStored(text); else PlotPresetShare.decode(text);
                throw new AssertionError("Invalid shared or stored text must be rejected");
            } catch (PlotPresetShare.ShareException failure) {
                check(expected == null || expected.equals(failure.errorKey()), "Expected import error " + expected + ", got " + failure.errorKey());
            }
        }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); checks++; }
}
