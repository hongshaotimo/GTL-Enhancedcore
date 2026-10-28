package com.gtl.enhancedcore;

import com.gtl.enhancedcore.common.world.personalplot.PersonalPlotPolicy;
import com.gtl.enhancedcore.common.world.personalplot.PersonalPlotStore;
import com.gtl.enhancedcore.common.world.plot.PlotSettingsCodec;
import com.gtl.enhancedcore.common.world.plot.PlotSettings;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.zip.ZipFile;

/** Real ZIP lock, restart reads, immutable owner identity and authoritative permission checks. */
final class PersonalPlotWorldRegression {
    private static int checks;
    static int run() throws Exception {
        checks = 0;
        for (boolean dedicated : new boolean[] {false, true}) for (boolean owner : new boolean[] {false, true})
            for (boolean operator : new boolean[] {false, true}) for (boolean template : new boolean[] {false, true}) {
                check(PersonalPlotPolicy.customize(dedicated, owner) == (!dedicated && owner), "Only integrated owner customizes terrain");
                check(PersonalPlotPolicy.editTemplate(dedicated, operator) == (dedicated && operator), "Only dedicated OP edits template");
                check(!PersonalPlotPolicy.create(dedicated, owner, template, true), "Already-owned world always denies another creation");
                check(PersonalPlotPolicy.create(dedicated, owner, template, false) == (dedicated ? template : owner), "LAN guest cannot create; dedicated player needs active template");
            }
        Path root = Files.createTempDirectory("gtl-personal-plots-regression-");
        UUID alice = UUID.fromString("01234567-89ab-cdef-0123-456789abcdef"), bob = UUID.fromString("fedcba98-7654-3210-fedc-ba9876543210");
        try {
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
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); checks++; }
}
