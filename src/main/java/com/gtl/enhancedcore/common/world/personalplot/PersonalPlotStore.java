package com.gtl.enhancedcore.common.world.personalplot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.gtl.enhancedcore.common.world.plot.PlotPresetShare;
import com.gtl.enhancedcore.common.world.plot.PlotSettings;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** Immutable UUID data packs avoid replacing ZIPs held open by Minecraft on Windows. */
public final class PersonalPlotStore {
    public static final int MAX_WORLDS = 2048;
    public static final String TEMPLATE_FILE = "gtl_personal_plot_template.json";
    public static final String PACK_PREFIX = "gtl_personal_plot_";
    private static final String MANIFEST = "gtl_personal_plot_owner.json";
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    public record Data(PlotSettings template, Map<UUID, PlotSettings> worlds) {
        public Data { worlds = Map.copyOf(worlds); if (worlds.size() > MAX_WORLDS) throw new IllegalArgumentException("World limit"); }
        public Data withTemplate(PlotSettings value) { return new Data(value, worlds); }
        public Data create(UUID owner, PlotSettings value) {
            if (worlds.containsKey(owner)) throw new IllegalStateException("Player already owns a world");
            Map<UUID, PlotSettings> copy = new LinkedHashMap<>(worlds); copy.put(owner, value); return new Data(template, copy);
        }
    }
    private PersonalPlotStore() {}
    private static String ownerName(UUID owner) { return owner.toString().replace("-", ""); }
    public static String dimensionPath(UUID owner) { return "personal_plot/" + ownerName(owner); }
    public static Path packPath(Path root, UUID owner) { return root.resolve("datapacks").resolve(PACK_PREFIX + ownerName(owner) + ".zip"); }
    public static Data read(Path root) throws IOException {
        PlotSettings template = null; Path templatePath = root.resolve(TEMPLATE_FILE);
        if (Files.exists(templatePath)) template = decodePreset(readBounded(templatePath, PlotPresetShare.MAX_TEXT_LENGTH * 4));
        Map<UUID, PlotSettings> worlds = new LinkedHashMap<>(); Path packs = root.resolve("datapacks");
        if (Files.isDirectory(packs)) try (var files = Files.newDirectoryStream(packs, PACK_PREFIX + "*.zip")) {
            for (Path file : files) {
                if (worlds.size() >= MAX_WORLDS) throw new IOException("Too many personal worlds");
                String name = file.getFileName().toString(), hex = name.substring(PACK_PREFIX.length(), name.length() - 4);
                if (!hex.matches("[0-9a-f]{32}")) throw new IOException("Invalid personal plot pack filename: " + name);
                UUID owner = UUID.fromString(hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-" + hex.substring(12, 16) + "-" + hex.substring(16, 20) + "-" + hex.substring(20));
                worlds.put(owner, readPack(file, owner));
            }
        }
        return new Data(template, worlds);
    }
    /** Each invocation changes one template or one new owner, never two commit boundaries. */
    public static void write(Path root, Data data) throws IOException {
        Data current = read(root);
        for (var existing : current.worlds.entrySet()) if (!existing.getValue().equals(data.worlds.get(existing.getKey())))
            throw new IOException("Existing personal worlds are immutable");
        Map<UUID, PlotSettings> additions = new LinkedHashMap<>(data.worlds); current.worlds.keySet().forEach(additions::remove);
        boolean templateChanged = !java.util.Objects.equals(current.template, data.template);
        if (additions.size() > 1 || templateChanged && !additions.isEmpty()) throw new IOException("Personal plot updates require one atomic operation");
        Files.createDirectories(root);
        if (templateChanged) {
            if (data.template == null) throw new IOException("Removing the server template is not supported");
            Path temporary = Files.createTempFile(root, ".personal-template-", ".tmp");
            boolean committed = false; IOException failure = null;
            try {
                Files.writeString(temporary, PlotPresetShare.encode(data.template), StandardCharsets.UTF_8); force(temporary);
                if (!decodePreset(readBounded(temporary, PlotPresetShare.MAX_TEXT_LENGTH * 4)).equals(data.template)) throw new IOException("Template verification failed");
                try { Files.move(temporary, root.resolve(TEMPLATE_FILE), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                catch (AtomicMoveNotSupportedException unsupported) { throw new IOException("Template storage requires atomic replacement", unsupported); }
                committed = true;
            } catch (IOException error) { failure = error; throw error; }
            finally { cleanupTemporary(temporary, committed, failure); }
        }
        for (var addition : additions.entrySet()) writePack(root, addition.getKey(), addition.getValue());
    }
    private static void writePack(Path root, UUID owner, PlotSettings settings) throws IOException {
        Path target = packPath(root, owner); Files.createDirectories(target.getParent());
        if (Files.exists(target)) throw new IOException("Personal plot pack already exists; refusing overwrite");
        Path temporary = Files.createTempFile(target.getParent(), ".personal-plot-", ".tmp");
        boolean committed = false; IOException failure = null;
        try {
            try (var zip = new ZipOutputStream(Files.newOutputStream(temporary), StandardCharsets.UTF_8)) {
                JsonObject pack = new JsonObject(), metadata = new JsonObject(); metadata.addProperty("pack_format", 15);
                metadata.addProperty("description", "GTL personal dimension " + owner); pack.add("pack", metadata); put(zip, "pack.mcmeta", GSON.toJson(pack));
                JsonObject manifest = new JsonObject(); manifest.addProperty("version", 1); manifest.addProperty("owner", owner.toString());
                manifest.addProperty("settings", PlotPresetShare.encode(settings)); put(zip, MANIFEST, GSON.toJson(manifest));
                put(zip, dimensionEntry(owner), GSON.toJson(dimensionJson(settings)));
            }
            force(temporary);
            if (!readPack(temporary, owner).equals(settings)) throw new IOException("Personal plot pack verification failed");
            // Atomic publication with no overwrite. ATOMIC_MOVE alone does not guarantee no replacement.
            // Unsupported filesystems fail safely; no existing world's ZIP is ever reopened for writing.
            Files.createLink(target, temporary);
            committed = true;
        } catch (IOException error) { failure = error; throw error; }
        finally { cleanupTemporary(temporary, committed, failure); }
    }
    private static PlotSettings readPack(Path archive, UUID owner) throws IOException {
        try (ZipFile zip = new ZipFile(archive.toFile(), StandardCharsets.UTF_8)) {
            JsonObject manifest = JsonParser.parseString(readEntry(zip, MANIFEST)).getAsJsonObject();
            if (manifest.get("version").getAsInt() != 1 || !owner.toString().equals(manifest.get("owner").getAsString())) throw new IOException("Personal plot owner mismatch");
            PlotSettings settings = decodePreset(manifest.get("settings").getAsString());
            if (!JsonParser.parseString(readEntry(zip, dimensionEntry(owner))).equals(dimensionJson(settings))) throw new IOException("Personal plot definition mismatch");
            return settings;
        } catch (RuntimeException invalid) { throw new IOException("Invalid personal plot pack; preserving owner identity", invalid); }
    }
    private static String readEntry(ZipFile zip, String name) throws IOException {
        ZipEntry entry = zip.getEntry(name);
        if (entry == null || entry.getSize() < 0 || entry.getSize() > 16384) throw new IOException("Invalid personal plot entry");
        try (var stream = zip.getInputStream(entry)) {
            byte[] bytes = stream.readNBytes(16385); if (bytes.length > 16384) throw new IOException("Personal plot entry too large");
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }
    private static String readBounded(Path path, int maximum) throws IOException {
        if (Files.size(path) > maximum) throw new IOException("Personal plot file too large");
        try (var stream = Files.newInputStream(path)) {
            byte[] bytes = stream.readNBytes(maximum + 1); if (bytes.length > maximum) throw new IOException("Personal plot file too large");
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }
    private static PlotSettings decodePreset(String text) throws IOException {
        try { return PlotPresetShare.decode(text); } catch (RuntimeException invalid) { throw new IOException("Invalid personal plot settings", invalid); }
    }
    private static void force(Path path) throws IOException { try (FileChannel file = FileChannel.open(path, StandardOpenOption.WRITE)) { file.force(true); } }
    private static void cleanupTemporary(Path temporary, boolean committed, IOException failure) throws IOException {
        try { Files.deleteIfExists(temporary); }
        catch (IOException cleanup) {
            if (committed) System.getLogger(PersonalPlotStore.class.getName()).log(System.Logger.Level.WARNING,
                    "Personal plot commit succeeded, but a temporary file remains: " + temporary, cleanup);
            else if (failure != null) failure.addSuppressed(cleanup);
            else throw cleanup;
        }
    }
    private static void put(ZipOutputStream zip, String name, String value) throws IOException {
        zip.putNextEntry(new ZipEntry(name)); zip.write(value.getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
    }
    private static String dimensionEntry(UUID owner) { return "data/gtl_enhancedcore/dimension/" + dimensionPath(owner) + ".json"; }
    public static JsonObject dimensionJson(PlotSettings settings) {
        JsonObject root = new JsonObject(), generator = new JsonObject(); root.addProperty("type", "minecraft:overworld");
        generator.addProperty("type", "gtl_enhancedcore:plot"); generator.addProperty("biome", "minecraft:plains");
        JsonObject encoded = JsonParser.parseString(PlotPresetShare.encode(settings)).getAsJsonObject().getAsJsonObject("settings");
        JsonObject geometry = new JsonObject();
        geometry.add("border_width", encoded.remove("border_width"));
        geometry.add("plot_edge_block", encoded.remove("plot_edge_block"));
        geometry.add("plot_edge_width", encoded.remove("plot_edge_width"));
        encoded.add("geometry_extras", geometry);
        generator.add("settings", encoded);
        root.add("generator", generator); return root;
    }
}
