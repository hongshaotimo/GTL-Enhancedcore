package com.gtl.enhancedcore;

import com.google.gson.JsonParser;
import com.gtl.enhancedcore.common.structure.StructureData;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/** Static native-cell contract checks; real Forge formation remains a separate acceptance test. */
public final class NativeHiddenStructureRegression {
    private static int assertions;

    public static int run() throws Exception {
        assertions = 0;
        Path fixtures = Path.of("src/test/resources/native-hidden-structures");
        var manifest = JsonParser.parseString(Files.readString(Path.of(
                "src/main/resources/data/gtl_enhancedcore/structures/hollow/native.json"))).getAsJsonObject();
        var machines = manifest.getAsJsonObject("machines");
        check(machines.size() == 3, "Three active native-isolation fixtures remain after retiring four satellites");
        int removed = 0;
        for (var entry : machines.entrySet()) {
            var spec = entry.getValue().getAsJsonObject();
            var rows = new ArrayList<String[]>();
            try (var input = Files.newInputStream(fixtures.resolve(spec.get("fixture").getAsString()))) {
                StructureData.read(input).forEachAisle(rows::add);
            }
            var size = spec.getAsJsonArray("size");
            check(rows.size() == size.get(2).getAsInt(), "Fixture depth: " + entry.getKey());
            check(rows.get(0).length == size.get(1).getAsInt(), "Fixture height: " + entry.getKey());
            check(rows.get(0)[0].length() == size.get(0).getAsInt(), "Fixture width: " + entry.getKey());
            var cells = spec.getAsJsonArray("cells");
            check(cells.size() == spec.get("removed").getAsInt(), "Deletion count matches exact cells");
            Set<String> positions = new HashSet<>();
            for (var element : cells) {
                var cell = element.getAsJsonObject();
                var pos = cell.getAsJsonArray("position");
                int x = pos.get(0).getAsInt(), y = pos.get(1).getAsInt(), z = pos.get(2).getAsInt();
                check(positions.add(pos.toString()), "No duplicate native cell");
                char symbol = rows.get(z)[y].charAt(x);
                check(symbol == cell.get("symbol").getAsString().charAt(0), "Cell agrees with original fixture");
                check(symbol == 'F' || symbol == 'M', "Only reviewed plain-block module cells are exempt");
                check(cell.getAsJsonArray("expectedBlocks").size() == 1,
                        "Every native target has an exact fixed candidate block");
                String expected = symbol == 'F' ? "gtlcore:dimensionally_transcendent_casing"
                        : "kubejs:dimensional_bridge_casing";
                check(cell.getAsJsonArray("expectedBlocks").get(0).getAsString().equals(expected),
                        "Candidate ID matches the upstream plain blocks predicate");
            }
            boolean module = entry.getKey().startsWith("gtladditions:nexus_satellite_factory_mk");
            check(cells.size() == (module ? 3 : 0), "Only four modules have certified removal coordinates");
            removed += cells.size();
        }
        check(removed == 0, "Retired module cell exemptions are no longer loaded");
        String helper = Files.readString(Path.of(
                "src/main/java/com/gtl/enhancedcore/common/structure/NativeHiddenStructureCells.java"));
        for (String contract : Set.of("predicate.isController", "predicate.isAny()", "target predicate unexpectedly any",
                "actual.equals(expected)",
                "original.aisleRepetitions", "Predicates.any()", "matches.length != depth")) {
            check(helper.contains(contract), "Native guard: " + contract);
        }
        String integration = Files.readString(Path.of(
                "src/main/java/com/gtl/enhancedcore/common/structure/NativeIsolationPatterns.java"));
        check(integration.contains("NativeHiddenStructureCells.apply(id, original, matches)"),
                "Native geometry exemptions are applied to the isolation copy before it is built");
        return assertions;
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
