package com.gtl.enhancedcore.audit;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtl.enhancedcore.common.data.GTLEnhancedcoreRecipeTypes;
import com.gtl.enhancedcore.common.machine.GTLEnhancedcoreMachines;
import com.gtl.enhancedcore.common.recipe.FusionParallelPolicy;
import com.gtl.enhancedcore.common.recipe.iv.IvMachineScope;
import com.gtl.enhancedcore.common.structure.MegastructureMaintenancePolicy;
import com.gtl.enhancedcore.common.structure.SpaceElevatorMaintenance;
import com.gtl.enhancedcore.common.util.MachineTooltips;
import com.gtl.enhancedcore.common.util.TooltipPolicy;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "enhancedcore_audit", value = Dist.CLIENT)
public final class TooltipClientChecks {
    private static final String PROPERTY = "gtl.enhancedcore.functionalTipsAudit";
    private static final String MARKER = "[TOOLTIP_CLIENT]";
    private static final String POLICY = "default-catalog-unfolded-20261001";
    private static final List<String> LOCALES = List.of("zh_cn", "en_us", "zh_cn");
    private static final List<Long> SAMPLE_DELAYS_MILLIS = List.of(250L, 350L, 450L, 300L, 550L, 400L, 650L);
    private static final Set<String> ANIMATED_KEYS = Set.of(TooltipPolicy.SOURCE_KEY, TooltipPolicy.CROSS_RECIPE_KEY);
    private static final Map<String, String> RETIRED_MODULE_KEYS = Map.of(
            "gtceu:suprachronal_assembly_line", "gtceu.machine.suprachronal_assembly_line.tooltip.1",
            "gtceu:suprachronal_assembly_line_module", "gtceu.machine.suprachronal_assembly_line_module.tooltip.0");
    private static final Set<String> DIRECT_TARGETS = Set.of("fission_reactor", "star_ultimate_material_forge_factory",
            "engraving_laser_plant", "assembly_line", "circuit_assembly_line", "suprachronal_assembly_line_module");
    private static final Pattern UNEXPANDED_ARGUMENT = Pattern.compile("%(?:(\\d+)\\$)?s");
    private static final List<String> failures = new ArrayList<>();
    private static final Map<String, Map<String, List<String>>> snapshots = new LinkedHashMap<>();
    private static final JsonArray localeReports = new JsonArray();
    private static final List<AnimationTarget> animationTargets = new ArrayList<>();
    private static CompletableFuture<Void> reload;
    private static Field tooltipBuilderField;
    private static JsonObject activeLocaleReport;
    private static boolean done;
    private static boolean localeVerified;
    private static boolean isolationVerified;
    private static boolean runClaimed;
    private static boolean worldObserved;
    private static int localeIndex;
    private static int sampleRound;
    private static int checks;
    private static long started;
    private static long lastSampleNanos;
    private static Path directory;
    private static Path runDirectory;

    private record AnimationTarget(MachineDefinition definition, String key, BiConsumer<ItemStack, List<Component>> builder,
                                   List<Component> samples, List<Long> sampleNanos) {}

