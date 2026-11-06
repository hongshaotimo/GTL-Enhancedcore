package com.gtl.enhancedcore.common.world.plot;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.zip.GZIPInputStream;

/** Published campus geometry remains fixed when its active machine catalog changes. */
final class FrozenCampusLayouts {
    private FrozenCampusLayouts() {}

    static <T> T load(String name, String catalogResource, Class<T> type) {
        String resource = "/data/gtl_enhancedcore/worldgen/" + name;
        try (var catalog = FrozenCampusLayouts.class.getResourceAsStream(catalogResource);
             var stream = FrozenCampusLayouts.class.getResourceAsStream(resource)) {
            if (catalog == null || stream == null) throw new IllegalStateException("Missing frozen campus resources: " + name);
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(catalog.readAllBytes()));
            try (var reader = new InputStreamReader(new GZIPInputStream(stream), StandardCharsets.UTF_8)) {
                var root = JsonParser.parseReader(reader).getAsJsonObject();
                if (root.get("schemaVersion").getAsInt() != 1 || !hash.equals(root.get("catalogSha256").getAsString()))
                    throw new IllegalStateException("Frozen campus catalog differs from its authored geometry: " + name);
                return new Gson().fromJson(root.getAsJsonObject("plan"), type);
            }
        } catch (IOException | NoSuchAlgorithmException error) {
            throw new IllegalStateException("Cannot load frozen campus layout: " + name, error);
        }
    }
}
