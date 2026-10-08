package com.gtl.enhancedcore;

import com.google.gson.JsonParser;
import com.gtl.enhancedcore.common.item.PatternGeneratorFilter;
import com.gtl.enhancedcore.common.item.PatternGeneratorFilter.Material;
import com.gtl.enhancedcore.common.item.PatternGeneratorLayout;
import com.gtl.enhancedcore.common.item.PatternGeneratorPresetFiles;
import com.gtl.enhancedcore.common.recipe.MvCircuitAssemblerRecipe;
import java.util.BitSet;
import java.util.List;
import java.util.Set;

public final class PatternGeneratorRegression {
    private static int checks;
    private static void check(boolean value, String reason) {
        checks++;
        if (!value) throw new AssertionError(reason);
    }

    public static int run() {
        checks = 0;
        for (int actual = 0; actual <= 32; actual++) {
            check(PatternGeneratorFilter.matchesCircuit(-1, actual), "Any circuit includes all configurations");
            for (int selection = 0; selection <= 32; selection++)
                check(PatternGeneratorFilter.matchesCircuit(selection, actual) == (selection == actual),
                        "Specific circuit and no-circuit remain distinct");
        }
        var words = PatternGeneratorFilter.keywords(" \"Iron\"  铜  iron \u201cWATER\u201d ");
        check(words.equals(List.of("iron", "铜", "water")), "Names, quotes, whitespace and case normalize once");
        var item = new Material("example:water", false, 3);
        var fluid = new Material("example:water", true, 3);
        var namedItems = new BitSet();
        var namedFluids = new BitSet();
        namedItems.set(3);
        check(PatternGeneratorFilter.allows(List.of(item), List.of("铜"), namedItems, namedFluids, Set.of()),
                "Localized item-name match survives server filtering");
        check(!PatternGeneratorFilter.allows(List.of(fluid), List.of("铜"), namedItems, namedFluids, Set.of()),
                "Item name bit cannot match a fluid with the same numeric index");
        namedFluids.set(3);
        check(PatternGeneratorFilter.allows(List.of(fluid), List.of("铜"), namedItems, namedFluids, Set.of()),
                "Localized fluid-name match is independent");
        check(PatternGeneratorFilter.allows(List.of(fluid), PatternGeneratorFilter.keywords("missing WATER"), new BitSet(), new BitSet(), Set.of()),
                "Whitelist keywords use OR and match registry IDs");
        check(!PatternGeneratorFilter.allows(List.of(item), List.of("water"), namedItems, namedFluids,
                Set.of("item:example:water")), "Blacklist wins over a matching whitelist");
        check(PatternGeneratorFilter.allows(List.of(fluid), List.of(), namedItems, namedFluids,
                Set.of("item:example:water")), "Item blacklist leaves the same-ID fluid usable");
        check(!PatternGeneratorFilter.allows(List.of(fluid), List.of(), namedItems, namedFluids,
                Set.of("fluid:example:water")), "Fluid blacklist rejects its own identity");
        check(PatternGeneratorFilter.allows(List.of(), List.of(), namedItems, namedFluids, Set.of()), "Empty filters allow no-input recipes");
        check(!PatternGeneratorFilter.allows(List.of(), List.of("water"), namedItems, namedFluids, Set.of()), "Whitelist cannot match absent material");

        var source = JsonParser.parseString("""
                {"type":"gtceu:shaped","pattern":["RIE","CHC","WIW"],
                 "key":{"I":{"tag":"gtceu:circuits/hv"},"R":{"item":"gtceu:mv_robot_arm"},
                        "E":{"item":"gtceu:mv_emitter"},"C":{"item":"gtceu:mv_conveyor_module"},
                        "H":{"item":"gtceu:mv_machine_hull"},"W":{"tag":"gtceu:cables/copper"}},
                 "result":{"item":"gtceu:mv_circuit_assembler","count":1},"show_notification":false}
                """);
        var before = source.deepCopy();
        var updated = MvCircuitAssemblerRecipe.useMvCircuits(source).getAsJsonObject();
        check(source.equals(before), "Original acquisition recipe is not mutated");
        check(updated.getAsJsonObject("key").getAsJsonObject("I").get("tag").getAsString().equals("gtceu:circuits/mv"),
                "Only target machine circuit grade changes");
        updated.getAsJsonObject("key").getAsJsonObject("I").addProperty("tag", "gtceu:circuits/hv");
        check(updated.equals(source), "Shape, quantity, notification and every other material remain identical");
        var unrelated = source.deepCopy().getAsJsonObject();
        unrelated.getAsJsonObject("result").addProperty("item", "gtl_enhancedcore:pattern_generator");
        check(MvCircuitAssemblerRecipe.useMvCircuits(unrelated) == unrelated, "Pattern generator acquisition is unaffected");
        unrelated.getAsJsonObject("result").addProperty("item", "gtceu:hv_circuit_assembler");
        check(MvCircuitAssemblerRecipe.useMvCircuits(unrelated) == unrelated, "Other assembler tiers are unaffected");
        layoutAndPresets();
        return checks;
    }

