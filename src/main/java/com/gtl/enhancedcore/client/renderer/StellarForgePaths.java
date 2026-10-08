package com.gtl.enhancedcore.client.renderer;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.joml.Vector3f;

/** Fixed optical paths verified against the production structure; contains no world mutations. */
public final class StellarForgePaths {
    public record Wave(float delay, List<Vector3f> path) {}
    public static final List<List<Vector3f>> ARCS, FEEDS, CHANNELS;
    public static final List<Wave> WAVES;
    static {
        try (var stream = StellarForgePaths.class.getResourceAsStream("/assets/gtl_enhancedcore/stellar_forge_paths.json")) {
            if (stream == null) throw new IllegalStateException("Missing stellar forge paths");
            var data = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            ARCS = paths(data.getAsJsonArray("arcs")); FEEDS = paths(data.getAsJsonArray("feeds"));
            CHANNELS = paths(data.getAsJsonArray("channels"));
            var waves = new ArrayList<Wave>();
            for (var item : data.getAsJsonArray("waves")) {
                var wave = item.getAsJsonObject();
                waves.add(new Wave(wave.get("delay").getAsFloat(), path(wave.getAsJsonArray("path"))));
            }
            WAVES = List.copyOf(waves);
        } catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
    }
    private StellarForgePaths() {}
    private static List<List<Vector3f>> paths(JsonArray values) {
        var result = new ArrayList<List<Vector3f>>();
        for (var value : values) result.add(path(value.getAsJsonArray()));
        return List.copyOf(result);
    }
    private static List<Vector3f> path(JsonArray values) {
        var result = new ArrayList<Vector3f>();
        for (var value : values) {
            var p = value.getAsJsonArray();
            result.add(new Vector3f(p.get(0).getAsFloat(),p.get(1).getAsFloat(),p.get(2).getAsFloat()));
        }
        return List.copyOf(result);
    }
}
