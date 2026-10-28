package com.gtl.enhancedcore;

import com.google.gson.JsonParser;
import com.gtl.enhancedcore.common.machine.HydrothermalLeachingComplexMachine;
import com.gtl.enhancedcore.common.machine.TieredParallelMachine;
import com.gtl.enhancedcore.common.recipe.EquipmentRecipeCatalog;
import com.gtl.enhancedcore.common.structure.StructureData;
import com.gtl.enhancedcore.common.util.TooltipPolicy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import javax.imageio.ImageIO;

/** User-reviewed geometry, functional upstream bindings and the new machine's shared scheduling integration. */
final class HydrothermalStructuresRegression {
    private static int checks;
    private static final String NAME = "hydrothermal_leaching_complex";
    private static final Path ROOT = Path.of("src/main/resources/data/gtl_enhancedcore/structures");

    static int run() throws Exception {
        var manifest = JsonParser.parseString(Files.readString(ROOT.resolve("gtl/upgrades_20261013.json")))
                .getAsJsonObject();
        var wood = manifest.getAsJsonObject("wood_distillation").getAsJsonObject("bindings");
        var muffler = wood.getAsJsonObject("#").getAsJsonArray("requiredParts").get(0).getAsJsonObject();
        check(muffler.get("count").getAsInt() == 2 && muffler.get("sample").toString().equals("[10,7,9]"),
                "the two fixed upstream mufflers become two required panel hatches");
        var skeleton = manifest.getAsJsonObject("skeleton_shift_rift_engine").getAsJsonObject("bindings");
        check(skeleton.getAsJsonObject("J").get("mode").getAsString().equals("original")
                        && skeleton.getAsJsonObject("J").get("sample").toString().equals("[7,9,15]"),
                "skeleton heating coils retain temperature context and same-coil validation");
        check(skeleton.getAsJsonObject("I").get("mode").getAsString().equals("original")
                        && skeleton.getAsJsonObject("I").get("sample").toString().equals("[11,2,7]"),
                "skeleton stellar casings retain SCTier instead of becoming a literal block");
        for (var entry : manifest.entrySet()) {
            var bindings = entry.getValue().getAsJsonObject().getAsJsonObject("bindings");
            for (var binding : bindings.entrySet()) {
                check(!binding.getValue().getAsJsonObject().get("mode").getAsString().equals("hatch")
                                || binding.getKey().equals("#"), "only marked positions accept hatches");
            }
        }
        var spec = JsonParser.parseString(Files.readString(ROOT.resolve(NAME + ".json"))).getAsJsonObject();
        check(spec.getAsJsonObject("bindings").getAsJsonObject("X").get("mode").getAsString().equals("hatch"),
                "schematic diamond-ore marks become real hatches rather than literal diamond ore");
        var aisles = new ArrayList<String[]>();
        StructureData.read(Files.newInputStream(ROOT.resolve(NAME + ".pattern.gz"))).forEachAisle(aisles::add);
        check(aisles.size() == 145 && aisles.stream().allMatch(rows -> rows.length == 73
                && java.util.Arrays.stream(rows).allMatch(row -> row.length() == 109)), "hydrothermal 109x73x145 geometry");
        var hash = MessageDigest.getInstance("SHA-256");
        hash.update("109 73 145\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        int controllers = 0, hatches = 0;
        int[] counts = new int[128];
        for (int z = 0; z < aisles.size(); z++) {
            for (int y = 0; y < 73; y++) {
                String row = aisles.get(z)[y];
                hash.update((row + "\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                for (int x = 0; x < 109; x++) {
                    char c = row.charAt(x);
                    counts[c]++;
                    if (c == 'C') {
                        controllers++;
                        check(x == 96 && y == 12 && z == 135, "controller follows MIN_Z without a Y flip");
                    } else if (c == 'X') {
                        hatches++;
                        check(z == 135 && x >= 94 && x <= 98 && y >= 10 && y <= 14
                                        && !(x == 96 && y == 12), "24 hatch marks retain the source's front panel");
                    }
                }
            }
        }
        check(controllers == 1 && hatches == 24, "one controller and 24 hatch slots");
        check(HexFormat.of().formatHex(hash.digest()).equals(spec.get("sha256").getAsString()), "runtime geometry digest");
        check(sha256(Files.readAllBytes(Path.of("SchemTool/input", NAME + ".schem")))
                .equals(spec.get("sourceSha256").getAsString()), "original schematic digest");
        for (var entry : spec.getAsJsonObject("counts").entrySet())
            check(counts[entry.getKey().charAt(0)] == entry.getValue().getAsInt(), "material count " + entry.getKey());
        for (int i = 0; i < 128; i++) if (counts[i] > 0)
            check(spec.getAsJsonObject("bindings").has(String.valueOf((char)i)), "every geometry symbol is bound");

        check(HydrothermalLeachingComplexMachine.CROSS_RECIPE_THREADS == 128,
                "hydrothermal has the requested fixed 128 cross-recipe threads");
        check(HydrothermalLeachingComplexMachine.class.getSuperclass() == TieredParallelMachine.class,
                "the machine reuses isolated-order payment, persistence, and mode handling");
        check(java.util.Arrays.stream(HydrothermalLeachingComplexMachine.class.getDeclaredMethods())
                .noneMatch(method -> method.getName().equals("getMaxParallel")),
                "parallel limits come from the installed parallel hatch, with no hidden fixed override");
        String buffers = Files.readString(Path.of("src/main/java/com/gtl/enhancedcore/common/recipe/iv/IvBuffers.java"));
        check(buffers.contains("\"gtl_enhancedcore:" + NAME + "\""), "new controller participates in isolated buffer routing");
        String registration = Files.readString(Path.of("src/main/java/com/gtl/enhancedcore/common/registration/HydrothermalLeachingRegistration.java"));
        check(registration.contains("DIGESTION_TREATMENT") && registration.contains("DISSOLUTION_TREATMENT"),
                "both requested upstream recipe types are registered");
        String structure = Files.readString(Path.of("src/main/java/com/gtl/enhancedcore/common/structure/HydrothermalLeachingComplexStructure.java"));
        check(structure.contains("requiredBlock(\"gtlcore:iridium_casing\")")
                        && structure.contains("IvBufferRegistry.materialPorts(-1)"),
                "iridium hatch fallback and ordinary/super-buffer material exclusivity");
        for (String ability : new String[]{"MAINTENANCE", "INPUT_ENERGY", "INPUT_LASER", "PARALLEL_HATCH"})
            check(structure.contains("PartAbility." + ability), "requested ability " + ability);
        var tips = TooltipPolicy.page(NAME).summary();
        check(tips.contains(TooltipPolicy.CROSS_RECIPE_KEY)
                        && tips.contains("tooltip.gtl_enhancedcore." + NAME + ".threads")
                        && tips.contains("tooltip.gtl_enhancedcore.iv_break"),
                "cross-type capability, thread count and cancellation loss warning remain visible");
        try (var reader = Files.newBufferedReader(Path.of("src/main/resources/data/gtl_enhancedcore/equipment_recipes.json"))) {
            var recipe = EquipmentRecipeCatalog.read(reader).stream().filter(r -> r.output().equals(NAME)).findFirst().orElseThrow();
            check(recipe.tier() == EquipmentRecipeCatalog.TIERS.indexOf("uev")
                    && recipe.method().equals("assembly_line"), "acquisition is a UEV assembly-line recipe");
            for (String item : new String[]{"gtceu:digestion_tank", "gtceu:dissolving_tank", "gtlcore:iridium_casing"})
                check(recipe.ingredients().stream().anyMatch(i -> i.id().equals(item)), "acquisition includes " + item);
        }
        Path textures = Path.of("src/main/resources/assets/gtl_enhancedcore/textures/block/multiblock", NAME);
        for (String mode : new String[]{"", "_active", "_paused"}) {
            var image = ImageIO.read(textures.resolve("overlay_front" + mode + ".png").toFile());
            check(image.getWidth() == 16 && image.getHeight() == (mode.equals("_active") ? 64 : 16), "native 16px overlay " + mode);
        }
        return checks;
    }

    private static String sha256(byte[] data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    }

    private static void check(boolean valid, String message) {
        if (!valid) throw new AssertionError(message);
        checks++;
    }
}
