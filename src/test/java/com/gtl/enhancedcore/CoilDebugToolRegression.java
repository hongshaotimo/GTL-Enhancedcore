package com.gtl.enhancedcore;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;

/** Source contracts supplement the opt-in real-registry/native-coil startup checks. */
final class CoilDebugToolRegression {
    private static int assertions;
    private static final Path JAVA = Path.of("src/main/java/com/gtl/enhancedcore");

    static int run() throws Exception {
        String coils = source("common/structure/DistorterCoils.java");
        check(coils.contains("GTCEuAPI.HEATING_COILS.entrySet()"), "Includes registered addon coils");
        check(coils.contains("CoilBlock.CoilType.TRITANIUM.getCoilTemperature()"), "Minimum follows native Tritanium temperature");
        check(coils.contains("getCoilTemperature() >= minimum"), "Coils at and above the threshold are accepted");
        check(coils.contains("return nativeCoils.test(state)"), "Native uniformity and actual CoilType propagation retained");
        check(coils.contains("Arrays.stream(blocks).map(BlockInfo::fromBlock)"), "Preview lists the accepted blocks");
        String structures = source("common/data/machines/multiblock/GTLStructures.java");
        check(structures.contains(".where('N', DistorterCoils.create())"), "Real distorter coil slots use the new predicate");
        check(!structures.contains("tritaniumHeatingCoils()"), "Hard-coded Tritanium-only predicate removed");
        String machine = source("common/machine/HyperstructuralChemicalDistorterMachine.java");
        check(machine.contains("recipe.data.getInt(\"ebf_temp\") <= getCoilType().getCoilTemperature()"),
                "Real recipe temperature check remains mandatory");
        check(machine.contains("excess / 100 * 4"), "Existing parallel formula unchanged");
        String mixin = source("mixin/gtlcore/GTLItemsDebugPatternToolMixin.java");
        check(mixin.contains("method = \"<clinit>\"") && mixin.contains("stringValue=debug_pattern_test")
                && mixin.contains("DEBUG_PATTERN_TEST:Lcom/tterrag/registrate/util/entry/ItemEntry;"), "Registration interception narrowly sliced");
        check(mixin.contains("require = 1, allow = 1"), "Exactly one registration call must be intercepted");
        check(mixin.contains("GTRegistrate;item("), "Skips builder creation before callbacks are registered");
        String retired = source("common/registration/RetiredDebugPatternTool.java");
        check(retired.contains("public ItemBuilder<ComponentItem, GTRegistrate> onRegister("), "No orphan or old behavior callbacks");
        check(retired.contains("RegistryObject.create(REPLACEMENT, ForgeRegistries.ITEMS)"), "Legacy field resolves lazily to replacement");
        check(!retired.contains("super.register()") && !retired.contains("PatternTestBehavior"), "No original registration or behavior");
        check(retired.contains("mapping.getKey().equals(OLD_ID)") && retired.contains("mapping.remap(GTLEnhancedcoreItems.getPatternGenerator())"),
                "Only the removed item mapping migrates");
        var config = JsonParser.parseString(Files.readString(Path.of("src/main/resources/gtl_enhancedcore.mixins.json")))
                .getAsJsonObject();
        check(config.getAsJsonArray("mixins").asList().stream().filter(e -> e.getAsString().equals("gtlcore.GTLItemsDebugPatternToolMixin")).count() == 1,
                "Removal enabled exactly once on both sides");
        check(!config.getAsJsonArray("client").toString().contains("GTLItemsDebugPatternToolMixin"), "Removal is not client-only hiding");
        for (String lang : new String[]{"zh_cn", "en_us"}) {
            var translations = JsonParser.parseString(Files.readString(Path.of(
                    "src/main/resources/assets/gtl_enhancedcore/lang/" + lang + ".json"))).getAsJsonObject();
            check(translations.has("gtl_enhancedcore.structure.distorter_coils"), "Localized coil mismatch " + lang);
        }
        return assertions;
    }

    private static String source(String path) throws Exception { return Files.readString(JAVA.resolve(path)); }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        assertions++;
    }
}
