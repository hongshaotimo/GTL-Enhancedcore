package com.gtl.enhancedcore;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.gtl.enhancedcore.common.world.campus.CampusPlanAccess;
import com.gtl.enhancedcore.common.world.plot.PlannedCampus;
import com.gtl.enhancedcore.common.world.plot.PlotSettings;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/** Frozen identities for published presets: a new layout needs a new style, not a rewritten baseline. */
public final class CampusPlanIdentityRegression {
    public static final Path FIXTURE = Path.of("src/test/resources/personal-plot/campus-plan-identities.json");
    private static int checks;

    public static void main(String[] args) throws Exception {
        System.out.println("Campus plan identity assertions: " + run());
    }

    static int run() throws Exception {
        checks = 0;
        var fixture = JsonParser.parseString(Files.readString(FIXTURE)).getAsJsonObject();
        check(fixture.get("schemaVersion").getAsInt() == 1, "Frozen campus identity fixture schema is explicit");
        Set<String> expectedStyles = Set.of("CAMPUS", "CAMPUS_PARK", "CAMPUS_COMPACT", "CAMPUS_DENSE", "CAMPUS_WOODLAND");
        Set<String> actualStyles = new HashSet<>(), identities = new HashSet<>();
        Set<CampusPlanAccess.Plan> distinctPlans = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (var element : fixture.getAsJsonArray("presets")) {
            var row = element.getAsJsonObject();
            var style = PlotSettings.RoadStyle.valueOf(row.get("roadStyle").getAsString());
            var settings = PlotSettings.plannedCampus().withRoadStyle(style);
            var plan = CampusPlanAccess.of(settings);
            String expectedIdentity = row.get("layoutIdentity").getAsString();
            check(actualStyles.add(style.name()) && identities.add(expectedIdentity), "Published style and identity are unique in the frozen fixture");
            check(distinctPlans.add(plan), "Different published styles own separate plan objects");
            check(style.ordinal() == row.get("roadStyleOrdinal").getAsInt(), "Published enum ordinals cannot move when another preset is appended");
            check(CampusPlanAccess.fingerprint(style, plan).equals(expectedIdentity),
                    style + " frozen machine IDs, ordered destinations, coordinates, bounds and metadata cannot silently change");
            check(CampusPlanAccess.layoutIdentity(settings).equals(expectedIdentity),
                    style + " runtime identity selects the exact frozen plan fingerprint");
            check(plan.plots().size() == row.get("machinePlots").getAsInt()
                            && plan.targets().size() == row.get("targets").getAsInt()
                            && plan.workshops().size() == row.get("workshops").getAsInt(),
                    style + " frozen target inventory includes every machine, AE core and workshop");
            check(bounds(plan.bounds()).equals(row.getAsJsonArray("worldBounds"))
                            && bounds(plan.aeBounds()).equals(row.getAsJsonArray("aeBounds")),
                    style + " frozen inclusive world and AE bounds remain unchanged");
            for (int index = 0; index < plan.targets().size(); index++)
                check(plan.targets().get(index).index() == index, style + " numeric destination indexes remain aligned with frozen target order");
        }
        check(actualStyles.equals(expectedStyles), "All five already-published campus identities have a fixed expectation");
        return checks;
    }

    static JsonArray bounds(PlannedCampus.Bounds bounds) {
        JsonArray values = new JsonArray(); values.add(bounds.minX()); values.add(bounds.minZ());
        values.add(bounds.maxX()); values.add(bounds.maxZ()); return values;
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); checks++; }
}
