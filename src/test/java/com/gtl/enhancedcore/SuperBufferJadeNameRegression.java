package com.gtl.enhancedcore;

import com.gtl.enhancedcore.integration.jade.SuperBufferJadeNames;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class SuperBufferJadeNameRegression {
    private static int assertions;

    private SuperBufferJadeNameRegression() {}

    public static void main(String[] arguments) throws Exception {
        System.out.println("Super buffer Jade name regression passed: " + run() + " assertions");
    }

    public static int run() throws Exception {
        int previous = assertions;
        String[] storedNames = {null, "", "我的总成", "gtceu.compressor", "Compressor", "压缩机", "车床", " "};
        String[] origins = {null, "", "gtceu.compressor", "压缩机", "车床"};
        for (String stored : storedNames) {
            for (String origin : origins) {
                for (boolean manual : new boolean[]{false, true}) {
                    var snapshot = SuperBufferJadeNames.select(stored, origin, manual, "gtceu.compressor", "压缩机");
                    boolean owned = !manual && stored != null && !stored.isEmpty() && stored.equals(origin);
                    String expectedKey = owned && (stored.equals("gtceu.compressor") || stored.equals("压缩机"))
                            ? "gtceu.compressor" : "";
                    check(snapshot.stored().equals(stored == null ? "" : stored), "Stored name changed in ownership matrix");
                    check(snapshot.translationKey().equals(expectedKey), "Automatic ownership was inferred from text");
                }
            }
        }
        check(SuperBufferJadeNames.select("gtceu.lathe", "gtceu.lathe", false, "gtceu.compressor", "压缩机")
                .translationKey().equals("gtceu.lathe"), "Proven legacy keys must not drift to the active mode");
        check(SuperBufferJadeNames.select("车床", "车床", false, "gtceu.compressor", "压缩机")
                .translationKey().isEmpty(), "A changed controller mode must not relabel the saved name");
        check(SuperBufferJadeNames.select("Compressor", "Compressor", false, "gtceu.compressor", "压缩机")
                .translationKey().isEmpty(), "Unresolved legacy English must remain the actual stored name");
        check(SuperBufferJadeNames.select("gtlcore.unknown_recipe", "gtlcore.unknown_recipe", false, "", "")
                .translationKey().equals("gtlcore.unknown_recipe"), "Missing resources must retain proven legacy keys");
        for (String invalidKey : new String[]{null, "", "gtceu:compressor", "gtceu compressor", "gtceu.压缩机",
                "gtceu." + "long".repeat(SuperBufferJadeNames.MAX_KEY_LENGTH)}) {
            check(SuperBufferJadeNames.select("压缩机", "压缩机", false, invalidKey, "压缩机")
                    .translationKey().isEmpty(), "Invalid translation key accepted");
        }
        var maximumName = "长".repeat(SuperBufferJadeNames.MAX_NAME_LENGTH);
        check(SuperBufferJadeNames.select(maximumName, "", true, "", "").stored().equals(maximumName),
                "A bounded manual name was truncated");
        check(SuperBufferJadeNames.select(maximumName + "长", "", true, "", "").stored().isEmpty(),
                "An oversized payload must leave the normal Jade title intact");
        check(new SuperBufferJadeNames.NameSnapshot("", "gtceu.compressor").translationKey().isEmpty(),
                "An empty name must never synthesize a translated title");
        check(new SuperBufferJadeNames.NameSnapshot(null, "gtceu.compressor").stored().isEmpty(),
                "A null name must remain empty");
        sourceContracts();
        return assertions - previous;
    }

    private static void sourceContracts() throws Exception {
        String provider = source("IvBufferInfoProvider.java");
        String helper = source("SuperBufferJadeNames.java");
        String plugin = source("GTLEnhancedcoreJadePlugin.java");
        int nameWrite = provider.indexOf("SuperBufferJadeNames.current(buffer).write(nameTag)");
        int isolatedWrite = provider.indexOf("if (IvBuffers.isolated(buffer))");
        check(nameWrite >= 0 && isolatedWrite > nameWrite, "Names must cover non-isolated super buffers");
        check(provider.contains("data.remove(KEY)"), "Reused responses must not keep another block's name");
        check(provider.contains("data.remove(NAME_KEY)") && provider.contains("data.put(NAME_KEY, nameTag)"),
                "New names must not make older clients display isolation-only status on normal buffers");
        check(provider.contains("private static final String KEY = \"gtlEnhancedcoreIvBuffer\""),
                "The existing Jade data key changed");
        check(provider.contains("return 10000;"), "The title override must run after Jade's object name provider");
        check(provider.contains("!tooltip.get(Identifiers.CORE_OBJECT_NAME).isEmpty()"),
                "The override must respect disabled Jade object titles");
        check(provider.contains("tooltip.remove(Identifiers.CORE_OBJECT_NAME)")
                        && provider.contains("IThemeHelper.get().title(name.display()), Identifiers.CORE_OBJECT_NAME"),
                "The real name must replace the title using Jade's theme and title tag");
        check(provider.contains("Tag.TAG_COMPOUND") && provider.contains("Tag.TAG_INT")
                        && provider.contains("Tag.TAG_BYTE") && provider.contains("tag.getInt(\"jobs\") < 0"),
                "Legacy and malformed status fields must be distinguished");
        check(helper.contains("SuperBufferAutoName.shouldTranslate") && helper.contains("SuperBufferNameAccess")
                        && helper.contains("SuperBufferNaming.automaticKey(buffer)"),
                "The existing name ownership and naming source must remain authoritative");
        check(helper.contains("GTRegistries.RECIPE_TYPES.values()")
                        && helper.contains("if (!unique.isEmpty() && !unique.equals(key)) return \"\";"),
                "Detached automatic names must use only an unambiguous registered key");
        check(helper.contains("Component.translatableWithFallback(translationKey, stored)"),
                "Automatic names must translate on the client and retain a literal fallback");
        check(helper.contains("data.contains(\"name\", Tag.TAG_STRING)")
                        && helper.contains("data.contains(\"nameKey\", Tag.TAG_STRING)"),
                "Name fields must be decoded by their actual NBT type");
        check(plugin.contains("registerBlockDataProvider(new IvBufferInfoProvider(), MetaMachineBlockEntity.class)")
                        && plugin.contains("registerBlockComponent(new IvBufferInfoProvider(), MetaMachineBlock.class)"),
                "The existing server and client registrations must include the name provider");
        for (String mutation : List.of("applyNames(", "enhanced$setAutomaticName(", "enhanced$restoreAutomaticName(",
                ".setCustomName(", "IvBuffers.bind(", "IvBuffers.cancel(", ".discardTasks(", ".jobs.clear(",
                ".accepting =", ".refreshNeeded =", ".load(")) {
            check(!provider.contains(mutation) && !helper.contains(mutation), "Jade reads mutate names or orders: " + mutation);
        }
        check(!provider.contains(".getString()") && !helper.contains(".getString()"),
                "The server must not materialize translated component strings");
    }

    private static String source(String filename) throws Exception {
        return Files.readString(Path.of("src/main/java/com/gtl/enhancedcore/integration/jade", filename),
                StandardCharsets.UTF_8);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        assertions++;
    }
}
