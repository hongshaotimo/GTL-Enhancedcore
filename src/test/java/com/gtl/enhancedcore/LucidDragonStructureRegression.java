package com.gtl.enhancedcore;

import com.gtl.enhancedcore.common.structure.StructureData;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Checks the retained input and the project-owned dragon geometry. */
final class LucidDragonStructureRegression {
    private static final Path STRUCTURES = Path.of("src/main/resources/data/gtl_enhancedcore/structures");
    private static int checks;

    static int run() throws Exception {
        check(!Files.exists(STRUCTURES.resolve("gtl/lucid_etchdreamer_preview_panel.pattern.gz")),
                "Lucid uses ADD's preview instead of a project preview panel");
        check(Files.isRegularFile(Path.of("SchemTool/input/lucid_etchdreamer.schem")),
                "Lucid original schematic input is preserved");

        List<String[]> dragon = read(STRUCTURES.resolve("dragonfieldproliferationcorestructure.pattern.gz"));
        check(dragon.size() == 120 && dragon.getFirst().length == 108 && dragon.getFirst()[0].length() == 252,
                "dragon current schematic dimensions");
        int dragonHatches = 0;
        for (int z = 0; z < dragon.size(); z++) {
            for (int y = 0; y < dragon.get(z).length; y++) {
                String row = dragon.get(z)[y];
                for (int x = 0; x < row.length(); x++) if (row.charAt(x) == 'X') dragonHatches++;
            }
        }
        check(dragonHatches == 24, "dragon reviewed schematic marks 24 hatch slots");
        check(dragon.get(112)[5].charAt(126) == 'C', "dragon MIN_Z controller anchor");
        return checks;
    }

    private static List<String[]> read(Path path) throws Exception {
        List<String[]> aisles = new ArrayList<>();
        StructureData.read(Files.newInputStream(path)).forEachAisle(aisles::add);
        return aisles;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
