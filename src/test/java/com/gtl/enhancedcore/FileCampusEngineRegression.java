package com.gtl.enhancedcore;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.gtl.enhancedcore.common.world.plot.CampusPresetRegistry;
import com.gtl.enhancedcore.common.world.plot.FactoryArchitecture;
import com.gtl.enhancedcore.common.world.plot.FileCampus;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

/** One reusable engine can load independent authored definitions without adding Java preset cases. */
final class FileCampusEngineRegression {
    private static int checks;
    static int run() throws Exception {
        checks = 0;
        var originalDefinition = CampusPresetRegistry.get("quartz_works");
        var original = FileCampus.of(originalDefinition.id());
        var originalIdentity = original.resourceIdentity();
        var originalFloor = original.surfaceBlock(null, 10, 10);
        var originalColor = original.previewColorAt(10, 10);
        var originalPlots = original.plots();
        var originalHall = original.halls().getFirst();
        check(original == FileCampus.of(originalDefinition.id()), "Repeated preset lookup uses its own cached engine");
        JsonObject manifest;
        try (var reader = new InputStreamReader(CampusPresetRegistry.class.getResourceAsStream(CampusPresetRegistry.RESOURCE), StandardCharsets.UTF_8)) {
            manifest = JsonParser.parseReader(reader).getAsJsonObject();
        }
        var additional = manifest.getAsJsonArray("presets").get(0).getAsJsonObject().deepCopy();
        additional.addProperty("id", "isolated_file_engine_check");
        additional.addProperty("nameKey", "gtl_enhancedcore.preset.engine_check.name");
        additional.addProperty("descriptionKey", "gtl_enhancedcore.preset.engine_check.description");
        additional.getAsJsonObject("terrain").getAsJsonObject("materials").addProperty("quartz", "minecraft:black_concrete");
        additional.getAsJsonObject("terrain").getAsJsonObject("previewColors").addProperty("quartz", 0x161616);
        manifest.getAsJsonArray("presets").add(additional);
        var definitions = CampusPresetRegistry.decode(manifest);
        var alternateDefinition = definitions.stream().filter(d -> d.id().equals("isolated_file_engine_check")).findFirst().orElseThrow();
        var alternate = new FileCampus(alternateDefinition);
        check(!CampusPresetRegistry.contains(alternate.id()) && definitions.size() == CampusPresetRegistry.definitions().size() + 1,
                "A second in-memory file entry loads without Java registration or a player-visible demo preset");
        check(alternate.id().equals(alternateDefinition.id()) && alternate.definition().equals(alternateDefinition),
                "The engine retains the selected authored identity rather than falling back to the factory");
        check(alternate.bounds().equals(original.bounds()) && alternate.aeCoreBounds().equals(original.aeCoreBounds())
                        && alternate.catalogResource().equals(original.catalogResource()),
                "A new definition can explicitly reuse existing authored geometry and machine resources");
        check(alternate.plots().equals(originalPlots) && alternate.plots() != originalPlots
                        && alternate.halls().equals(original.halls()) && alternate.halls() != original.halls()
                        && alternate.workshops().equals(original.workshops()) && alternate.workshops() != original.workshops(),
                "Separate engine instances own separate immutable layout containers even when resource contents match");
        var first = originalPlots.getFirst();
        check(alternate.plotAt(first.x(), first.z()).equals(first) && alternate.plotAt(first.x(), first.z()) != first,
                "Machine lookup resolves the selected engine's own loaded plot objects");
        check(originalFloor.equals("minecraft:smooth_quartz")
                        && alternate.surfaceBlock(null, 10, 10).equals("minecraft:black_concrete")
                        && alternate.materials().contains("minecraft:black_concrete"),
                "Changing a file definition's terrain palette changes its actual floor sampler and generation palette");
        check(alternate.previewColorAt(10, 10) == 0xff161616 && originalColor != alternate.previewColorAt(10, 10),
                "Each file definition supplies an independent authored preview color palette");
        check(!alternate.resourceIdentity().equals(originalIdentity), "Different file identities and materials cannot alias one resource fingerprint");
        var copiedMaterials = new HashMap<>(originalDefinition.materials());
        copiedMaterials.put("quartz", "minecraft:blue_concrete");
        var recoloredDefinition = new CampusPresetRegistry.Definition(originalDefinition.id(), originalDefinition.nameKey(),
                originalDefinition.descriptionKey(), originalDefinition.catalogResource(), originalDefinition.layoutResource(),
                originalDefinition.architectureResource(), originalDefinition.engine(), copiedMaterials, originalDefinition.dimensions(),
                originalDefinition.previewColors());
        var recolored = new FileCampus(recoloredDefinition);
        check(!recolored.resourceIdentity().equals(originalIdentity)
                        && recolored.surfaceBlock(null, 10, 10).equals("minecraft:blue_concrete"),
                "The resource fingerprint also detects material changes when a definition ID is unchanged");
        copiedMaterials.put("quartz", "minecraft:red_concrete");
        check(recolored.surfaceBlock(null, 10, 10).equals("minecraft:blue_concrete"),
                "Mutating an authoring map after construction cannot recolor a loaded preset");
        var copiedColors = new HashMap<>(originalDefinition.previewColors()); copiedColors.put("quartz", 0x111234);
        var previewOnly = new FileCampus(new CampusPresetRegistry.Definition(originalDefinition.id(), originalDefinition.nameKey(),
                originalDefinition.descriptionKey(), originalDefinition.catalogResource(), originalDefinition.layoutResource(),
                originalDefinition.architectureResource(), originalDefinition.engine(), originalDefinition.materials(),
                originalDefinition.dimensions(), copiedColors));
        check(previewOnly.previewColorAt(10, 10) == 0xff111234
                        && !previewOnly.resourceIdentity().equals(originalIdentity)
                        && previewOnly.surfaceBlock(null, 10, 10).equals(originalFloor),
                "A preview-only palette edit changes its resource identity while preserving the authored block floor");
        check(original.plots() == originalPlots && original.halls().getFirst() == originalHall
                        && original.surfaceBlock(null, 10, 10).equals(originalFloor)
                        && original.previewColorAt(10, 10) == originalColor && original.resourceIdentity().equals(originalIdentity),
                "Loading or recoloring another definition leaves the original layout, colors and identity unchanged");
        var renderer = FactoryArchitecture.forResource(originalDefinition.architectureResource());
        check(renderer == FactoryArchitecture.forResource(alternateDefinition.architectureResource()),
                "An explicitly shared architecture resource reuses one immutable renderer");
        for (var hall : java.util.List.of(original.coveredHalls().getFirst(), original.outdoorSites().getFirst())) {
            int x = hall.x() + hall.width() / 2, z = hall.z() + hall.depth() / 2;
            check(original.surfaceBlock(null, x, z).equals(renderer.surface(hall, x, z))
                            && original.maxDecorationYAt(null, x, z) == renderer.top(hall, x, z),
                    "Indoor and open-court sampling use the architecture renderer selected by the definition");
            int top = renderer.top(hall, x, z);
            check(java.util.Objects.equals(original.blockAboveAt(null, x, top, z), renderer.above(hall, x, top, z)),
                    "The selected renderer supplies actual shaped states as well as decoration height");
        }
        immutable(() -> alternate.plots().clear(), "One preset cannot alter another's machine table through a public collection");
        return checks;
    }
    private static void immutable(Runnable action, String message) {
        try { action.run(); } catch (UnsupportedOperationException expected) { checks++; return; }
        throw new AssertionError(message);
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); checks++; }
}
