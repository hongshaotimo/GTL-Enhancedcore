package com.gtl.enhancedcore;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.gtl.enhancedcore.common.world.personalplot.PersonalPlotStore;
import com.gtl.enhancedcore.common.world.plot.CompactPlannedCampus;
import com.gtl.enhancedcore.common.world.plot.DensePlannedCampus;
import com.gtl.enhancedcore.common.world.plot.PlannedCampus;
import com.gtl.enhancedcore.common.world.plot.PlotLayout;
import com.gtl.enhancedcore.common.world.plot.PlotPresetShare;
import com.gtl.enhancedcore.common.world.plot.PlotSettings;
import com.gtl.enhancedcore.common.world.plot.PlotSettingsCodec;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** The campus is a new immutable UUID world recipe, including across ordinary-world template changes. */
final class CampusPresetPersistenceRegression {
    private static int checks;

    static int run() throws Exception {
        checks = 0;
        check(!PlotSettings.plannedCampus().showMachineFrames(), "New campus defaults support frames off");
        check(PlotSettings.plannedCampus().roadStyle() == PlotSettings.RoadStyle.CAMPUS_WOODLAND
                        && PlotSettings.defaults().withRoadPreset(PlotSettings.RoadPreset.CAMPUS).equals(PlotSettings.plannedCampus()),
                "Both campus selection paths choose the current woodland identity");
        check(PlotSettings.RoadStyle.CAMPUS.ordinal() == 7 && PlotSettings.RoadStyle.CAMPUS_PARK.ordinal() == 8
                        && PlotSettings.RoadStyle.CAMPUS_COMPACT.ordinal() == 9 && PlotSettings.RoadStyle.CAMPUS_DENSE.ordinal() == 10
                        && PlotSettings.RoadStyle.CAMPUS_WOODLAND.ordinal() == 11,
                "Appending new presets cannot move the five saved campus ordinals");
        for (var style : new PlotSettings.RoadStyle[] {PlotSettings.RoadStyle.CAMPUS, PlotSettings.RoadStyle.CAMPUS_PARK,
                PlotSettings.RoadStyle.CAMPUS_COMPACT, PlotSettings.RoadStyle.CAMPUS_DENSE, PlotSettings.RoadStyle.CAMPUS_WOODLAND}) {
            PlotSettings campus = PlotSettings.plannedCampus().withRoadStyle(style);
            check(campus.isCampus() && PlotPresetShare.decode(PlotPresetShare.encode(campus)).roadStyle() == style,
                    "All five campus generation identities remain distinct through sharing");
            reject(() -> campus.withSurfaceY(319), "All campus identities retain the height limit");
            reject(() -> campus.withLevels(-60, -53), "All campus identities retain the minimum foundation depth");
        }
        PlotSettings compact = PlotSettings.plannedCampus().withRoadStyle(PlotSettings.RoadStyle.CAMPUS_COMPACT);
        check(CompactPlannedCampus.CATALOG.equals(DensePlannedCampus.CATALOG)
                        && CompactPlannedCampus.CATALOG.endsWith("planned_campus_machines_v3.json")
                        && !CompactPlannedCampus.CATALOG.equals(PlannedCampus.CATALOG),
                "Frozen Compact/Dense layouts read their own unchanged catalog before current reservations change");
        check(compact.campusPlotCount() == CompactPlannedCampus.plots().size(),
                "The saved compact identity counts the frozen compact machine slots");
        for (var plot : CompactPlannedCampus.plots())
            check(PlotLayout.surfaceBlock(compact, plot.controllerX(), plot.controllerZ())
                            .equals(CompactPlannedCampus.surfaceBlock(compact, plot.controllerX(), plot.controllerZ())),
                    "The saved compact identity samples its frozen terrain at every controller");
        roundTrip(false);
        roundTrip(true);
        rejectMismatchedFrameDefinition(false);
        rejectMismatchedFrameDefinition(true);
        return checks;
    }

