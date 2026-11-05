package com.gtl.enhancedcore;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.gtl.enhancedcore.common.world.campus.CampusPlanAccess;
import com.gtl.enhancedcore.common.world.personalplot.PersonalPlotStore;
import com.gtl.enhancedcore.common.world.plot.CampusPresetRegistry;
import com.gtl.enhancedcore.common.world.plot.FileCampus;
import com.gtl.enhancedcore.common.world.plot.PlotPresetShare;
import com.gtl.enhancedcore.common.world.plot.PlotSettings;
import com.gtl.enhancedcore.common.world.plot.PlotSettingsCodec;
import com.mojang.serialization.JsonOps;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.zip.ZipFile;

/** File references have a distinct v4 format while every published nineteen-field recipe stays v3. */
final class FileCampusPresetPersistenceRegression {
    private static final Path IDENTITY_FIXTURE = Path.of("src/test/resources/personal-plot/file-campus-identities.json");
    private static int checks;

    static int run() throws Exception {
        checks = 0;
        publishedIdentities();
        check(PlotSettings.RoadStyle.CAMPUS_FILE.ordinal() == 13 && PlotSettings.RoadStyle.CAMPUS_FACTORY.ordinal() == 12,
                "File-backed presets append an identity after all six frozen campuses");
        for (var legacy : List.of(PlotSettings.defaults(), PlotSettings.plannedCampus(), PlotSettings.factoryCampus())) {
            JsonObject json = JsonParser.parseString(PlotPresetShare.encode(legacy)).getAsJsonObject();
            check(json.get("version").getAsInt() == 3 && json.getAsJsonObject("settings").size() == 19
                            && !json.getAsJsonObject("settings").has("campus_preset_id") && legacy.campusPresetId().isEmpty(),
                    "Published and free-plot shares preserve their original exact v3 field set");
            var dimension = PersonalPlotStore.dimensionJson(legacy).getAsJsonObject("generator").getAsJsonObject("settings");
            check(!dimension.has("campus_preset_id") && !dimension.getAsJsonObject("geometry_extras").has("campus_preset_id"),
                    "Legacy dimension JSON does not gain an empty preset-reference field");
            check(PlotSettingsCodec.CODEC.parse(JsonOps.INSTANCE, dimension).result().orElseThrow().equals(legacy),
                    "A legacy dimension with no file ID still decodes to its original named generation");
        }
        var identities = new HashSet<String>();
        identities.add(CampusPlanAccess.layoutIdentity(PlotSettings.factoryCampus()));
        for (var definition : CampusPresetRegistry.definitions()) {
            var settings = PlotSettings.fileCampus(definition.id());
            String text = PlotPresetShare.encode(settings);
            JsonObject json = JsonParser.parseString(text).getAsJsonObject();
            check(settings.isCampus() && settings.roadStyle() == PlotSettings.RoadStyle.CAMPUS_FILE
                            && settings.campusPresetId().equals(definition.id()), "The requested file identity is explicit");
            check(identities.add(CampusPlanAccess.layoutIdentity(settings)),
                    "Each registered file preset and the frozen factory retain different teleport contracts");
            check(CampusPlanAccess.of(settings) == CampusPlanAccess.of(PlotPresetShare.decode(text)),
                    "Repeated transported opens of one file preset use its own cached plan");
            check(json.get("version").getAsInt() == 4 && json.getAsJsonObject("settings").size() == 20
                            && json.getAsJsonObject("settings").get("campus_preset_id").getAsString().equals(definition.id()),
                    "File preset export uses v4 with one explicit registry reference");
            check(PlotPresetShare.decode(text).equals(settings) && PlotPresetShare.decodeStored(text).equals(settings)
                            && PlotPresetShare.encode(PlotPresetShare.decode(text)).equals(text),
                    "File preset share, persistent read and canonical export preserve the same complete settings");
            var encoded = PlotSettingsCodec.CODEC.encodeStart(JsonOps.INSTANCE, settings).result().orElseThrow().getAsJsonObject();
            check(!encoded.has("campus_preset_id")
                            && encoded.getAsJsonObject("geometry_extras").get("campus_preset_id").getAsString().equals(definition.id())
                            && PlotSettingsCodec.CODEC.parse(JsonOps.INSTANCE, encoded).result().orElseThrow().equals(settings),
                    "Actual world codec keeps the file ID inside geometry_extras");
            check(settings.withMachineFrames(true).withLineStyle(PlotSettings.LineStyle.SOLID)
                            .withGroundBlock("minecraft:cyan_concrete").withPlotEdgeWidth(2).campusPresetId().equals(definition.id()),
                    "Ordinary settings edits preserve the file identity");
            check(settings.withRoadStyle(PlotSettings.RoadStyle.HIGHWAY).campusPresetId().isEmpty(),
                    "Leaving file mode removes its reference instead of attaching it to an unrelated layout");
            rejected(text, root -> root.addProperty("version", 3), "schema");
            rejected(text, root -> root.getAsJsonObject("settings").remove("campus_preset_id"), "schema");
            rejected(text, root -> root.getAsJsonObject("settings").addProperty("road_style", "CAMPUS_FACTORY"), "settings");
            for (String id : List.of("", "missing_preset", "celestial_megastructure", "../quartz_works", "QUARTZ_WORKS", "quartz_works "))
                rejected(text, root -> root.getAsJsonObject("settings").addProperty("campus_preset_id", id), "settings");
            rejected(text, root -> root.getAsJsonObject("settings").addProperty("campus_preset_id", false), "schema");
            rejected(text, root -> root.getAsJsonObject("settings").add("campus_preset_id", new JsonObject()), "schema");
            rejected(text, root -> root.getAsJsonObject("settings").add("campus_preset_id", com.google.gson.JsonNull.INSTANCE), "schema");
        }
        String legacy = PlotPresetShare.encode(PlotSettings.factoryCampus());
        rejected(legacy, root -> root.getAsJsonObject("settings").addProperty("campus_preset_id", ""), "schema");
        rejected(legacy, root -> root.getAsJsonObject("settings").addProperty("road_style", "CAMPUS_FILE"), "settings");
        rejected(legacy, root -> root.addProperty("version", 4), "schema");
        rejectConstruction(() -> PlotSettings.defaults().withRoadStyle(PlotSettings.RoadStyle.CAMPUS_FILE));
        rejectConstruction(() -> PlotSettings.fileCampus("missing_preset"));
        rejectConstruction(() -> PlotSettings.fileCampus("celestial_megastructure"));
        storedIsolation();
        return checks;
    }

