package com.gtl.enhancedcore;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;

/** Source contracts supplement the opt-in debug-tool registry startup checks. */
final class CoilDebugToolRegression {
    private static int assertions;
    private static final Path JAVA = Path.of("src/main/java/com/gtl/enhancedcore");

    static int run() throws Exception {
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
        return assertions;
    }

    private static String source(String path) throws Exception { return Files.readString(JAVA.resolve(path)); }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        assertions++;
    }
}
