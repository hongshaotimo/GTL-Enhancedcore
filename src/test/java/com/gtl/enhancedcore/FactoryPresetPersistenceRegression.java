package com.gtl.enhancedcore;

import com.google.gson.JsonParser;
import com.gtl.enhancedcore.common.world.campus.CampusPlanAccess;
import com.gtl.enhancedcore.common.world.personalplot.PersonalPlotStore;
import com.gtl.enhancedcore.common.world.plot.FactoryCampus;
import com.gtl.enhancedcore.common.world.plot.PlannedCampus;
import com.gtl.enhancedcore.common.world.plot.PlotLayout;
import com.gtl.enhancedcore.common.world.plot.PlotPresetShare;
import com.gtl.enhancedcore.common.world.plot.PlotPreviewRaster;
import com.gtl.enhancedcore.common.world.plot.PlotSettings;
import com.gtl.enhancedcore.common.world.plot.PlotSettingsCodec;
import com.mojang.serialization.JsonOps;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Comparator;
import java.util.UUID;
import java.util.zip.ZipFile;

/** Factory templates and destinations remain separate from every previously published campus. */
final class FactoryPresetPersistenceRegression {
    private static int checks;

    static int run() throws Exception {
        checks = 0;
        PlotSettings factory = PlotSettings.factoryCampus(), woodland = PlotSettings.plannedCampus();
        check(factory.isCampus() && factory.roadStyle() == PlotSettings.RoadStyle.CAMPUS_FACTORY,
                "Factory creation selects its own generation identity");
        check(factory.roadStyle().ordinal() == 12 && PlotSettings.RoadStyle.CAMPUS_WOODLAND.ordinal() == 11,
                "Factory appends an identity without moving the published woodland ordinal");
        check(PlotSettings.defaults().withRoadPreset(PlotSettings.RoadPreset.FACTORY).equals(factory),
                "The road preset card and dedicated creation entry select the same factory");
        check(PlotPresetShare.decode(PlotPresetShare.encode(factory)).equals(factory),
                "Factory survives the player preset sharing path");
        var definition = PersonalPlotStore.dimensionJson(factory).getAsJsonObject("generator").getAsJsonObject("settings");
        check(definition.get("road_style").getAsString().equals("CAMPUS_FACTORY")
                        && PlotSettingsCodec.CODEC.parse(JsonOps.INSTANCE, definition).result().orElseThrow().equals(factory),
                "Dedicated-server dimension JSON and startup codec retain factory identity");
        check(!FactoryCampus.CATALOG.equals(PlannedCampus.CATALOG), "Factory owns a separate machine catalog resource");
        var plan = CampusPlanAccess.of(factory);
        check(plan != CampusPlanAccess.of(woodland)
                        && !CampusPlanAccess.layoutIdentity(factory).equals(CampusPlanAccess.layoutIdentity(woodland)),
                "Factory preview, search and teleport use a separate cached plan and fingerprint");
        check(plan.plots().equals(FactoryCampus.plots()) && plan.workshops().equals(FactoryCampus.workshops())
                        && plan.bounds().equals(FactoryCampus.bounds()) && plan.aeBounds().equals(FactoryCampus.aeCoreBounds()),
                "Every consumer receives the authored factory target table");
        check(plan.bounds().width() == 2000 && plan.bounds().depth() == 2000
                        && plan.aeBounds().width() == 160 && plan.aeBounds().depth() == 160,
                "Factory keeps its full island and central AE dimensions");
        check(factory.campusPlotCount() == plan.plots().size(), "Creation summary counts factory bays");
        for (var target : plan.targets()) {
            check(target == plan.targets().get(target.index()), "Factory destination indexes address the immutable factory session table");
            if (target.plot() == null) continue;
            var p = target.plot();
            check(plan.planningTier(p) == FactoryCampus.planningTier(p.id()), "Factory search metadata reads factory progression");
            check(PlotLayout.surfaceBlock(factory, p.x(), p.z()).equals(FactoryCampus.surfaceBlock(factory, p.x(), p.z())),
                    "Factory generation dispatches its own floor sampler");
            String above = FactoryCampus.blockAboveAt(factory, p.x(), 1, p.z());
            check(PlotLayout.blockIdAt(factory, p.x(), factory.surfaceY() + 1, p.z()).equals(above == null ? "minecraft:air" : above),
                    "Factory generation dispatches its own building sampler");
        }
        var viewport = new PlotPreviewRaster.CampusViewport(-1000, -1000, 2000, 2000);
        int[] pixels = PlotPreviewRaster.renderCampus(factory, 2, 2, viewport, 1);
        for (int z = 0; z < 2; z++) for (int x = 0; x < 2; x++)
            check(pixels[z * 2 + x] == (0xff000000 | FactoryCampus.previewColorAt(-500 + x * 1000, -500 + z * 1000)),
                    "Creation raster samples factory colors at its actual world coordinates");
        templateIsolation(factory, woodland);
        return checks;
    }

    private static void templateIsolation(PlotSettings factory, PlotSettings woodland) throws Exception {
        var root = Files.createTempDirectory("gtl-factory-persistence-");
        UUID oldOwner = UUID.fromString("00fac700-0000-4000-8000-000000000001");
        UUID factoryOwner = UUID.fromString("00fac700-0000-4000-8000-000000000002");
        try {
            var before = PersonalPlotStore.read(root).create(oldOwner, woodland);
            PersonalPlotStore.write(root, before);
            byte[] oldArchive = Files.readAllBytes(PersonalPlotStore.packPath(root, oldOwner));
            PersonalPlotStore.write(root, before.withTemplate(factory));
            var selected = PersonalPlotStore.read(root);
            check(selected.template().equals(factory) && selected.worlds().get(oldOwner).equals(woodland),
                    "OP factory template selection preserves the existing woodland dimension");
            PersonalPlotStore.write(root, selected.create(factoryOwner, selected.template()));
            var reopened = PersonalPlotStore.read(root);
            check(reopened.template().equals(factory) && reopened.worlds().get(factoryOwner).equals(factory),
                    "Factory template and private dimension survive a server restart");
            check(Arrays.equals(oldArchive, Files.readAllBytes(PersonalPlotStore.packPath(root, oldOwner))),
                    "Creating a factory never rewrites the previous owner's woodland ZIP");
            try (var zip = new ZipFile(PersonalPlotStore.packPath(root, factoryOwner).toFile(), StandardCharsets.UTF_8)) {
                String path = "data/gtl_enhancedcore/dimension/" + PersonalPlotStore.dimensionPath(factoryOwner) + ".json";
                try (var input = zip.getInputStream(zip.getEntry(path))) {
                    var dimension = JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
                    check(dimension.getAsJsonObject("generator").getAsJsonObject("settings").get("road_style").getAsString().equals("CAMPUS_FACTORY"),
                            "The actual immutable owner ZIP records the factory identity");
                }
            }
            PersonalPlotStore.write(root, reopened.withTemplate(woodland));
            var switched = PersonalPlotStore.read(root);
            check(switched.worlds().get(factoryOwner).equals(factory) && switched.worlds().get(oldOwner).equals(woodland),
                    "Later template changes keep each created dimension on its original preset");
        } finally {
            try (var files = Files.walk(root)) {
                for (var file : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(file);
            }
        }
    }

    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); checks++; }
}