    /** Published FILE entries freeze their own resources and transport contracts, not registry ordering. */
    private static void publishedIdentities() throws Exception {
        var fixture = JsonParser.parseString(Files.readString(IDENTITY_FIXTURE, StandardCharsets.UTF_8)).getAsJsonObject();
        check(fixture.get("schemaVersion").getAsInt() == 1, "Published file-campus identity fixture has an explicit schema");
        var ids = new HashSet<String>(); var resources = new HashSet<String>(); var layouts = new HashSet<String>();
        var plans = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<CampusPlanAccess.Plan, Boolean>());
        for (var element : fixture.getAsJsonArray("presets")) {
            var row = element.getAsJsonObject(); String id = row.get("id").getAsString();
            check(ids.add(id) && CampusPresetRegistry.contains(id), "Every frozen file preset has one still-registered explicit ID: " + id);
            var definition = CampusPresetRegistry.get(id); var campus = FileCampus.of(id);
            var settings = PlotSettings.fileCampus(id); var plan = CampusPlanAccess.of(settings);
            check(definition.engine().equals(row.get("engine").getAsString())
                            && definition.catalogResource().equals(row.get("catalogResource").getAsString())
                            && definition.layoutResource().equals(row.get("layoutResource").getAsString())
                            && definition.architectureResource().equals(row.get("architectureResource").getAsString()),
                    id + " retains the published engine and independent authored resource paths");
            String resourceIdentity = row.get("resourceIdentity").getAsString(), layoutIdentity = row.get("layoutIdentity").getAsString();
            check(resources.add(resourceIdentity) && layouts.add(layoutIdentity) && plans.add(plan),
                    "Published file presets cannot share resource identities, teleport identities or plan objects: " + id);
            check(campus.resourceIdentity().equals(resourceIdentity),
                    id + " published catalog, terrain palette, dimensions, preview palette and architecture remain frozen");
            check(CampusPlanAccess.layoutIdentity(settings).equals(layoutIdentity),
                    id + " published ordered controller coordinates and numeric teleport destinations remain frozen");
            check(plan.plots().size() == row.get("machinePlots").getAsInt()
                            && plan.plots().stream().map(plot -> plot.id()).distinct().count() == row.get("machineTypes").getAsInt()
                            && plan.targets().size() == row.get("targets").getAsInt()
                            && plan.workshops().size() == row.get("workshops").getAsInt()
                            && campus.sharedMachineCount() == row.get("sharedMachines").getAsInt()
                            && campus.halls().size() == row.get("sites").getAsInt()
                            && campus.maxHeight() == row.get("maxHeight").getAsInt(),
                    id + " retains its published machine inventory, shared capacity, sites and height envelope");
            check(CampusPlanIdentityRegression.bounds(plan.bounds()).equals(row.getAsJsonArray("worldBounds"))
                            && CampusPlanIdentityRegression.bounds(plan.aeBounds()).equals(row.getAsJsonArray("aeBounds")),
                    id + " retains its inclusive world and AE-core bounds");
            for (int index = 0; index < plan.targets().size(); index++)
                check(plan.targets().get(index).index() == index, id + " destination indexes stay aligned with the frozen target order");
            var expectedResources = row.getAsJsonObject("resources");
            check(expectedResources.keySet().containsAll(List.of(definition.catalogResource(), definition.layoutResource(), definition.architectureResource())),
                    id + " fixture covers all three resources used by the runtime engine");
            for (var resource : expectedResources.entrySet()) {
                byte[] data;
                try (var stream = FileCampusPresetPersistenceRegression.class.getResourceAsStream(resource.getKey())) {
                    if (stream == null) throw new AssertionError("Missing published file-campus resource: " + resource.getKey());
                    data = stream.readAllBytes();
                }
                check(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data)).equals(resource.getValue().getAsString()),
                        id + " published file bytes remain unchanged: " + resource.getKey());
            }
        }
        check(ids.contains("quartz_works") && !ids.contains("celestial_megastructure"),
                "The retained factory preserves its frozen expectation and the removed celestial ID is absent");
    }

    public static void main(String[] args) throws Exception {
        checks = 0; publishedIdentities();
        System.out.println("Published file-campus identity checks: " + checks);
    }

    private static void storedIsolation() throws Exception {
        var root = Files.createTempDirectory("gtl-file-campus-persistence-");
        UUID previousOwner = UUID.fromString("00fac700-0000-4000-8000-000000000010");
        Map<UUID, byte[]> snapshots = new LinkedHashMap<>();
        try {
            PersonalPlotStore.write(root, PersonalPlotStore.read(root).create(previousOwner, PlotSettings.factoryCampus()));
            snapshots.put(previousOwner, Files.readAllBytes(PersonalPlotStore.packPath(root, previousOwner)));
            int index = 0;
            for (var definition : CampusPresetRegistry.definitions()) {
                var file = PlotSettings.fileCampus(definition.id());
                PersonalPlotStore.write(root, PersonalPlotStore.read(root).withTemplate(file));
                UUID owner = new UUID(0x00fac70000004000L, 0x8000000000000020L + index++);
                var selected = PersonalPlotStore.read(root);
                check(selected.template().equals(file), "The actual server template file retains its file-preset ID across restart");
                PersonalPlotStore.write(root, selected.create(owner, selected.template()));
                var reopened = PersonalPlotStore.read(root);
                check(reopened.worlds().get(owner).equals(file) && reopened.worlds().get(previousOwner).equals(PlotSettings.factoryCampus()),
                        "New file worlds and existing frozen factory worlds remain distinct after restart");
                try (var zip = new ZipFile(PersonalPlotStore.packPath(root, owner).toFile(), StandardCharsets.UTF_8)) {
                    String path = "data/gtl_enhancedcore/dimension/" + PersonalPlotStore.dimensionPath(owner) + ".json";
                    try (var input = zip.getInputStream(zip.getEntry(path))) {
                        var dimension = JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
                        var values = dimension.getAsJsonObject("generator").getAsJsonObject("settings");
                        check(!values.has("campus_preset_id")
                                        && values.getAsJsonObject("geometry_extras").get("campus_preset_id").getAsString().equals(definition.id()),
                                "The real owner ZIP stores the reference at the actual startup codec path");
                        check(PlotSettingsCodec.CODEC.parse(JsonOps.INSTANCE, values).result().orElseThrow().equals(file),
                                "The startup codec can read the actual newly published file-preset dimension ZIP");
                    }
                }
                for (var old : snapshots.entrySet()) check(Arrays.equals(old.getValue(), Files.readAllBytes(PersonalPlotStore.packPath(root, old.getKey()))),
                        "Selecting and creating another file preset leaves every earlier owner ZIP byte-for-byte unchanged");
                snapshots.put(owner, Files.readAllBytes(PersonalPlotStore.packPath(root, owner)));
            }
            PersonalPlotStore.write(root, PersonalPlotStore.read(root).withTemplate(PlotSettings.plannedCampus()));
            var restarted = PersonalPlotStore.read(root);
            check(restarted.worlds().size() == snapshots.size(), "Returning to a legacy template preserves every existing dimension");
            for (var old : snapshots.entrySet()) check(Arrays.equals(old.getValue(), Files.readAllBytes(PersonalPlotStore.packPath(root, old.getKey()))),
                    "A later legacy template does not rewrite any published file-based dimension");
        } finally {
            try (var files = Files.walk(root)) { for (var file : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(file); }
        }
    }
    private static void rejected(String text, Consumer<JsonObject> mutation, String expected) {
        var json = JsonParser.parseString(text).getAsJsonObject(); mutation.accept(json);
        for (boolean stored : List.of(false, true)) {
            try {
                if (stored) PlotPresetShare.decodeStored(json.toString()); else PlotPresetShare.decode(json.toString());
                throw new AssertionError("A malformed, unknown or cross-version file reference was accepted");
            } catch (PlotPresetShare.ShareException invalid) {
                check(expected.equals(invalid.errorKey()), "The invalid file reference is rejected at the correct schema/settings boundary");
            }
        }
    }
    private static void rejectConstruction(Runnable action) {
        try { action.run(); throw new AssertionError("Missing or unknown file ID was accepted"); }
        catch (IllegalArgumentException expected) { checks++; }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); checks++; }
}
