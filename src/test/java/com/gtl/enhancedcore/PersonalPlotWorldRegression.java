package com.gtl.enhancedcore;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.gtl.enhancedcore.common.world.personalplot.PersonalPlotPolicy;
import com.gtl.enhancedcore.common.world.personalplot.PersonalPlotStore;
import com.gtl.enhancedcore.common.world.personalplot.PersonalPlotDeletion;
import com.gtl.enhancedcore.common.world.plot.PlotPresetShare;
import com.gtl.enhancedcore.common.world.plot.PlotSettingsCodec;
import com.gtl.enhancedcore.common.world.plot.PlotSettings;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** Real ZIP lock, restart reads, immutable owner identity and authoritative permission checks. */
final class PersonalPlotWorldRegression {
    private static int checks;
    static int run() throws Exception {
        checks = 0;
        String storageMixin = Files.readString(Path.of("src/main/java/com/gtl/enhancedcore/mixin/mc/PersonalPlotStorageMixin.java"));
        check(storageMixin.contains("m_246049_") && storageMixin.contains("getDataTag(")
                        && storageMixin.contains("m_78290_") && storageMixin.contains("saveDataTag(")
                        && storageMixin.contains("m_6626_") && storageMixin.contains("createTag("),
                "Personal storage lifecycle hooks retain both Mojmap and runtime SRG signatures");
        String packMixin = Files.readString(Path.of("src/main/java/com/gtl/enhancedcore/mixin/mc/PersonalPlotFilePackMixin.java"));
        check(packMixin.contains("f_243662_") && packMixin.contains("failedToLoad") && packMixin.contains("close()"),
                "Personal ZIP retirement hook retains Forge runtime field and close method");
        for (boolean dedicated : new boolean[] {false, true}) for (boolean owner : new boolean[] {false, true})
            for (boolean operator : new boolean[] {false, true}) for (boolean template : new boolean[] {false, true}) {
                check(PersonalPlotPolicy.customize(dedicated, owner) == (!dedicated && owner), "Only integrated owner customizes terrain");
                check(PersonalPlotPolicy.editTemplate(dedicated, operator) == (dedicated && operator), "Only dedicated OP edits template");
                check(!PersonalPlotPolicy.create(dedicated, owner, template, true), "Already-owned world always denies another creation");
                check(PersonalPlotPolicy.create(dedicated, owner, template, false) == (dedicated ? template : owner), "LAN guest cannot create; dedicated player needs active template");
                for (boolean self : new boolean[] {false, true})
                    check(PersonalPlotPolicy.manageOwner(dedicated, owner, operator, self) == (dedicated ? self || operator : owner && self),
                            "Dedicated players manage self, OP manages others; integrated owner only manages self");
            }
        Path root = Files.createTempDirectory("gtl-personal-plots-regression-");
        UUID alice = UUID.fromString("01234567-89ab-cdef-0123-456789abcdef"), bob = UUID.fromString("fedcba98-7654-3210-fedc-ba9876543210");
        try {
            deletionLifecycle(root.resolve("deletion-lifecycle"), alice, bob);
            legacyArchives(root.resolve("legacy"), alice, bob);
            var empty = PersonalPlotStore.read(root); check(empty.template() == null && empty.worlds().isEmpty(), "Ordinary existing save starts without personal state");
            var template = PlotSettings.preset(3); var pending = empty.withTemplate(template); PersonalPlotStore.write(root, pending);
            check(PersonalPlotStore.read(root).equals(pending), "Saved template survives a process-independent read");
            var activeAtStartup = PersonalPlotStore.read(root).template();
            var aliceWorld = pending.create(alice, activeAtStartup); PersonalPlotStore.write(root, aliceWorld);
            Path alicePack = PersonalPlotStore.packPath(root, alice); byte[] originalAlice = Files.readAllBytes(alicePack);
            var newerTemplate = template.withGroundBlock("minecraft:red_concrete").withPlotChunks(8);
            var changed = aliceWorld.withTemplate(newerTemplate);
            check(changed.worlds().get(alice).equals(template), "Template changes never rewrite existing world settings");
            try (ZipFile heldOpen = new ZipFile(alicePack.toFile(), StandardCharsets.UTF_8)) {
                PersonalPlotStore.write(root, changed);
                check(PersonalPlotStore.read(root).template().equals(newerTemplate), "Template can update while Minecraft holds existing owner ZIP open");
                var bobWorld = changed.create(bob, activeAtStartup); PersonalPlotStore.write(root, bobWorld);
                check(PersonalPlotStore.read(root).equals(bobWorld), "Bob can commit while Minecraft holds Alice ZIP open on Windows");
                check(heldOpen.getEntry("pack.mcmeta") != null, "Original loaded ZIP handle remains valid");
                check(java.util.Arrays.equals(originalAlice, Files.readAllBytes(alicePack)), "Adding Bob never modifies loaded Alice archive");
                var restarted = PersonalPlotStore.read(root);
                check(restarted.template().equals(newerTemplate), "Restart sees new active template");
                check(restarted.worlds().get(bob).equals(template), "Pending template does not leak into current session creates");
                try { restarted.create(alice, newerTemplate); throw new AssertionError("Duplicate owner accepted"); }
                catch (IllegalStateException expected) { check(true, "Duplicate UUID rejected before disk changes"); }
                try { PersonalPlotStore.write(root, new PersonalPlotStore.Data(newerTemplate, java.util.Map.of(alice, newerTemplate, bob, template))); throw new AssertionError("Immutable owner changed"); }
                catch (IOException expected) { check(java.util.Arrays.equals(originalAlice, Files.readAllBytes(alicePack)), "Attempt to alter existing terrain preserves archive"); }
                String definition = "data/gtl_enhancedcore/dimension/" + PersonalPlotStore.dimensionPath(alice) + ".json";
                var json = com.google.gson.JsonParser.parseString(new String(heldOpen.getInputStream(heldOpen.getEntry(definition)).readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
                check(json.get("type").getAsString().equals("minecraft:overworld"), "Generated definition uses true overworld dimension type");
                check(json.getAsJsonObject("generator").get("type").getAsString().equals("gtl_enhancedcore:plot"), "Standard startup uses plot generator");
                var settings = json.getAsJsonObject("generator").getAsJsonObject("settings");
                var geometry = settings.getAsJsonObject("geometry_extras");
                check(geometry.get("border_width").getAsInt() == template.borderWidth()
                                && geometry.get("plot_edge_block").getAsString().equals(template.plotEdgeBlock())
                                && geometry.get("plot_edge_width").getAsInt() == template.plotEdgeWidth()
                                && !settings.has("border_width") && !settings.has("plot_edge_width"),
                        "Dimension codec receives grouped geometry settings without flattened duplicates");
                var oldSettings = com.google.gson.JsonParser.parseString("""
                        {"plot_chunks":5,"ground_block":"minecraft:light_gray_concrete","road_block":"minecraft:black_concrete",
                         "border_block":"minecraft:light_gray_concrete","marker_block":"minecraft:sea_lantern","road_style":"BOULEVARD",
                         "road_width":4,"marker_mode":"PLOT","marker_size":0,"bedrock_y":-64,"surface_y":-53,
                         "line_block":"minecraft:white_concrete","line_style":"CROSSWALK","dash_length":3,"dash_gap":3,"border_width":2}
                        """).getAsJsonObject();
                var oldDecoded = PlotSettingsCodec.CODEC.parse(JsonOps.INSTANCE, oldSettings).result().orElseThrow();
                check(oldDecoded.borderWidth() == 2 && oldDecoded.plotEdgeBlock().equals("minecraft:white_concrete")
                                && oldDecoded.plotEdgeWidth() == 0,
                        "Legacy personal plot dimension settings decode with their old border width");
                check(heldOpen.getEntry("data/minecraft/dimension/overworld.json") == null, "No original overworld override is emitted");
            }
            check(!PersonalPlotStore.dimensionPath(alice).equals(PersonalPlotStore.dimensionPath(bob)), "UUID dimension identities are distinct");
            check(PersonalPlotStore.dimensionPath(alice).matches("personal_plot/[0-9a-f]{32}"), "Dimension path is solely UUID-derived");
            byte[] validTemplate = Files.readAllBytes(root.resolve(PersonalPlotStore.TEMPLATE_FILE));
            try { PersonalPlotStore.write(root, new PersonalPlotStore.Data(template, java.util.Map.of())); throw new AssertionError("Owner removal accepted"); }
            catch (IOException expected) { check(java.util.Arrays.equals(validTemplate, Files.readAllBytes(root.resolve(PersonalPlotStore.TEMPLATE_FILE))), "Rejected transaction preserves template and worlds"); }
            Files.writeString(alicePack, "broken archive");
            try { PersonalPlotStore.read(root); throw new AssertionError("Corrupt owner ZIP ignored"); }
            catch (IOException expected) { check(Files.readString(alicePack).equals("broken archive"), "Corrupt owner fails closed without resetting identity"); }
            try { PersonalPlotStore.write(root, pending); throw new AssertionError("Corrupt owner overwritten"); }
            catch (IOException expected) { check(Files.readString(alicePack).equals("broken archive"), "Corrupt owner cannot be silently recreated"); }
            Files.write(alicePack, originalAlice); Files.writeString(root.resolve(PersonalPlotStore.TEMPLATE_FILE), "invalid template");
            try { PersonalPlotStore.write(root, pending); throw new AssertionError("Malformed template overwritten"); }
            catch (IOException expected) { check(Files.readString(root.resolve(PersonalPlotStore.TEMPLATE_FILE)).equals("invalid template"), "Malformed template fails closed and remains untouched"); }
            try (var files = Files.walk(root)) { check(files.noneMatch(p -> p.getFileName().toString().endsWith(".tmp")), "All completed and failed writes clean temporary files"); }
            check(PersonalPlotStore.dimensionJson(PlotSettings.preset(1).withSurfaceY(319)).get("type").getAsString().equals("minecraft:overworld"), "Full requested build-height customization remains supported");
        } finally {
            try (var files = Files.walk(root)) { for (Path path : files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path); }
        }
        return checks;
    }
    private static void deletionLifecycle(Path root, UUID alice, UUID bob) throws Exception {
        var settings = PlotSettings.preset(3);
        PersonalPlotStore.write(root, new PersonalPlotStore.Data(null, java.util.Map.of(alice, settings)));
        PersonalPlotStore.write(root, PersonalPlotStore.read(root).create(bob, settings));
        Path dimension = PersonalPlotStore.dimensionDirectory(root, alice);
        Files.createDirectories(dimension.resolve("region")); Files.writeString(dimension.resolve("region/r.0.0.mca"), "old terrain");
        Path otherDimension = PersonalPlotStore.dimensionDirectory(root, bob);
        Files.createDirectories(otherDimension); Files.writeString(otherDimension.resolve("keep.txt"), "unrelated owner");
        byte[] bobPack = Files.readAllBytes(PersonalPlotStore.packPath(root, bob));
        PersonalPlotStore.beginDeletion(root, alice);
        PersonalPlotStore.beginDeletion(root, alice);
        check(PersonalPlotStore.deletions(root).equals(java.util.Set.of(alice)), "Durable deletion marker is idempotent and preserves UUID identity");
        check(!PersonalPlotStore.read(root).worlds().containsKey(alice), "Restart reads cannot resurrect a tombstoned owner from its remaining ZIP");
        try { PersonalPlotStore.write(root, PersonalPlotStore.read(root).create(alice, settings)); throw new AssertionError("Deletion with residual ZIP was recreated"); }
        catch (IOException expected) { check(Files.exists(PersonalPlotStore.packPath(root, alice)), "Residual archive blocks recreation until cleanup completes"); }
        PersonalPlotDeletion.cleanup(root, alice);
        check(!Files.exists(dimension) && !Files.exists(PersonalPlotStore.packPath(root, alice)), "Cleanup removes both exact dimension directory and owner archive");
        check(Files.readString(otherDimension.resolve("keep.txt")).equals("unrelated owner")
                        && java.util.Arrays.equals(bobPack, Files.readAllBytes(PersonalPlotStore.packPath(root, bob))),
                "Deleting one owner preserves the other owner's chunks and ZIP byte for byte");
        check(PersonalPlotStore.deletions(root).contains(alice), "Completed deletion keeps tombstone so old level.dat definitions cannot reappear");
        var replacement = settings.withGroundBlock("minecraft:red_concrete");
        PersonalPlotStore.write(root, PersonalPlotStore.read(root).create(alice, replacement));
        check(!PersonalPlotStore.deletions(root).contains(alice) && PersonalPlotStore.read(root).worlds().get(alice).equals(replacement),
                "Explicit recreation publishes new settings and clears deletion tombstone");
        check(!Files.exists(dimension), "Recreation cannot inherit the deleted terrain directory");
    }
    private static void legacyArchives(Path root, UUID alice, UUID bob) throws Exception {
        // Captured v1 wire shapes: the owner preset and dimension definition are separate contracts.
        String preset = Files.readString(Path.of("src/test/resources/personal-plot/v1-preset.json"));
        JsonObject definition = JsonParser.parseString(Files.readString(Path.of("src/test/resources/personal-plot/v1-dimension.json"))).getAsJsonObject();
        PlotSettings expected = new PlotSettings(5, "minecraft:light_gray_concrete", "minecraft:black_concrete",
                "minecraft:light_gray_concrete", "minecraft:sea_lantern", PlotSettings.RoadStyle.BOULEVARD, 4,
                PlotSettings.MarkerMode.PLOT, 0, -64, -53, "minecraft:white_concrete", PlotSettings.LineStyle.CROSSWALK, 3, 3, 2);
        JsonObject manifest = new JsonObject(); manifest.addProperty("version", 1); manifest.addProperty("owner", alice.toString());
        manifest.addProperty("settings", preset);
        Path pack = PersonalPlotStore.packPath(root, alice);
        writeFixture(pack, alice, manifest, definition);
        byte[] original = Files.readAllBytes(pack);
        check(PersonalPlotStore.read(root).worlds().get(alice).equals(expected), "Complete v1 owner ZIP restores all original terrain fields and default absent edges");
        check(java.util.Arrays.equals(original, Files.readAllBytes(pack)), "Reading a legacy archive never rewrites its bytes");
        try { PlotPresetShare.decode(preset); throw new AssertionError("Legacy interchange accepted on current UI/network decoder"); }
        catch (PlotPresetShare.ShareException expectedFailure) { check(true, "Legacy support is limited to persisted data, current interchange stays strict"); }
        Path template = root.resolve(PersonalPlotStore.TEMPLATE_FILE); Files.writeString(template, preset);
        check(PersonalPlotStore.read(root).template().equals(expected), "V1 dedicated-server template restores without a manual rewrite");
        byte[] oldTemplate = Files.readAllBytes(template);
        try (ZipFile heldOpen = new ZipFile(pack.toFile(), StandardCharsets.UTF_8)) {
            var mixed = PersonalPlotStore.read(root).create(bob, PlotSettings.preset(2));
            PersonalPlotStore.write(root, mixed);
            check(PersonalPlotStore.read(root).equals(mixed), "New v2 owners coexist with v1 owners after upgrade");
            check(java.util.Arrays.equals(oldTemplate, Files.readAllBytes(template)), "Creating another owner leaves old template bytes unchanged");
            PersonalPlotStore.write(root, mixed.withTemplate(PlotSettings.preset(4)));
            check(PersonalPlotStore.read(root).worlds().get(alice).equals(expected), "Template changes retain original v1 terrain after restart read");
            check(java.util.Arrays.equals(original, Files.readAllBytes(pack)) && heldOpen.getEntry("pack.mcmeta") != null,
                    "Legacy owner remains immutable while its ZIP is held open on Windows");
        }
        for (Consumer<JsonObject> change : java.util.List.<Consumer<JsonObject>>of(
                json -> json.addProperty("type", "minecraft:the_nether"),
                json -> json.getAsJsonObject("generator").addProperty("type", "minecraft:flat"),
                json -> json.getAsJsonObject("generator").addProperty("biome", "minecraft:desert"),
                json -> json.getAsJsonObject("generator").getAsJsonObject("settings").addProperty("border_width", 3),
                json -> json.getAsJsonObject("generator").getAsJsonObject("settings").remove("border_width"),
                json -> json.getAsJsonObject("generator").getAsJsonObject("settings").addProperty("unknown", 1),
                json -> json.getAsJsonObject("generator").getAsJsonObject("settings").add("geometry_extras", new JsonObject()),
                json -> json.addProperty("unexpected", true))) {
            JsonObject invalid = definition.deepCopy(); change.accept(invalid);
            writeFixture(pack, alice, manifest, invalid); rejectFixture(root, pack);
        }
        for (Consumer<JsonObject> change : java.util.List.<Consumer<JsonObject>>of(
                json -> json.addProperty("owner", bob.toString()),
                json -> json.addProperty("version", 2),
                json -> json.addProperty("settings", preset.replace("\"version\": 1", "\"version\": 100")),
                json -> json.addProperty("settings", preset.replace("\"border_width\": 2", "\"border_width\": 2, \"border_width\": 2")),
                json -> json.addProperty("settings", preset.replace("\"border_width\": 2", "\"plot_edge_width\": 2")),
                json -> json.addProperty("settings", preset.replace("\"border_width\": 2", "\"border_width\": 9")),
                json -> json.addProperty("settings", preset.replace("minecraft:black_concrete", "minecraft:command_block")),
                json -> json.addProperty("settings", preset.replace("\"road_width\": 4", "\"road_width\": \"4\"")))) {
            JsonObject invalid = manifest.deepCopy(); change.accept(invalid);
            writeFixture(pack, alice, invalid, definition); rejectFixture(root, pack);
        }
        JsonObject newerManifest = manifest.deepCopy(); newerManifest.addProperty("settings", PlotPresetShare.encode(expected.withPlotEdgeWidth(1)));
        writeFixture(pack, alice, newerManifest, definition); rejectFixture(root, pack);
        writeFixture(pack, alice, manifest, definition);
        check(PersonalPlotStore.read(root).worlds().get(alice).equals(expected), "Valid v1 archive can be read again after failed validation attempts");
    }
    private static void rejectFixture(Path root, Path pack) throws Exception {
        byte[] before = Files.readAllBytes(pack);
        try { PersonalPlotStore.read(root); throw new AssertionError("Invalid legacy archive accepted"); }
        catch (IOException expected) { check(java.util.Arrays.equals(before, Files.readAllBytes(pack)), "Invalid legacy archives fail closed and remain untouched"); }
    }
    private static void writeFixture(Path path, UUID owner, JsonObject manifest, JsonObject definition) throws Exception {
        Files.createDirectories(path.getParent());
        try (var zip = new ZipOutputStream(Files.newOutputStream(path), StandardCharsets.UTF_8)) {
            for (var entry : java.util.Map.of("pack.mcmeta", "{\"pack\":{\"pack_format\":15,\"description\":\"Legacy fixture\"}}",
                    "gtl_personal_plot_owner.json", manifest.toString(),
                    "data/gtl_enhancedcore/dimension/" + PersonalPlotStore.dimensionPath(owner) + ".json", definition.toString()).entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey())); zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
            }
        }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); checks++; }
}