    private static void roundTrip(boolean frames) throws Exception {
        PlotSettings campus = PlotSettings.plannedCampus().withMachineFrames(frames);
        String shared = PlotPresetShare.encode(campus);
        JsonObject envelope = JsonParser.parseString(shared).getAsJsonObject();
        check(envelope.get("version").getAsInt() == 3, "Campus uses current v3 interchange");
        check(envelope.getAsJsonObject("settings").get("show_machine_frames").getAsBoolean() == frames,
                "Campus share records the selected frame state explicitly");
        check(PlotPresetShare.decode(shared).equals(campus), "Sharing preserves every campus terrain field");
        check(PlotPresetShare.decodeStored(shared).equals(campus), "Stored campus uses the same complete preset identity");
        check(PlotPresetShare.encode(PlotPresetShare.decode(shared)).equals(shared), "Campus share export is canonical");
        check(campus.isCampus() && campus.roadStyle() == PlotSettings.RoadStyle.CAMPUS_WOODLAND,
                "The dedicated preset retains campus generation identity");

        Path root = Files.createTempDirectory("gtl-campus-persistence-regression-");
        UUID ordinaryOwner = UUID.fromString("00cafe00-0000-4000-8000-000000000001");
        UUID campusOwner = UUID.fromString("00cafe00-0000-4000-8000-000000000002");
        try {
            var empty = PersonalPlotStore.read(root);
            check(empty.template() == null && empty.worlds().isEmpty(), "An untouched save has no personal terrain state");
            PlotSettings ordinary = PlotSettings.preset(5).withGroundBlock("minecraft:blue_concrete");
            var normalTemplate = empty.withTemplate(ordinary);
            PersonalPlotStore.write(root, normalTemplate);
            var normalWorld = PersonalPlotStore.read(root).create(ordinaryOwner, ordinary);
            PersonalPlotStore.write(root, normalWorld);
            Path ordinaryPack = PersonalPlotStore.packPath(root, ordinaryOwner);
            byte[] ordinaryBytes = Files.readAllBytes(ordinaryPack);
            check(PersonalPlotStore.read(root).worlds().get(ordinaryOwner).equals(ordinary),
                    "An ordinary private dimension exists before the new preset is selected");

            try (ZipFile loadedOrdinary = new ZipFile(ordinaryPack.toFile(), StandardCharsets.UTF_8)) {
                PersonalPlotStore.write(root, normalWorld.withTemplate(campus));
                var campusTemplate = PersonalPlotStore.read(root);
                check(campusTemplate.template().equals(campus), "Campus survives the real server template storage path");
                check(campusTemplate.worlds().get(ordinaryOwner).equals(ordinary),
                        "Selecting a campus template keeps the existing ordinary world's settings");
                check(Arrays.equals(ordinaryBytes, Files.readAllBytes(ordinaryPack)),
                        "Selecting campus never rewrites the loaded ordinary UUID ZIP");

                var withCampus = campusTemplate.create(campusOwner, campusTemplate.template());
                PersonalPlotStore.write(root, withCampus);
                var restarted = PersonalPlotStore.read(root);
                Path campusPack = PersonalPlotStore.packPath(root, campusOwner);
                byte[] campusBytes = Files.readAllBytes(campusPack);
                check(restarted.equals(withCampus), "Restart restores the template and both immutable UUID terrain identities");
                check(restarted.worlds().get(campusOwner).isCampus(), "The new owner reloads the campus generator mode");
                check(!ordinaryPack.equals(campusPack) && Files.exists(ordinaryPack) && Files.exists(campusPack),
                        "Campus has its own UUID archive alongside the ordinary private world");
                check(loadedOrdinary.getEntry("pack.mcmeta") != null
                                && Arrays.equals(ordinaryBytes, Files.readAllBytes(ordinaryPack)),
                        "Creating campus preserves the ordinary archive bytes and open ZIP handle");

                try (ZipFile archive = new ZipFile(campusPack.toFile(), StandardCharsets.UTF_8)) {
                    String definitionPath = "data/gtl_enhancedcore/dimension/"
                            + PersonalPlotStore.dimensionPath(campusOwner) + ".json";
                    JsonObject definition = readJson(archive, definitionPath);
                    JsonObject generator = definition.getAsJsonObject("generator");
                    JsonObject values = generator.getAsJsonObject("settings");
                    check(definition.get("type").getAsString().equals("minecraft:overworld"),
                            "Campus uses the existing overworld dimension height contract");
                    check(generator.get("type").getAsString().equals("gtl_enhancedcore:plot")
                                    && generator.get("biome").getAsString().equals("minecraft:plains"),
                            "Campus stays on the registered plot codec and fixed biome");
                    check(values.get("road_style").getAsString().equals("CAMPUS_WOODLAND"),
                            "The actual UUID dimension JSON records campus mode");
                    check(values.has("geometry_extras") && !values.has("plot_edge_width"),
                            "Campus dimension settings retain grouped geometry codec fields");
                    check(values.getAsJsonObject("geometry_extras").get("show_machine_frames").getAsBoolean() == frames
                                    && !values.has("show_machine_frames"),
                            "The actual UUID dimension JSON records the selected frame state in grouped geometry");
                    PlotSettings decoded = PlotSettingsCodec.CODEC.parse(JsonOps.INSTANCE, values).result().orElseThrow();
                    check(decoded.equals(campus), "The startup codec reads the real ZIP dimension settings without losing campus fields");
                    var encoded = PlotSettingsCodec.CODEC.encodeStart(JsonOps.INSTANCE, decoded).result().orElseThrow();
                    check(PlotSettingsCodec.CODEC.parse(JsonOps.INSTANCE, encoded).result().orElseThrow().equals(campus),
                            "Campus codec encode/decode preserves the entire terrain recipe");
                    check(archive.getEntry("data/minecraft/dimension/overworld.json") == null,
                            "Campus creation never publishes an override for the original overworld");
                    JsonObject manifest = readJson(archive, "gtl_personal_plot_owner.json");
                    check(manifest.get("owner").getAsString().equals(campusOwner.toString()),
                            "The campus archive owner is the requested UUID");
                    check(PlotPresetShare.decodeStored(manifest.get("settings").getAsString()).equals(campus),
                            "The archive manifest and dimension definition agree on campus terrain");

                    var changedFrames = new HashMap<>(restarted.worlds());
                    changedFrames.put(campusOwner, campus.withMachineFrames(!frames));
                    try {
                        PersonalPlotStore.write(root, new PersonalPlotStore.Data(campus, changedFrames));
                        throw new AssertionError("An existing campus frame setting changed");
                    } catch (IOException expected) {
                        check(Arrays.equals(campusBytes, Files.readAllBytes(campusPack))
                                        && PersonalPlotStore.read(root).equals(restarted),
                                "The frame choice stays immutable after campus creation");
                    }

                    PlotSettings replacementTemplate = PlotSettings.preset(2).withGroundBlock("minecraft:red_concrete");
                    var updated = restarted.withTemplate(replacementTemplate);
                    PersonalPlotStore.write(root, updated);
                    check(PersonalPlotStore.read(root).equals(updated), "A later template change reloads independently of both created worlds");
                    check(Arrays.equals(ordinaryBytes, Files.readAllBytes(ordinaryPack))
                                    && Arrays.equals(campusBytes, Files.readAllBytes(campusPack)),
                            "Template updates preserve both ordinary and campus ZIP bytes while loaded");

                    try {
                        updated.create(ordinaryOwner, campus);
                        throw new AssertionError("Campus accepted an already-owned ordinary UUID");
                    } catch (IllegalStateException expected) {
                        check(PersonalPlotStore.read(root).equals(updated), "Campus cannot recreate an already-owned ordinary dimension");
                    }
                    var replacementWorlds = new HashMap<>(updated.worlds());
                    replacementWorlds.put(ordinaryOwner, campus);
                    try {
                        PersonalPlotStore.write(root, new PersonalPlotStore.Data(replacementTemplate, replacementWorlds));
                        throw new AssertionError("Campus replaced an existing ordinary world's terrain");
                    } catch (IOException expected) {
                        check(Arrays.equals(ordinaryBytes, Files.readAllBytes(ordinaryPack))
                                        && Arrays.equals(campusBytes, Files.readAllBytes(campusPack)),
                                "A direct ordinary-to-campus overwrite is rejected without changing either archive");
                        check(PersonalPlotStore.read(root).equals(updated), "Rejected terrain replacement preserves saved template and owners");
                    }
                }
            }
            int ceiling = 319 - PlannedCampus.maxHeight();
            check(PlannedCampus.fitsHeight(campus.withSurfaceY(ceiling)), "The highest legal campus surface retains complete frame clearance");
            reject(() -> campus.withSurfaceY(ceiling + 1), "A campus one block beyond world height is rejected at settings construction");
            JsonObject invalidShare = envelope.deepCopy();
            invalidShare.getAsJsonObject("settings").addProperty("surface_y", ceiling + 1);
            reject(() -> PlotPresetShare.decode(invalidShare.toString()), "Shared campus cannot bypass the frame height limit");
            JsonObject invalidCodec = PersonalPlotStore.dimensionJson(campus).getAsJsonObject("generator")
                    .getAsJsonObject("settings").deepCopy();
            invalidCodec.addProperty("surface_y", ceiling + 1);
            try {
                check(PlotSettingsCodec.CODEC.parse(JsonOps.INSTANCE, invalidCodec).result().isEmpty(),
                        "Startup codec rejects an overheight campus definition");
            } catch (IllegalArgumentException expected) {
                check(true, "Startup codec construction rejects an overheight campus definition");
            }
            try (var paths = Files.walk(root)) {
                check(paths.noneMatch(path -> path.getFileName().toString().endsWith(".tmp")),
                        "Completed campus writes and refused replacements leave no pending transaction files");
            }
        } finally {
            // This is solely our own createTempDirectory save; walk does not follow symbolic links.
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }

    private static void rejectMismatchedFrameDefinition(boolean flatDefinition) throws Exception {
        Path root = Files.createTempDirectory("gtl-campus-frame-mismatch-");
        UUID owner = UUID.fromString("00cafe00-0000-4000-8000-000000000003");
        PlotSettings campus = PlotSettings.plannedCampus().withPlotEdgeBlock("minecraft:white_concrete")
                .withPlotEdgeWidth(0).withMachineFrames(true);
        JsonObject definition = PersonalPlotStore.dimensionJson(campus);
        JsonObject values = definition.getAsJsonObject("generator").getAsJsonObject("settings");
        JsonObject geometry = values.getAsJsonObject("geometry_extras");
        if (flatDefinition) {
            values.remove("geometry_extras");
            values.add("border_width", geometry.get("border_width"));
        } else geometry.addProperty("show_machine_frames", false);
        JsonObject manifest = new JsonObject();
        manifest.addProperty("version", 1);
        manifest.addProperty("owner", owner.toString());
        manifest.addProperty("settings", PlotPresetShare.encode(campus));
        Path pack = PersonalPlotStore.packPath(root, owner);
        try {
            Files.createDirectories(pack.getParent());
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(pack), StandardCharsets.UTF_8)) {
                zip.putNextEntry(new ZipEntry("gtl_personal_plot_owner.json"));
                zip.write(manifest.toString().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
                zip.putNextEntry(new ZipEntry("data/gtl_enhancedcore/dimension/"
                        + PersonalPlotStore.dimensionPath(owner) + ".json"));
                zip.write(definition.toString().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            byte[] original = Files.readAllBytes(pack);
            try {
                PersonalPlotStore.read(root);
                throw new AssertionError("An enabled frame manifest accepted an inconsistent dimension definition");
            } catch (IOException expected) {
                check(Arrays.equals(original, Files.readAllBytes(pack)),
                        "A mismatched frame definition is rejected while preserving the archive");
            }
        } finally {
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }

    private static JsonObject readJson(ZipFile archive, String name) throws IOException {
        var entry = archive.getEntry(name);
        if (entry == null) throw new AssertionError("Missing campus ZIP entry: " + name);
        try (var input = archive.getInputStream(entry)) {
            return JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static void reject(Runnable action, String message) {
        try { action.run(); throw new AssertionError(message); }
        catch (IllegalArgumentException expected) { check(true, message); }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Campus preset persistence checks: " + run());
    }
}