    private static void layoutAndPresets() {
        check(PatternGeneratorLayout.DEFAULT_COLUMNS == 3 && PatternGeneratorLayout.capacity(3) == 45, "Default preview is 3 columns of 15");
        check(PatternGeneratorLayout.columns(-1) == 1 && PatternGeneratorLayout.columns(11) == 10, "Column limits are enforced");
        for (int columns = 1; columns <= 10; columns++) {
            int capacity = columns * 15;
            for (int total : new int[]{0, 1, 14, 15, 16, 44, 45, 46, 149, 150, 151, 10003}) {
                int pages = PatternGeneratorLayout.pages(total, columns);
                check(pages == Math.max(1, (total + capacity - 1) / capacity), "Page count covers each tail exactly");
                check(PatternGeneratorLayout.page(Integer.MAX_VALUE, total, columns) == pages - 1, "Page clamps after shrinking filters");
                var seen = new java.util.HashSet<Integer>();
                for (int page = 0; page < pages; page++) {
                    var positions = new java.util.HashSet<Integer>();
                    for (int i = 0; i < Math.min(capacity, total - page * capacity); i++) {
                        int column = PatternGeneratorLayout.column(i), row = PatternGeneratorLayout.row(i);
                        check(column < columns && row < 15, "Grid fits configured columns and fifteen rows");
                        check(positions.add(column * 15 + row), "Cells never overlap");
                        seen.add(page * capacity + i);
                    }
                }
                check(seen.size() == total, "Pagination neither omits nor duplicates recipes");
            }
        }
        java.nio.file.Path directory = null;
        try {
            directory = java.nio.file.Files.createTempDirectory("pattern-preset-regression-");
            var preset = PatternGeneratorPresetFiles.create("../铁板\\生产线", "玩家甲", "author-a", "{schema:4,recipeType:\"gtceu:bender\"}", List.of("gtceu:test/a", "gtceu:test/b"));
            check(PatternGeneratorPresetFiles.decode(PatternGeneratorPresetFiles.encode(preset)).equals(preset), "Unicode preset roundtrip preserves exact mapping");
            var first = PatternGeneratorPresetFiles.save(directory, preset);
            var second = PatternGeneratorPresetFiles.save(directory, preset);
            check(first.getParent().equals(directory) && second.getParent().equals(directory), "Display names cannot traverse directories");
            check(!first.equals(second), "Saving identical names never overwrites an existing file");
            java.nio.file.Files.writeString(directory.resolve("broken.json"), "{not json");
            java.nio.file.Files.writeString(directory.resolve("future.json"), PatternGeneratorPresetFiles.encode(preset).replace("\"version\": 1", "\"version\": 2"));
            java.nio.file.Files.writeString(directory.resolve("large.json"), " ".repeat(PatternGeneratorPresetFiles.MAX_BYTES + 1));
            var listing = PatternGeneratorPresetFiles.list(directory);
            check(listing.presets().size() == 2 && listing.unreadable() == 3, "Invalid, future and oversized files do not hide valid presets");
            try { PatternGeneratorPresetFiles.create("bad", "", "", "{}", List.of("invalid id")); throw new AssertionError("Invalid ID accepted"); }
            catch (IllegalArgumentException expected) { check(true, "Invalid recipe IDs rejected"); }
        } catch (java.io.IOException error) { throw new AssertionError(error); }
        finally {
            if (directory != null) try {
                try (var files = java.nio.file.Files.list(directory)) { for (var file : files.toList()) java.nio.file.Files.delete(file); }
                java.nio.file.Files.delete(directory);
            } catch (java.io.IOException error) { throw new AssertionError(error); }
        }
    }
}
