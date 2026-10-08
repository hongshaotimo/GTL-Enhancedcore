package com.gtl.enhancedcore;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/** Palette changes affect solid structural blocks, never controller rendering or hatch abilities. */
final class StructureCasingRegression {
    private static int checks;

    static int run() throws Exception {
        var root = Path.of("src/main/resources/data/gtl_enhancedcore/structures/gtl");
        var expected = Map.ofEntries(
                Map.entry("crystalline_infinity", "gtlcore:extreme_strength_tritanium_casing"),
                Map.entry("mega_distillery", "gtceu:clean_machine_casing"),
                Map.entry("mega_extractor", "gtlcore:hyper_mechanical_casing"),
                Map.entry("suprachronal_assembly_line", "gtlcore:molecular_casing"),
                Map.entry("mega_fluid_heater", "gtlcore:iridium_casing"),
                Map.entry("advanced_sps_crafting", "gtceu:fusion_casing_mk2"),
                Map.entry("star_ultimate_material_forge_factory", "gtlcore:molecular_casing"),
                Map.entry("nano_core", "gtlcore:hyper_mechanical_casing"),
                Map.entry("gravitation_shockburst", "gtlcore:create_casing"),
                Map.entry("advanced_neutron_activator", "gtlcore:sps_casing"),
                Map.entry("antientropy_condensation_center", "gtlcore:antifreeze_heatproof_machine_casing"),
                Map.entry("advanced_integrated_ore_processor", "gtceu:robust_machine_casing"),
                Map.entry("atomic_energy_excitation_plant", "gtlcore:dimensionally_transcendent_casing"),
                Map.entry("mage_assembler", "gtlcore:iridium_casing"),
                Map.entry("superconducting_electromagnetism", "gtlcore:lafium_mechanical_casing"));
        Set<String> changed = new java.util.HashSet<>();
        int machines = 0;
        for (String file : new String[]{"manifest.json", "upgrades.json", "upgrades_20260927.json"}) {
            var manifest = JsonParser.parseString(Files.readString(root.resolve(file))).getAsJsonObject();
            for (var entry : manifest.entrySet()) {
                String name = entry.getKey();
                var spec = entry.getValue().getAsJsonObject();
                var bindings = spec.getAsJsonObject("bindings");
                var hatch = bindings.getAsJsonObject("#");
                check(!spec.has("appearance"), name + ": native appearance retained");
                check(bindings.getAsJsonObject("@").size() == 2, name + ": controller binding unchanged");
                check(hatch.get("fallback").equals(spec.get("hatchFallback")), name + ": hatch casing metadata");
                for (var value : bindings.entrySet())
                    check(!value.getValue().getAsJsonObject().has("override"), name + ": no previous overrides");
                if (expected.containsKey(name)) {
                    String target = expected.get(name);
                    check(hatch.get("fallback").getAsString().equals(target), name + ": panel matches native renderer");
                    var replacements = spec.getAsJsonObject("casingReplacements");
                    check(replacements != null && replacements.size() == 1, name + ": explicit source provenance");
                    String old = replacements.keySet().iterator().next();
                    check(!old.equals(target) && replacements.get(old).getAsString().equals(target),
                            name + ": exact solid replacement");
                    int solids = 0;
                    for (var bindingEntry : bindings.entrySet()) {
                        var binding = bindingEntry.getValue().getAsJsonObject();
                        if (!binding.has("block") || bindingEntry.getKey().equals("#")) continue;
                        check(!binding.get("block").getAsString().equals(old), name + ": old body casing removed");
                        if (binding.get("block").getAsString().equals(target)) {
                            check(!binding.get("mode").getAsString().equals("hatch"), name + ": no new body ports");
                            solids += spec.getAsJsonObject("counts").get(bindingEntry.getKey()).getAsInt();
                        }
                    }
                    check(solids > 100, name + ": whole body palette, not only a cosmetic patch");
                    changed.add(name);
                } else {
                    check(!spec.has("casingReplacements"), name + ": compatible palette unchanged");
                }
                machines++;
            }
        }
        check(machines == 28 && changed.equals(expected.keySet()), "exact native palette scope");
        return checks;
    }

    private static void check(boolean valid, String message) {
        if (!valid) throw new AssertionError(message);
        checks++;
    }
}