    private TooltipClientChecks() {}

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (done || !Boolean.getBoolean(PROPERTY) || event.phase != TickEvent.Phase.END) return;
        var client = Minecraft.getInstance();
        try {
            if (directory == null) directory = isolatedDirectory(client);
            boolean noWorld = client.level == null && client.player == null && client.getSingleplayerServer() == null;
            worldObserved |= !noWorld;
            require(noWorld, "The title-screen audit must never enter a world");
            if (reload != null && reload.isCompletedExceptionally()) reload.join();
            if (!(client.screen instanceof TitleScreen) || client.getOverlay() != null) return;
            if (started == 0) {
                require(Runtime.getRuntime().maxMemory() <= 4L * 1024 * 1024 * 1024, "Client heap exceeds the 4G limit");
                var heapArguments = java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments()
                        .stream().filter(argument -> argument.startsWith("-Xmx")).toList();
                require(heapArguments.size() == 1 && Set.of("-Xmx4G", "-Xmx4g", "-Xmx4096M", "-Xmx4096m")
                        .contains(heapArguments.getFirst()), "The title-only audit requires an explicit 4G heap");
                claimRun();
                started = System.nanoTime();
                GTLEnhancedcore.LOGGER.info("{} START title_only=true shift_keyboard=NOT_RUN heapMaxMiB={}",
                        MARKER, Runtime.getRuntime().maxMemory() / (1024 * 1024));
            }
            require(!Screen.hasShiftDown(), "Default-builder checks require real Shift state to be released");
            if (reload == null) {
                String locale = LOCALES.get(localeIndex);
                require(client.getLanguageManager().getLanguage(locale) != null, "Locale is not registered: " + locale);
                client.getLanguageManager().setSelected(locale);
                client.options.languageCode = locale;
                client.options.save();
                GTLEnhancedcore.LOGGER.info("{} RELOAD locale={} round={}", MARKER, locale, localeIndex + 1);
                reload = client.reloadResourcePacks();
                return;
            }
            if (!reload.isDone()) return;
            reload.join();
            if (!localeVerified) {
                verifyLocale(client, LOCALES.get(localeIndex));
                localeVerified = true;
                sampleRound = 1;
                lastSampleNanos = System.nanoTime();
                return;
            }
            if (!sampleBuilders(LOCALES.get(localeIndex))) return;
            reload = null;
            localeVerified = false;
            if (++localeIndex == LOCALES.size()) finish(client, null);
        } catch (Throwable error) {
            finish(client, error);
        }
    }

    private static Path isolatedDirectory(Minecraft client) throws Exception {
        String configured = System.getProperty("gtl.enhancedcore.functionalTipsAuditDirectory", "");
        require(!configured.isBlank(), "Missing isolated-directory property");
        Path expected = Path.of(configured).toAbsolutePath().normalize();
        Path actual = client.gameDirectory.toPath().toAbsolutePath().normalize();
        require(expected.getFileName() != null && expected.getParent() != null && expected.getParent().getParent() != null
                        && expected.getFileName().toString().equals("client")
                        && expected.getParent().getFileName().toString().equals("player-facing-tips-20261001")
                        && expected.getParent().getParent().getFileName().toString().equals("_audit")
                        && actual.equals(expected) && !Files.isSymbolicLink(actual)
                        && actual.toRealPath().equals(expected),
                "Refusing to inspect or write outside the isolated functional-tips client directory");
        isolationVerified = true;
        String runId = System.getProperty("gtl.enhancedcore.functionalTipsAuditRunId", "");
        require(runId.matches("[A-Za-z0-9][A-Za-z0-9_-]{7,95}"), "Missing or unsafe audit run ID");
        require(System.getProperty("gtl.enhancedcore.functionalTipsCandidateSha256", "").matches("[0-9a-fA-F]{64}"),
                "Missing candidate SHA-256 binding");
        for (String property : System.getProperties().stringPropertyNames()) {
            if (property.startsWith("gtl.enhancedcore.") && property.endsWith("Audit") && !property.equals(PROPERTY)) {
                require(!Boolean.getBoolean(property), "Another audit mode is active: " + property);
            }
        }
        Path saves = actual.resolve("saves");
        if (Files.exists(saves)) {
            try (var entries = Files.list(saves)) {
                require(entries.findAny().isEmpty(), "The title-only fixture contains worlds");
            }
        }
        return actual;
    }

    private static void claimRun() throws Exception {
        String runId = System.getProperty("gtl.enhancedcore.functionalTipsAuditRunId");
        runDirectory = directory.resolve("runs").resolve(runId).normalize();
        require(runDirectory.startsWith(directory) && !Files.isSymbolicLink(directory.resolve("runs"))
                        && !Files.isSymbolicLink(runDirectory), "Unsafe result-directory path");
        Files.createDirectories(runDirectory);
        require(runDirectory.toRealPath().equals(runDirectory), "The result directory resolves through an alias");
        require(!Files.exists(runDirectory.resolve("COMPLETE.json")) && !Files.exists(runDirectory.resolve("FAIL.json")),
                "Refusing to reuse a completed or failed audit run ID");
        Files.writeString(runDirectory.resolve("AUDIT_STARTED"), runId, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        runClaimed = true;
        tooltipBuilderField = MachineDefinition.class.getDeclaredField("tooltipBuilder");
        require(tooltipBuilderField.getDeclaringClass() == MachineDefinition.class
                        && BiConsumer.class.isAssignableFrom(tooltipBuilderField.getType()),
                "The verified MachineDefinition backing-field API changed");
        tooltipBuilderField.setAccessible(true);
    }

    private static void verifyLocale(Minecraft client, String locale) throws Exception {
        int previousChecks = checks;
        animationTargets.clear();
        activeLocaleReport = new JsonObject();
        activeLocaleReport.addProperty("locale", locale);
        activeLocaleReport.addProperty("round", localeIndex + 1);
        activeLocaleReport.add("catalogs", new JsonArray());
        activeLocaleReport.add("originalContent", new JsonArray());
        localeReports.add(activeLocaleReport);
        check(client.getLanguageManager().getSelected().equals(locale), "Selected locale differs after reload: " + locale);
        check(client.options.languageCode.equals(locale), "Options locale differs after reload: " + locale);
        var resource = client.getResourceManager().getResource(
                new ResourceLocation(GTLEnhancedcore.MOD_ID, "lang/" + locale + ".json")).orElseThrow();
        JsonObject translations;
        try (var reader = resource.openAsReader()) {
            translations = JsonParser.parseReader(reader).getAsJsonObject();
        }
        var normalizedTranslations = new LinkedHashMap<String, String>();
        try (var stream = resource.open()) {
            Language.loadFromJson(stream, normalizedTranslations::put);
        }
        check(translations.size() > 0, "Loaded language resource is empty: " + locale);
        for (var entry : translations.entrySet()) {
            check(Language.getInstance().has(entry.getKey()), "Missing loaded translation: " + locale + ":" + entry.getKey());
            check(Language.getInstance().getOrDefault(entry.getKey()).equals(normalizedTranslations.get(entry.getKey())),
                    "Runtime locale/fallback differs from the actual resource: " + locale + ":" + entry.getKey());
        }
        Language loadedLanguage = Language.getInstance();
        int jadeChecks = SuperBufferJadeNameChecks.run();
        checks += jadeChecks;
        check(jadeChecks > 0, "The loaded Jade helper did not inspect any name/protocol fixtures");
        int jadeTitleChecks = SuperBufferJadeClientTitleChecks.run();
        checks += jadeTitleChecks;
        check(jadeTitleChecks > 0, "The actual Jade tooltip/theme helper did not inspect any client titles");
        check(Language.getInstance() == loadedLanguage, "Jade name checks did not restore the actual loaded Language instance");
        check(client.getLanguageManager().getSelected().equals(locale) && client.options.languageCode.equals(locale),
                "Jade name checks changed the selected resource locale");
        int factoryChecks = 0;
        for (String key : ANIMATED_KEYS) factoryChecks += PlayerTooltipStyleChecks.verify(key);
        checks += factoryChecks;
        var owned = new HashSet<>(GTLEnhancedcoreMachines.all());
        require(!owned.isEmpty(), "Enhancedcore machine registrations are not ready");
        var registered = new ArrayList<>(GTRegistries.MACHINES.values());
        registered.sort(Comparator.comparing(definition -> definition.getId().toString()));
        check(registered.containsAll(owned), "Owned machine facade contains unregistered definitions");
        var rendered = new LinkedHashMap<String, List<String>>();
        var nativeSeen = new HashSet<String>();
        var suprachronalSeen = new HashSet<String>();
        int ownCount = 0;
        int foreignCount = 0;
        for (var definition : registered) {
            boolean own = owned.contains(definition);
            boolean expectedForeign = foreignTarget(definition.getId());
            if (!own && !expectedForeign) continue;
            var builder = definition.getTooltipBuilder();
            if (builder == null) {
                check(false, "Registered machine has no tooltip builder: " + definition.getId());
                continue;
            }
            var lines = build(definition, builder);
            long observedAt = System.nanoTime();
            var raw = rawBuilder(definition);
            require(raw != null, "Registered definition has no backing tooltip callback: " + definition.getId());
            var inherited = own ? List.<Component>of() : build(definition, raw);
            verifyDefault(definition, lines, inherited, own, locale);
            var repeated = build(definition, definition.getTooltipBuilder());
            check(semantics(lines).equals(semantics(repeated)), "Actual builder changes non-color semantics: " + definition.getId());
            for (String key : ANIMATED_KEYS) {
                if (count(lines, key) == 0) continue;
                var samples = new ArrayList<Component>();
                samples.add(onlyAnimatedRow(lines, key, definition.getId().toString()).copy());
                var sampleNanos = new ArrayList<Long>();
                sampleNanos.add(observedAt);
                animationTargets.add(new AnimationTarget(definition, key, builder, samples, sampleNanos));
            }
            var itemLines = definition.asStack().getTooltipLines(null, TooltipFlag.NORMAL);
            for (var line : itemLines) verifyTranslation(line, locale + ":item:" + definition.getId());
            if (!own) {
                check(count(itemLines, TooltipPolicy.SOURCE_KEY) == 1, "Full item tooltip lost/duplicated source: " + definition.getId());
                check(count(itemLines, TooltipPolicy.CROSS_RECIPE_KEY) == count(lines, TooltipPolicy.CROSS_RECIPE_KEY),
                        "Full item tooltip changed the declared cross-recipe row: " + definition.getId());
                verifyOriginal(definition, raw, inherited, lines, itemLines, locale);
                foreignCount++;
            } else {
                ownCount++;
            }
            if (IvMachineScope.nativeTarget(definition.getId())) nativeSeen.add(definition.getId().getPath());
            if (RETIRED_MODULE_KEYS.containsKey(definition.getId().toString())) suprachronalSeen.add(definition.getId().toString());
            rendered.put(definition.getId().toString(), lines.stream().map(TooltipClientChecks::visibleText).toList());
        }
        check(ownCount == owned.size(), "Not every owned registered builder was inspected");
        check(nativeSeen.equals(IvMachineScope.NATIVE_IDS), "Missing native tooltip targets: " + nativeSeen);
        check(suprachronalSeen.equals(RETIRED_MODULE_KEYS.keySet()), "The real host/retired-module definitions were not both inspected");
        check(foreignCount > 0, "No third-party wrappers were inspected");
        check(animationTargets.stream().map(AnimationTarget::key).collect(java.util.stream.Collectors.toSet())
                .equals(ANIMATED_KEYS), "Actual registered builders did not expose both dynamic project keys");
        nesting();
        layoutFixtures();
        var previous = snapshots.putIfAbsent(locale, rendered);
        if (previous != null) check(previous.equals(rendered), "Language round trip changed actual default rows: " + locale);
        if (locale.equals("en_us")) check(!snapshots.get("zh_cn").equals(rendered), "Language change did not change actual tooltip text");
        activeLocaleReport.addProperty("resource", resource.sourcePackId());
        activeLocaleReport.addProperty("translationKeys", translations.size());
        activeLocaleReport.addProperty("ownedBuilders", ownCount);
        activeLocaleReport.addProperty("foreignBuilders", foreignCount);
        activeLocaleReport.addProperty("nativeBuilders", nativeSeen.size());
        activeLocaleReport.addProperty("jadeNameProtocolChecks", jadeChecks);
        activeLocaleReport.addProperty("jadeRealClientTitleChecks", jadeTitleChecks);
        activeLocaleReport.addProperty("factoryShapeChecks", factoryChecks);
        activeLocaleReport.addProperty("animationTargets", animationTargets.size());
        activeLocaleReport.addProperty("checksBeforeAnimation", checks - previousChecks);
        activeLocaleReport.addProperty("failuresBeforeAnimation", failures.size());
        GTLEnhancedcore.LOGGER.info("{} LOCALE locale={} owned={} foreign={} native={} checks={} failures={} shift=LOGIC_ONLY",
                MARKER, locale, ownCount, foreignCount, nativeSeen.size(), checks, failures.size());
    }

    @SuppressWarnings("unchecked")
    private static BiConsumer<ItemStack, List<Component>> rawBuilder(MachineDefinition definition) throws IllegalAccessException {
        return (BiConsumer<ItemStack, List<Component>>) tooltipBuilderField.get(definition);
    }

    private static Component onlyAnimatedRow(List<Component> lines, String key, String context) {
        var matching = lines.stream().filter(line -> MachineTooltips.containsKey(line, key)).toList();
        require(matching.size() == 1, "Expected one actual animated builder row: " + context + ":" + key);
        return matching.getFirst();
    }

    private static boolean sampleBuilders(String locale) {
        long delayNanos = SAMPLE_DELAYS_MILLIS.get(sampleRound - 1) * 1_000_000L;
        if (System.nanoTime() - lastSampleNanos < delayNanos) return false;
        int previousChecks = checks;
        var sampled = new LinkedHashMap<MachineDefinition, List<Component>>();
        var sampleTimes = new LinkedHashMap<MachineDefinition, Long>();
        for (AnimationTarget target : animationTargets) {
            if (!sampled.containsKey(target.definition())) {
                var actualRows = build(target.definition(), target.builder());
                sampleTimes.put(target.definition(), System.nanoTime());
                sampled.put(target.definition(), actualRows);
                var freshRows = build(target.definition(), target.definition().getTooltipBuilder());
                check(semantics(actualRows).equals(semantics(freshRows)),
                        "A retained real builder and fresh registered getter differ semantically: " + locale + ":" + target.definition().getId());
            }
            var row = onlyAnimatedRow(sampled.get(target.definition()), target.key(), locale + ":" + target.definition().getId());
            target.samples().add(row.copy());
            target.sampleNanos().add(sampleTimes.get(target.definition()));
        }
        lastSampleNanos = System.nanoTime();
        sampleRound++;
        if (sampleRound < SAMPLE_DELAYS_MILLIS.size() + 1) return false;
        var reports = new JsonArray();
        activeLocaleReport.add("builderAnimation", reports);
        for (AnimationTarget target : animationTargets) {
            var report = new JsonObject();
            report.addProperty("machine", target.definition().getId().toString());
            report.addProperty("key", target.key());
            report.addProperty("callback", "REUSED_ACTUAL_REGISTERED_GETTER_CALLBACK");
            report.addProperty("sampling", "CLIENT_END_TICK_NO_SLEEP");
            var samples = new JsonArray();
            report.add("samples", samples);
            reports.add(report);
            for (int index = 0; index < target.samples().size(); index++) {
                long sampleAt = target.sampleNanos().get(index);
                long gap = index == 0 ? 0 : sampleAt - target.sampleNanos().get(index - 1);
                if (index > 0) check(gap >= 250_000_000L, "Actual builder samples are less than 250ms apart: " + target.definition().getId());
                var sample = new JsonObject();
                sample.addProperty("elapsedNanos", sampleAt - target.sampleNanos().getFirst());
                sample.addProperty("gapNanos", gap);
                var color = target.samples().get(index).getStyle().getColor();
                sample.addProperty("color", color == null ? "MISSING" : String.format(java.util.Locale.ROOT, "#%06X", color.getValue()));
                samples.add(sample);
            }
            checks += PlayerTooltipStyleChecks.verifyAnimation(target.key(), List.copyOf(target.samples()));
            report.addProperty("nativePaletteAndColorProgress", true);
        }
        activeLocaleReport.addProperty("animationFinalBatchChecks", checks - previousChecks);
        activeLocaleReport.addProperty("animationSampleCountPerTarget", sampleRound);
        activeLocaleReport.addProperty("checksCumulative", checks);
        activeLocaleReport.addProperty("failuresCumulative", failures.size());
        GTLEnhancedcore.LOGGER.info("{} ANIMATION locale={} builders={} samples_each={} physical_flicker=NOT_RUN",
                MARKER, locale, animationTargets.size(), sampleRound);
        return true;
    }

    private static boolean foreignTarget(ResourceLocation id) {
        return IvMachineScope.nativeTarget(id) || FusionParallelPolicy.limit(id.getNamespace(), id.getPath()) > 0
                || MegastructureMaintenancePolicy.matches(id.getNamespace(), id.getPath()) || SpaceElevatorMaintenance.matches(id)
                || id.getNamespace().equals("gtceu") && DIRECT_TARGETS.contains(id.getPath());
    }

    private static List<Component> build(MachineDefinition definition, BiConsumer<ItemStack, List<Component>> builder) {
        var lines = new ArrayList<Component>();
        builder.accept(definition.asStack(), lines);
        return List.copyOf(lines);
    }

    private static List<String> recipeKeys(MachineDefinition definition, boolean activeOnly) {
        var keys = new LinkedHashSet<String>();
        var recipes = definition.getRecipeTypes();
        if (recipes == null) return List.of();
        for (GTRecipeType recipe : recipes) {
            require(recipe != null && recipe.registryName != null, "Invalid actual recipe-type metadata: " + definition.getId());
            if (activeOnly && (recipe == GTLEnhancedcoreRecipeTypes.CAUSALITY_TERMINAL
                    || recipe == GTLEnhancedcoreRecipeTypes.WIRELESS_CHARGER)) continue;
            var id = recipe.registryName;
            keys.add(id.getNamespace() + "." + id.getPath());
        }
        if (activeOnly && definition.getId().toString().equals("gtceu:suprachronal_assembly_line_module")) return List.of();
        return List.copyOf(keys);
    }

    private static JsonObject verifyCatalog(MachineDefinition definition, List<Component> lines, String context) {
        var expected = recipeKeys(definition, true);
        var listed = new ArrayList<String>();
        var groups = new JsonArray();
        check(count(lines, TooltipPolicy.RECIPE_GROUP) == 0, "Old continuation wording remains in a default catalog: " + context);
        for (Component line : lines) {
            var keys = new ArrayList<String>();
            translationKeys(line, keys);
            check(keys.stream().noneMatch(key -> key.startsWith("gtceu.machine.available_recipe_map_")),
                    "Original recipe-map wording was not normalized: " + context);
            if (!MachineTooltips.containsKey(line, TooltipPolicy.RECIPE_HEADER)) continue;
            if (!(line.getContents() instanceof TranslatableContents contents) || !contents.getKey().equals(TooltipPolicy.RECIPE_HEADER)) {
                check(false, "Catalog group does not use a top-level translatable header: " + context);
                continue;
            }
            check(contents.getArgs().length == 1 && contents.getArgs()[0] instanceof Component,
                    "Catalog header must receive one component argument: " + context);
            if (contents.getArgs().length != 1 || !(contents.getArgs()[0] instanceof Component names)) continue;
            var namesAndSeparators = new ArrayList<String>();
            translationKeys(names, namesAndSeparators);
            var namesInGroup = namesAndSeparators.stream().filter(key -> !key.equals(TooltipPolicy.RECIPE_SEPARATOR)).toList();
            check(!namesInGroup.isEmpty() && namesInGroup.size() <= TooltipPolicy.RECIPES_PER_LINE,
                    "Default catalog group must contain 1-4 actual recipe types: " + context + ":" + namesInGroup);
            check(namesAndSeparators.stream().filter(TooltipPolicy.RECIPE_SEPARATOR::equals).count() == namesInGroup.size() - 1L,
                    "Catalog group separators do not match the actual names: " + context);
            check(names.getString().equals(joinRecipeNames(namesInGroup)), "Catalog contains an unverified literal or incomplete type name: " + context);
            listed.addAll(namesInGroup);
            groups.add(new GsonBuilder().create().toJsonTree(namesInGroup));
        }
        check(listed.equals(expected), "Default catalog differs from the complete actual active recipe types: " + context
                + " expected=" + expected + " listed=" + listed);
        check(listed.size() == new HashSet<>(listed).size(), "An actual type is duplicated across catalog groups: " + context);
        var report = new JsonObject();
        report.addProperty("machine", definition.getId().toString());
        report.add("registeredMetadata", new GsonBuilder().create().toJsonTree(recipeKeys(definition, false)));
        report.add("expectedActiveTypes", new GsonBuilder().create().toJsonTree(expected));
        report.add("defaultGroups", groups);
        if (definition.getId().toString().equals("gtceu:suprachronal_assembly_line_module")) {
            report.addProperty("metadataException", "USER_DISABLED_EXTENSION_MODULE_NO_ACTIVE_RECIPE_MAP");
        } else if (!recipeKeys(definition, false).equals(expected)) {
            report.addProperty("metadataException", "EXACT_EMPTY_CAUSALITY_TERMINAL_OR_WIRELESS_CHARGER_TYPES");
        }
        return report;
    }

    private static String joinRecipeNames(List<String> keys) {
        String separator = Component.translatable(TooltipPolicy.RECIPE_SEPARATOR).getString();
        return String.join(separator, keys.stream().map(key -> Component.translatable(key).getString()).toList());
    }

    private static void translationKeys(Component component, List<String> keys) {
        if (component.getContents() instanceof TranslatableContents contents) {
            keys.add(contents.getKey());
            for (Object argument : contents.getArgs()) if (argument instanceof Component nested) translationKeys(nested, keys);
        }
        for (Component sibling : component.getSiblings()) translationKeys(sibling, keys);
    }

    private static boolean recipeRow(Component component) {
        var keys = new ArrayList<String>();
        translationKeys(component, keys);
        return keys.stream().anyMatch(key -> key.startsWith("gtceu.machine.available_recipe_map_")
                || key.equals(TooltipPolicy.RECIPE_HEADER) || key.equals(TooltipPolicy.RECIPE_GROUP));
    }

    private static void verifyOriginal(MachineDefinition definition, BiConsumer<ItemStack, List<Component>> raw,
                                       List<Component> inherited, List<Component> lines, List<Component> itemLines, String locale) {
        String context = locale + ":" + definition.getId();
        String retiredKey = RETIRED_MODULE_KEYS.get(definition.getId().toString());
        Set<String> declaredRemovals = retiredKey == null ? Set.of()
                : TooltipPolicy.replacedKeys(definition.getId().getPath().equals("suprachronal_assembly_line")
                        ? "suprachronal_module_host" : "suprachronal_module");
        var retained = new ArrayList<Component>();
        var exceptions = new JsonArray();
        int recipeRewrites = 0;
        for (Component original : inherited) {
            String removedKey = declaredRemovals.stream().filter(key -> MachineTooltips.containsKey(original, key))
                    .sorted().findFirst().orElse(null);
            if (removedKey != null) {
                var exception = new JsonObject();
                exception.addProperty("key", removedKey);
                exception.addProperty("original", visibleText(original));
                exception.addProperty("reason", "USER_REQUESTED_MODULE_PROMISE_REMOVAL");
                exceptions.add(exception);
            } else if (recipeRow(original)) {
                recipeRewrites++;
            } else {
                retained.add(original);
            }
        }
        var expectedCounts = semanticCounts(retained);
        var defaultCounts = semanticCounts(lines);
        var itemCounts = semanticCounts(itemLines);
        var evidence = new JsonArray();
        for (var entry : expectedCounts.entrySet()) {
            int defaultCount = defaultCounts.getOrDefault(entry.getKey(), 0);
            int itemCount = itemCounts.getOrDefault(entry.getKey(), 0);
            check(defaultCount == entry.getValue(), "Original default meaning/count changed: " + context + ":" + entry.getKey());
            check(itemCount == entry.getValue(), "Full Forge item tooltip removed or duplicated original meaning: " + context + ":" + entry.getKey());
            var row = new JsonObject();
            row.addProperty("semantic", entry.getKey());
            row.addProperty("originalCount", entry.getValue());
            row.addProperty("defaultCount", defaultCount);
            row.addProperty("itemCount", itemCount);
            evidence.add(row);
        }
        var expectedOrder = semantics(retained);
        int cursor = 0;
        for (String meaning : semantics(lines)) {
            if (cursor < expectedOrder.size() && expectedOrder.get(cursor).equals(meaning)) cursor++;
        }
        check(cursor == expectedOrder.size(), "Foreign original default information was reordered or folded: " + context);
        if (retiredKey != null) {
            for (String removedKey : declaredRemovals) {
                check(count(itemLines, removedKey) == 0 && count(lines, removedKey) == 0,
                        "A declared module removal escaped into the full item tooltip: " + context + ":" + removedKey);
            }
            String warningKey = definition.getId().getPath().equals("suprachronal_assembly_line")
                    ? "tooltip.gtl_enhancedcore.suprachronal_no_modules" : "tooltip.gtl_enhancedcore.suprachronal_module_disabled";
            check(count(itemLines, warningKey) == 1, "Full item tooltip lost/duplicated the module warning: " + context);
        }
        var report = new JsonObject();
        report.addProperty("machine", definition.getId().toString());
        report.addProperty("unwrappedCallback", raw.getClass().getName());
        report.addProperty("rawRows", inherited.size());
        report.addProperty("recipeMapRewrites", recipeRewrites);
        report.addProperty("retiredModuleKey", retiredKey);
        report.addProperty("retiredModuleRawRows", retiredKey == null ? 0 : count(inherited, retiredKey));
        report.add("declaredModuleReplacementKeys", new GsonBuilder().create().toJsonTree(declaredRemovals.stream().sorted().toList()));
        report.add("intentionalRemovals", exceptions);
        report.add("retainedMeaningCounts", evidence);
        activeLocaleReport.getAsJsonArray("originalContent").add(report);
    }

    private static List<String> semantics(List<Component> lines) {
        return lines.stream().map(line -> semanticJson(JsonParser.parseString(Component.Serializer.toJson(line))).toString()).toList();
    }

    private static Map<String, Integer> semanticCounts(List<Component> lines) {
        var counts = new LinkedHashMap<String, Integer>();
        for (String meaning : semantics(lines)) counts.merge(meaning, 1, Integer::sum);
        return counts;
    }

    private static JsonElement semanticJson(JsonElement source) {
        if (source.isJsonObject()) {
            var properties = new TreeMap<String, JsonElement>();
            for (var entry : source.getAsJsonObject().entrySet()) {
                if (!Set.of("color", "bold", "italic", "underlined", "strikethrough", "obfuscated", "font", "insertion",
                        "clickEvent", "hoverEvent").contains(entry.getKey())) properties.put(entry.getKey(), entry.getValue());
            }
            var result = new JsonObject();
            properties.forEach((key, value) -> result.add(key, semanticJson(value)));
            return result;
        }
        if (source.isJsonArray()) {
            var result = new JsonArray();
            for (JsonElement element : source.getAsJsonArray()) result.add(semanticJson(element));
            return result;
        }
        if (source.isJsonPrimitive() && source.getAsJsonPrimitive().isString()) {
            return new JsonPrimitive(ChatFormatting.stripFormatting(source.getAsString()));
        }
        return source.deepCopy();
    }

    private static String visibleText(Component component) {
        return ChatFormatting.stripFormatting(component.getString());
    }

    private static void verifyDefault(MachineDefinition definition, List<Component> lines, List<Component> inherited,
                                      boolean own, String locale) {
        String context = locale + ":" + definition.getId();
        int hints = count(lines, TooltipPolicy.SHIFT_HINT);
        activeLocaleReport.getAsJsonArray("catalogs").add(verifyCatalog(definition, lines, context));
        for (var line : lines) {
            verifyTranslation(line, context);
            check(!UNEXPANDED_ARGUMENT.matcher(line.getString()).find(), "Unexpanded runtime argument: " + context + ":" + line.getString());
        }
        if (own) {
            long summaryRows = lines.stream().filter(line -> !recipeRow(line)
                    && !MachineTooltips.containsKey(line, TooltipPolicy.SHIFT_HINT)).count();
            check(hints <= 1, "Duplicate core-only Shift hint: " + context);
            check(summaryRows <= TooltipPolicy.MAX_SUMMARY_LINES, "Core summary exceeds 10 rows excluding recipes: " + context);
            var candidates = TooltipPolicy.pages().entrySet().stream()
                    .filter(entry -> entry.getValue().summary().stream().allMatch(key -> count(lines, key) == 1)).toList();
            check(candidates.size() == 1, "Missing or ambiguous declared summary: " + context + " pages="
                    + candidates.stream().map(Map.Entry::getKey).toList());
            if (candidates.size() == 1) expandedLogic(candidates.getFirst().getKey(), context);
        } else {
            check(hints == count(inherited, TooltipPolicy.SHIFT_HINT), "Project wrapper added a Shift menu to foreign defaults: " + context);
            for (String page : foreignPages(definition.getId())) {
                for (String key : TooltipPolicy.page(page).summary()) check(count(lines, key) == 1,
                        "Registered getter skipped a declared third-party summary: " + context + ":" + page + ":" + key);
                for (String key : TooltipPolicy.page(page).details()) check(count(lines, key) == 1,
                        "Project detail was hidden instead of remaining default-visible: " + context + ":" + page + ":" + key);
                foreignShiftLogic(page, inherited, lines, context);
            }
            check(count(lines, TooltipPolicy.SOURCE_KEY) == 1, "Missing/duplicate dynamic modification source: " + context);
            boolean crossRecipe = IvMachineScope.nativeTarget(definition.getId()) && recipeKeys(definition, false).size() > 1;
            check(count(lines, TooltipPolicy.CROSS_RECIPE_KEY) == (crossRecipe ? 1 : 0),
                    "Cross-recipe claim differs from the actual native isolation/type contract: " + context);
            if (IvMachineScope.nativeTarget(definition.getId())) {
                for (String key : TooltipPolicy.page("native_isolation").summary()) check(count(lines, key) == 1,
                        "Native input/capacity requirement is missing or duplicated: " + context + ":" + key);
                for (String key : TooltipPolicy.nativeSummaryKeys(definition.getId().getPath())) check(count(lines, key) >= 1,
                        "Original machine-specific requirement was hidden: " + context + ":" + key);
            }
            String retiredKey = RETIRED_MODULE_KEYS.get(definition.getId().toString());
            if (retiredKey != null) {
                check(count(lines, retiredKey) == 0, "Removed module promise remains default-visible: " + context + ":" + retiredKey);
                String warningKey = definition.getId().getPath().equals("suprachronal_assembly_line")
                        ? "tooltip.gtl_enhancedcore.suprachronal_no_modules" : "tooltip.gtl_enhancedcore.suprachronal_module_disabled";
                check(count(lines, warningKey) == 1, "Host/module disable warning is not default-visible exactly once: " + context);
                if (definition.getId().getPath().equals("suprachronal_assembly_line")) {
                    check(count(inherited, "gtceu.machine.suprachronal_assembly_line.tooltip.0") > 0,
                            "The unwrapped host callback did not expose its real original flavor");
                    check(count(lines, "gtceu.machine.suprachronal_assembly_line.tooltip.0")
                                    == count(inherited, "gtceu.machine.suprachronal_assembly_line.tooltip.0"),
                            "Host flavor was removed, collapsed, or duplicated");
                }
            }
        }
    }

    private static List<String> foreignPages(ResourceLocation id) {
        var pages = new ArrayList<String>();
        if (IvMachineScope.nativeTarget(id)) pages.addAll(TooltipPolicy.nativePages(id.getPath()));
        if (FusionParallelPolicy.limit(id.getNamespace(), id.getPath()) > 0) pages.add("fusion_reactor");
        if (MegastructureMaintenancePolicy.matches(id.getNamespace(), id.getPath()))
            pages.add("optional_maintenance");
        if (SpaceElevatorMaintenance.matches(id)) pages.add("forbidden_maintenance");
        if (id.getNamespace().equals("gtceu")) {
            switch (id.getPath()) {
                case "fission_reactor", "engraving_laser_plant" -> pages.add(id.getPath());
                case "star_ultimate_material_forge_factory" -> pages.add("star_ultimate_material_forge");
                case "assembly_line", "circuit_assembly_line" -> pages.add("pattern_buffer");
                case "suprachronal_assembly_line_module" -> pages.add("suprachronal_module");
                default -> {}
            }
        }
        return List.copyOf(pages);
    }

    private static void expandedLogic(String page, String context) {
        var layout = TooltipPolicy.page(page);
        var selected = layout.select(true, TooltipPolicy.SHIFT_HINT);
        check(selected.containsAll(layout.summary()) && selected.containsAll(layout.details()), "Expanded layout omits declared rows: " + context);
        check(!selected.contains(TooltipPolicy.SHIFT_HINT), "Expanded layout retains collapsed hint: " + context);
        for (String key : selected) check(Language.getInstance().has(key), "Expanded-layout resource translation is missing: " + context + ":" + key);
        if (page.equals("universal_joint_factory")) check(selected.contains(TooltipPolicy.JOINT_FACTORY_FLAVOR),
                "Expanded logic omits the requested flavor: " + context);
    }

    private static void foreignShiftLogic(String page, List<Component> inherited, List<Component> actual, String context) {
        var declared = TooltipPolicy.page(page);
        var catalog = actual.stream().filter(TooltipClientChecks::recipeRow).toList();
        var additions = new TooltipPolicy.Layout<>(declared.summary(), declared.details(), semantics(catalog));
        var original = semantics(inherited);
        var defaults = TooltipPolicy.compose(additions, original, ignored -> false, ignored -> false, false, null);
        var expanded = TooltipPolicy.compose(additions, original, ignored -> false, ignored -> false, true, null);
        check(defaults.equals(expanded), "Project foreign composition changes information with Shift: " + context + ":" + page);
        check(defaults.containsAll(original) && defaults.containsAll(declared.summary()) && defaults.containsAll(declared.details())
                && defaults.containsAll(additions.recipeRows()), "Foreign default composition omitted original/details/catalog: " + context + ":" + page);
        check(!defaults.contains(TooltipPolicy.SHIFT_HINT), "Foreign project composition introduced its own Shift menu: " + context);
        for (String key : declared.summary()) check(Language.getInstance().has(key), "Missing declared foreign summary translation: " + context + ":" + key);
        for (String key : declared.details()) check(Language.getInstance().has(key), "Missing declared default foreign detail translation: " + context + ":" + key);
    }

    private static void layoutFixtures() {
        int previousChecks = checks;
        var summary = new ArrayList<>(List.of("summary"));
        var details = new ArrayList<>(List.of("detail"));
        var recipeRows = new ArrayList<>(List.of("recipe-row"));
        var layout = new TooltipPolicy.Layout<>(summary, details, recipeRows);
        summary.clear();
        details.clear();
        recipeRows.clear();
        check(layout.summary().equals(List.of("summary")) && layout.details().equals(List.of("detail"))
                && layout.recipeRows().equals(List.of("recipe-row")), "Layout did not snapshot all three independent inputs");
        expectFailure(() -> layout.summary().clear(), UnsupportedOperationException.class, "Mutable layout summary");
        expectFailure(() -> layout.details().clear(), UnsupportedOperationException.class, "Mutable layout details");
        expectFailure(() -> layout.recipeRows().clear(), UnsupportedOperationException.class, "Mutable layout catalog");
        var manyRecipes = java.util.stream.IntStream.range(0, TooltipPolicy.MAX_SUMMARY_LINES + 3)
                .mapToObj(index -> "recipe-" + index).toList();
        var independent = new TooltipPolicy.Layout<>(List.of("summary"), List.of("detail"), manyRecipes);
        check(independent.recipeRows().size() > TooltipPolicy.MAX_SUMMARY_LINES, "Recipes were incorrectly charged against the core summary limit");
        var inherited = List.of("upstream", "upstream", "original-credit", "original-flavor");
        var defaults = TooltipPolicy.compose(independent, inherited, ignored -> false, ignored -> false, false, null);
        var expanded = TooltipPolicy.compose(independent, inherited, ignored -> false, ignored -> false, true, null);
        check(defaults.equals(expanded) && defaults.containsAll(manyRecipes) && defaults.contains("detail"),
                "Default foreign composition hides details/catalog or depends on Shift");
        check(defaults.stream().filter("upstream"::equals).count() == 2 && defaults.contains("original-credit")
                && defaults.contains("original-flavor"), "Foreign composition erased original multiplicity/flavor/credit");
        expectFailure(() -> defaults.clear(), UnsupportedOperationException.class, "Mutable composed defaults");
        expectFailure(() -> layout.select(false, null), NullPointerException.class, "Core optional details accepted a missing hint");
        expectFailure(() -> TooltipPolicy.page("__enhancedcore_audit_missing_page__"), NullPointerException.class, "Unknown tooltip page accepted");
        expectFailure(() -> new TooltipPolicy.Layout<>(java.util.Collections.nCopies(TooltipPolicy.MAX_SUMMARY_LINES + 1, "summary"), List.of()),
                IllegalArgumentException.class, "Oversized core summary accepted");
        expectFailure(() -> new TooltipPolicy.Layout<>(List.of(), java.util.Collections.nCopies(TooltipPolicy.MAX_DETAIL_LINES + 1, "detail")),
                IllegalArgumentException.class, "Oversized optional details accepted");
        expectFailure(() -> new TooltipPolicy.Layout<>(java.util.Arrays.asList("summary", null), List.of()),
                NullPointerException.class, "Null layout member accepted");
        var emptyCatalog = new ArrayList<Component>();
        MachineTooltips.create("native_isolation", new GTRecipeType[0]).accept(ItemStack.EMPTY, emptyCatalog);
        for (GTRecipeType[] recipeFixture : new GTRecipeType[][]{null, new GTRecipeType[]{null}, new GTRecipeType[0]}) {
            var safeCatalog = new ArrayList<Component>();
            MachineTooltips.create("native_isolation", recipeFixture).accept(ItemStack.EMPTY, safeCatalog);
            check(semanticCounts(safeCatalog).equals(semanticCounts(emptyCatalog)),
                    "Empty/unusable recipe metadata changed the normal tooltip body");
            check(count(safeCatalog, TooltipPolicy.RECIPE_HEADER) == 0 && count(safeCatalog, TooltipPolicy.RECIPE_GROUP) == 0,
                    "Empty/unusable recipe metadata produced a phantom recipe catalog");
        }
        var recipeDefinition = GTRegistries.MACHINES.get(new ResourceLocation("gtceu", "large_chemical_reactor"));
        require(recipeDefinition != null, "Recipe fallback fixture controller is not registered");
        var actualTypes = recipeDefinition.getRecipeTypes();
        require(actualTypes != null && actualTypes.length > 0 && actualTypes[0] != null && actualTypes[0].registryName != null,
                "Recipe fallback fixture has invalid registered recipe metadata");
        var typedCatalog = new ArrayList<Component>();
        MachineTooltips.create("native_isolation", actualTypes[0]).accept(ItemStack.EMPTY, typedCatalog);
        var mixedCatalog = new ArrayList<Component>();
        MachineTooltips.create("native_isolation", new GTRecipeType[]{null, actualTypes[0], null}).accept(ItemStack.EMPTY, mixedCatalog);
        check(semanticCounts(mixedCatalog).equals(semanticCounts(typedCatalog)),
                "Null recipe placeholders discarded or duplicated a valid registered recipe type");
        var actualRecipeId = actualTypes[0].registryName;
        check(count(mixedCatalog, TooltipPolicy.RECIPE_HEADER) == 1
                        && count(mixedCatalog, actualRecipeId.getNamespace() + "." + actualRecipeId.getPath()) == 1,
                "Mixed valid/null recipe metadata lost the real default-visible recipe catalog");
        var types = java.util.stream.IntStream.range(0, 9).mapToObj(index -> "type-" + index).toList();
        var groups = TooltipPolicy.recipeGroups(types);
        check(groups.size() == 3 && groups.getFirst().size() == 4 && groups.getLast().size() == 1, "Recipe grouping is not 4/4/1");
        check(groups.stream().flatMap(List::stream).toList().equals(types), "Recipe grouping dropped or reordered actual types");
        expectFailure(() -> groups.clear(), UnsupportedOperationException.class, "Mutable recipe groups");
        expectFailure(() -> groups.getFirst().clear(), UnsupportedOperationException.class, "Mutable inner recipe group");
        activeLocaleReport.addProperty("layoutFixtureChecks", checks - previousChecks);
    }

    private static void expectFailure(Runnable action, Class<? extends Throwable> expected, String message) {
        try {
            action.run();
        } catch (Throwable error) {
            check(expected.isInstance(error), message + ": wrong failure type " + error.getClass().getName());
            return;
        }
        check(false, message);
    }

    private static void nesting() throws Exception {
        var definition = GTRegistries.MACHINES.get(new ResourceLocation("gtceu", "dimensional_focus_engraving_array"));
        require(definition != null, "Dimensional array is not registered");
        var raw = rawBuilder(definition);
        require(raw != null, "Dimensional array has no upstream builder");
        var actual = build(definition, definition.getTooltipBuilder());
        var coil = actual.stream().filter(line -> MachineTooltips.containsKey(line, "gtceu.multiblock.coil_parallel"))
                .findFirst().orElseThrow();
        try {
            var markers = new ArrayList<Component>();
            for (int depth = 0; depth < 4; depth++) {
                var inner = definition.getTooltipBuilder();
                var marker = coil.copy().append(Component.literal(" [audit-wrapper-" + depth + "]"));
                markers.add(marker);
                definition.setTooltipBuilder((stack, lines) -> {
                    inner.accept(stack, lines);
                    lines.add(marker.copy());
                });
                var nested = build(definition, definition.getTooltipBuilder());
                var expected = new ArrayList<>(actual);
                expected.addAll(markers);
                check(semanticCounts(nested).equals(semanticCounts(expected)), "Actual nested getter changed original meaning/counts: depth=" + depth);
                for (var required : markers) check(semanticCounts(nested).getOrDefault(semantics(List.of(required)).getFirst(), 0) == 1,
                        "Actual nested getter removed or duplicated an upstream hard requirement: depth=" + depth);
                verifyCatalog(definition, nested, "nested-depth-" + depth);
                for (String key : TooltipPolicy.page("native_isolation").summary()) check(count(nested, key) == 1,
                        "Actual nested wrapping duplicated native summary: " + key);
                check(count(nested, TooltipPolicy.SOURCE_KEY) == 1 && count(nested, TooltipPolicy.CROSS_RECIPE_KEY) == 1
                                && count(nested, TooltipPolicy.SHIFT_HINT) == count(actual, TooltipPolicy.SHIFT_HINT),
                        "Actual nested wrapping duplicated source/hint");
            }
        } finally {
            definition.setTooltipBuilder(raw);
        }
        boolean restored = tooltipBuilderField.get(definition) == raw;
        check(restored, "Probe did not restore the exact raw registered builder");
        activeLocaleReport.addProperty("nestedGetterRawIdentityRestored", restored);
        var dimensionThenNative = MachineTooltips.modifyNative(MachineTooltips.modifyWithRecipes(raw,
                "dimensional_focus_engraving_array", definition.getRecipeTypes()), definition.getId().getPath(), definition.getRecipeTypes());
        var nativeThenDimension = MachineTooltips.modifyWithRecipes(MachineTooltips.modifyNative(raw,
                definition.getId().getPath(), definition.getRecipeTypes()), "dimensional_focus_engraving_array", definition.getRecipeTypes());
        if (MegastructureMaintenancePolicy.matches(definition.getId().getNamespace(), definition.getId().getPath())) {
            dimensionThenNative = MachineTooltips.modify(dimensionThenNative, "optional_maintenance");
            nativeThenDimension = MachineTooltips.modify(nativeThenDimension, "optional_maintenance");
        }
        var first = build(definition, dimensionThenNative);
        var second = build(definition, nativeThenDimension);
        check(semanticCounts(first).equals(semanticCounts(second)), "Third-party wrapping order changes default meanings/counts");
        check(semanticCounts(actual).equals(semanticCounts(first)), "Actual registered default differs from explicit wrapper composition");
        for (var rows : List.of(first, second)) {
            check(count(rows, TooltipPolicy.SOURCE_KEY) == 1 && count(rows, TooltipPolicy.CROSS_RECIPE_KEY) == 1
                            && count(rows, TooltipPolicy.SHIFT_HINT) == count(actual, TooltipPolicy.SHIFT_HINT),
                    "Explicit nesting duplicated source/hint");
            verifyCatalog(definition, rows, "explicit-wrapper-order");
        }
    }

    private static void verifyTranslation(Component component, String context) {
        if (component.getContents() instanceof TranslatableContents contents) {
            check(Language.getInstance().has(contents.getKey()), "Missing actual component translation: " + context + ":" + contents.getKey());
            for (Object argument : contents.getArgs()) if (argument instanceof Component nested) verifyTranslation(nested, context);
        }
        for (var sibling : component.getSiblings()) verifyTranslation(sibling, context);
    }

    private static int count(List<Component> rows, String key) {
        return (int) rows.stream().filter(line -> MachineTooltips.containsKey(line, key)).count();
    }

    private static void check(boolean passed, String message) {
        checks++;
        if (!passed) {
            failures.add(message);
            GTLEnhancedcore.LOGGER.error("{} CHECK_FAIL {}", MARKER, message);
        }
    }

    private static void require(boolean passed, String message) {
        checks++;
        if (!passed) throw new AssertionError(message);
    }

    private static void finish(Minecraft client, Throwable error) {
        if (done) return;
        done = true;
        if (error != null) {
            failures.add(error.toString());
            GTLEnhancedcore.LOGGER.error(MARKER + " FAIL", error);
        }
        boolean complete = failures.isEmpty() && localeIndex == LOCALES.size() && isolationVerified && runClaimed && !worldObserved;
        String status = complete ? "COMPLETE" : "FAIL";
        try {
            if (runClaimed && runDirectory != null) {
                var result = new JsonObject();
                result.addProperty("status", status);
                result.addProperty("mode", "functional-tips");
                result.addProperty("policy", POLICY);
                result.addProperty("runId", System.getProperty("gtl.enhancedcore.functionalTipsAuditRunId", ""));
                result.addProperty("productionSha256", System.getProperty("gtl.enhancedcore.functionalTipsCandidateSha256", ""));
                result.addProperty("checks", checks);
                result.addProperty("heapMaxMiB", Runtime.getRuntime().maxMemory() / (1024 * 1024));
                result.addProperty("configuredHeap", "4G");
                result.addProperty("titleScreenVerified", started != 0);
                result.addProperty("noWorldOpened", !worldObserved && client.level == null && client.player == null && client.getSingleplayerServer() == null);
                result.addProperty("shiftKeyboard", "NOT_RUN");
                result.addProperty("expandedValidation", "LOGIC_ONLY");
                result.addProperty("physicalFlicker", "NOT_RUN");
                result.addProperty("builderColorAnimation", localeIndex == LOCALES.size() ? "ALL_LOCALES_SAMPLED" : "INCOMPLETE");
                result.addProperty("foreignShiftPolicy", "SAME_CAPTURED_INHERITED_INPUT_LOGIC_ONLY");
                result.addProperty("originalValidation", "RAW_BACKING_CALLBACK_SEMANTIC_MULTISET");
                result.add("suprachronalExactRemovals", new GsonBuilder().create().toJsonTree(RETIRED_MODULE_KEYS));
                result.addProperty("elapsedMillis", started == 0 ? 0 : (System.nanoTime() - started) / 1_000_000);
                result.add("locales", localeReports);
                result.add("snapshots", new GsonBuilder().create().toJsonTree(snapshots));
                result.add("failures", new GsonBuilder().create().toJsonTree(failures));
                Files.writeString(runDirectory.resolve(status + ".json"), new GsonBuilder().setPrettyPrinting().create().toJson(result),
                        StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            }
            GTLEnhancedcore.LOGGER.info("{} {} checks={} failures={} shift_keyboard=NOT_RUN expanded=LOGIC_ONLY",
                    MARKER, status, checks, failures.size());
        } catch (Throwable markerFailure) {
            GTLEnhancedcore.LOGGER.error(MARKER + " FAIL marker_write", markerFailure);
        } finally {
            if (isolationVerified) client.stop();
        }
    }
}
